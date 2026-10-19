package com.paytech.ledger.ext.mq;

import java.util.List;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.messaging.api.ConsumeTransaction;
import com.paytech.foundation.messaging.api.ConsumedEventRepository;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventConsumer;
import com.paytech.foundation.messaging.api.DomainEventHandler;
import com.paytech.foundation.messaging.api.DomainEventRejectedException;
import com.paytech.ledger.domain.AccountPostingBatch;
import com.paytech.ledger.domain.AccountPostingCommand;
import com.paytech.ledger.domain.view.PostedInstructionView;
import com.paytech.ledger.domainservice.service.TransactionPostingService;
import com.paytech.ledger.integration.billing.BillingFactClient;

import lombok.AllArgsConstructor;

/**
 * 控制科目过账入口：消费 account 的 AccountPosted（account.events → ledger.account.hash → ledger.account.p0..p3），按过账流严格有序
 * <li>{@link ConsumeTransaction#HANDLER_MANAGED}：经 billing-api 读事实必须在事务外，拦截器先在短事务中判定重放、旧版本与缺口，本 Handler
 * 成功后再另开短事务推进 t_consumed_aggregate_version；两次提交之间中断的重投由 uk_instr_source（account、批次主键、指令类型）幂等吸收。</li>
 * <li>事务外：① 该流尚无消费进度且序号不是 1 → 并发冲突等待重投（直到序号 1 到达或运维追平）；② 桶移动批次、被替代事实后到（SKIPPED）的
 * 批次（无流水、无被替代事实时）直接返回，只推进进度；③ 经 billing-api 读事实，版本不一致 → 并发冲突重投。事务内：生成 ACCOUNT_POSTING 指令并过账。</li>
 * <li>信封非法或过账规则明确拒绝：抛 {@link DomainEventRejectedException} 进入 ledger.account.dlq，同流后续批次因缺口最终也进入死信，恢复走
 * {@code POST /posting-streams/v1/catch-up}；并发冲突与依赖不可用等其他失败原样抛出，由 paytech-messaging 退避重投。</li>
 */
@Component
@AllArgsConstructor
@DomainEventHandler(eventType = AccountPostedPayload.EVENT_TYPE, strictOrder = true,
    transaction = ConsumeTransaction.HANDLER_MANAGED)
public class AccountPostedPostingHandler implements DomainEventConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(AccountPostedPostingHandler.class);

    private final BillingFactClient billingFactClient;
    private final TransactionTemplate transactionTemplate;
    private final ConsumedEventRepository consumedEventRepository;
    private final TransactionPostingService transactionPostingService;

    /** 日志只记录事件 ID、批次与指令，不记录 Payload。 */
    @Override
    public void consume(DomainEvent event) {
        try {
            AccountPostingBatch batch = AccountPostedEventReader.read(event);
            checkFirstSequence(batch);
            if (!batch.requiresPosting()) {
                LOG.info("AccountPosted {} advanced without posting: sourceType={}", event.eventId(), batch.getSourceType());
                return;
            }
            BillingFactPayload fact = billingFactClient.getFact(batch.getPayload().getFactId(),
                batch.getPayload().getFactVersion());
            List<PostedInstructionView> posted = transactionTemplate.execute(
                status -> transactionPostingService.post(new AccountPostingCommand(batch, fact)));
            LOG.info("AccountPosted {} posted: instructions={}", event.eventId(),
                posted.stream().map(PostedInstructionView::getInstructionId).toList());
        } catch (ConcurrentRequestException conflict) {
            LOG.warn("AccountPosted {} conflicted and will be redelivered: {}", event.eventId(), conflict.getMessage());
            throw conflict;
        } catch (IllegalArgumentException | FailException rejected) {
            LOG.warn("AccountPosted {} rejected to dead letter: {}", event.eventId(), rejected.getMessage());
            throw new DomainEventRejectedException("入账批次不可过账: " + event.eventId(), rejected);
        }
    }

    /** 首条序号校验：该流从未消费时只接受序号 1，其余等待重投（之后的缺口与旧版本由拦截器判定）；进度在本库短事务内读取。 */
    void checkFirstSequence(AccountPostingBatch batch) {
        OptionalLong last = transactionTemplate.execute(
            status -> consumedEventRepository.lastVersion(batch.getAggregateKey()));
        if (last.isEmpty() && batch.sequence() != 1) {
            throw new ConcurrentRequestException("过账流尚未消费序号 1，等待重投或追平: " + batch.getAggregateKey()
                + ", 序号 " + batch.sequence());
        }
    }
}
