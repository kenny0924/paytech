package com.paytech.risk.control.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.control.core.dal.dataobject.ClientUnderwriting;
import com.paytech.risk.control.domain.underwriting.UnderwritingContent;
import com.paytech.risk.control.domain.underwriting.UnderwritingView;
import com.paytech.risk.control.domainservice.port.ControlIdentifierGenerator;
import com.paytech.risk.control.domainservice.repository.ClientUnderwritingRepository;
import com.paytech.risk.control.domainservice.validator.underwriting.UnderwritingContentValidator;
import com.paytech.risk.control.service.UnderwritingService;
import com.paytech.risk.control.service.processor.request.CreateUnderwritingDraftRequest;

import lombok.AllArgsConstructor;

/**
 * 建承保草稿（U5）
 * <li>Client 承保锁串行同一 Client 的承保写入；同一 Client 已有进行中版本时拒绝，进行中槽位唯一索引兜底。</li>
 * <li>版本号为该 Client 最新版本号加一；草稿只校验字段组合，提交必填项留到提交时校验。</li>
 */
@Component
@AllArgsConstructor
public class CreateUnderwritingDraftProcessor
    extends AbstractLockProcessor<CreateUnderwritingDraftRequest, UnderwritingView> {
    private final Clock clock;
    private final UnderwritingService service;
    private final ControlIdentifierGenerator identifiers;
    private final ClientUnderwritingRepository underwritings;

    /** 在等待时间内尝试获取 Client 承保锁。 */
    @Override
    protected LockCommand lockCommand(CreateUnderwritingDraftRequest request) {
        return LockCommand.tryLocks(List.of(UnderwritingLocks.client(request.getClientId())), UnderwritingLocks.WAIT_TIME,
            UnderwritingLocks.LEASE_TIME);
    }

    @Override
    protected UnderwritingView processWithinLock(CreateUnderwritingDraftRequest request) {
        // 业务验证
        UnderwritingContentValidator.validateDraft(request.getInput(), request.getTerms());
        UnderwritingLocks.requireNoOpen(underwritings, request.getClientId());

        // 保存
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ClientUnderwriting row = UnderwritingLocks.newDraft(identifiers.nextIdentifier(), request.getClientId(),
            UnderwritingLocks.nextVersionNo(underwritings, request.getClientId()),
            new UnderwritingContent(request.getInput(), request.getTerms()), request.getActorAccountId(), now);
        service.createDraft(row);
        return UnderwritingView.of(row, null);
    }
}
