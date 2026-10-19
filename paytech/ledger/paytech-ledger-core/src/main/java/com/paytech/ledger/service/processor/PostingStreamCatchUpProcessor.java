package com.paytech.ledger.service.processor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.messaging.api.AggregateKey;
import com.paytech.foundation.messaging.api.ConsumedEventRepository;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.ledger.core.constant.AccountingLocks;
import com.paytech.ledger.domain.AccountPostingBatch;
import com.paytech.ledger.domain.AccountPostingCommand;
import com.paytech.ledger.domain.AccountPostingPage;
import com.paytech.ledger.domainservice.service.TransactionPostingService;
import com.paytech.ledger.integration.account.AccountPostingClient;
import com.paytech.ledger.integration.billing.EconomicFactClient;
import com.paytech.ledger.service.processor.request.CatchUpPostingStreamRequest;
import com.paytech.ledger.service.processor.response.CatchUpPostingStreamResponse;

import lombok.AllArgsConstructor;

/**
 * 追平一条 account 过账流（AccountPosted 首条未到、缺口或进入死信后的恢复路径，技术方案 2.1 Q-06）
 * <li>在本库短事务内读取已消费序号，经 account-api 从“已消费序号 + 1”按序号分页读取批次；每个批次按与消费 Handler 相同的逻辑处理：需要过账的
 * 事实批次事务外经 billing-api 读事实，再在一个本库事务内过账并推进 t_consumed_aggregate_version；桶移动与 SKIPPED 批次只推进进度。</li>
 * <li>批次序号必须从已消费序号 + 1 连续递增，否则按系统错误停止；最多处理 maxBatches 个批次，已处理的批次各自提交、保留。</li>
 * <li>同一流的追平共用资源锁并立即尝试；与消费 Handler 并发时由 uk_instr_source 冲突（409）与只增进度保护，可再次调用继续。</li>
 * <li>account 或 billing 不可用时以 503 失败；期间未开放以 409 失败；规则或数据拒绝以 400 失败。</li>
 */
@Component
@AllArgsConstructor
public class PostingStreamCatchUpProcessor
    extends AbstractLockProcessor<CatchUpPostingStreamRequest, CatchUpPostingStreamResponse> {
    /** account-api 单页批次上限。 */
    static final int PAGE_LIMIT = 200;

    private final EconomicFactClient economicFactClient;
    private final TransactionTemplate transactionTemplate;
    private final AccountPostingClient accountPostingClient;
    private final ConsumedEventRepository consumedEventRepository;
    private final TransactionPostingService transactionPostingService;

    /** 同一过账流的追平立即尝试获取资源锁。 */
    @Override
    protected LockCommand lockCommand(CatchUpPostingStreamRequest request) {
        return LockCommand.tryLock(AccountingLocks.postingStream(streamOf(request).aggregateId()));
    }

    @Override
    protected CatchUpPostingStreamResponse processWithinLock(CatchUpPostingStreamRequest request) {
        AccountPostedPayload.StreamKey stream = streamOf(request);
        AggregateKey key = AggregateKey.of(AccountPostedPayload.AGGREGATE_TYPE, stream.aggregateId());
        long last = transactionTemplate.execute(status -> consumedEventRepository.lastVersion(key)).orElse(0L);
        int processed = 0;
        while (processed < request.getMaxBatches()) {
            int limit = Math.min(PAGE_LIMIT, request.getMaxBatches() - processed);
            AccountPostingPage page = accountPostingClient.listPostings(stream, last + 1, limit);
            for (AccountPostingBatch batch : page.getBatches()) {
                checkNext(key, last, batch);
                apply(key, batch);
                last = batch.sequence();
                processed++;
            }
            if (page.getNextFromSequence() == null || page.getBatches().isEmpty()) {
                return new CatchUpPostingStreamResponse(processed, last, true);
            }
        }
        return new CatchUpPostingStreamResponse(processed, last, false);
    }

    /** 由请求的主体、币种与分片构造并校验过账流身份。 */
    static AccountPostedPayload.StreamKey streamOf(CatchUpPostingStreamRequest request) {
        try {
            return AccountPostedPayload.parseAggregateId(AccountPostedPayload.aggregateId(request.getEntityType(),
                request.getEntityId(), request.getCurrency(), request.getShardNo()));
        } catch (IllegalArgumentException invalid) {
            throw new InvalidParameterException("过账流身份非法: "
                + request.getEntityType() + "/" + request.getEntityId() + "/" + request.getCurrency() + "/"
                + request.getShardNo());
        }
    }

    /** account 返回的批次必须属于本流且序号紧接已处理序号。 */
    void checkNext(AggregateKey key, long last, AccountPostingBatch batch) {
        if (!key.equals(batch.getAggregateKey()) || batch.sequence() != last + 1) {
            throw new ErrorException("account 过账流批次不连续: " + key + ", 期望序号 " + (last + 1) + ", 实际 "
                + batch.getAggregateKey() + " / " + batch.sequence());
        }
    }

    /**
     * 处理一个批次：需要过账时事务外读事实，再在一个本库事务内过账并推进消费进度
     * <li>过账幂等（同一批次已有指令时直接返回），进度只增不减，因此与消费 Handler 先后处理同一批次都安全。</li>
     */
    void apply(AggregateKey key, AccountPostingBatch batch) {
        EconomicFactPayload fact = batch.requiresPosting()
            ? economicFactClient.getFact(batch.getPayload().getFactId(), batch.getPayload().getFactVersion()) : null;
        transactionTemplate.executeWithoutResult(status -> {
            if (fact != null) {
                transactionPostingService.post(new AccountPostingCommand(batch, fact));
            }
            consumedEventRepository.advance(key, batch.sequence());
        });
    }
}
