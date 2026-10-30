package com.paytech.account.service.processor;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.account.api.enums.AccountPostingErrorCode;
import com.paytech.account.core.dal.dataobject.AccountPostingStream;
import com.paytech.account.domain.model.StreamRebuildResult;
import com.paytech.account.domainservice.repository.AccountPostingStreamRepository;
import com.paytech.account.domainservice.service.PostingStreamRebuildService;
import com.paytech.account.domainservice.validator.EntityIdentityValidator;
import com.paytech.account.service.processor.request.RebuildPostingStreamsRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 按入账流重建主体与币种下的 Owner 账户余额（{@code POST /account-postings/v1/streams/rebuild}）
 * <li>主体与币种下没有入账流时 STREAM_NOT_FOUND；否则每条流一个短事务：锁流行 → 按流水重算 → 以旧版本 CAS 加一（设计 4.5）。</li>
 * <li>与入账、桶移动并发时由流行锁串行，CAS 不符为 409。不删不改流水，不发布事件；操作人只写日志。</li>
 */
@Component
@AllArgsConstructor
public class RebuildPostingStreamsProcessor extends AbstractProcessor<RebuildPostingStreamsRequest, StreamRebuildResult> {
    private static final Logger LOG = LoggerFactory.getLogger(RebuildPostingStreamsProcessor.class);

    private final PostingStreamRebuildService postingStreamRebuildService;
    private final AccountPostingStreamRepository accountPostingStreamRepository;

    @Override
    protected void validate(RebuildPostingStreamsRequest request) {
        EntityIdentityValidator.requireConsistent(request.getEntityType(), request.getEntityId());
    }

    @Override
    protected StreamRebuildResult processRequest(RebuildPostingStreamsRequest request) {
        List<AccountPostingStream> streams = accountPostingStreamRepository.findByEntity(request.getEntityType(),
            request.getEntityId(), request.getCurrency());
        if (streams.isEmpty()) {
            throw new FailException(AccountPostingErrorCode.STREAM_NOT_FOUND);
        }
        StreamRebuildResult result = postingStreamRebuildService.rebuild(streams);
        LOG.info("入账流余额已重建: {}/{}/{}, 操作人 {}, 账户 {}, 余额有变化的账户 {}, 流水 {}",
            request.getEntityType().getCode(), request.getEntityId(), request.getCurrency(), request.getOperatorId(),
            result.getAccountCount(), result.getChangedAccountCount(), result.getEntryCount());
        return result;
    }
}
