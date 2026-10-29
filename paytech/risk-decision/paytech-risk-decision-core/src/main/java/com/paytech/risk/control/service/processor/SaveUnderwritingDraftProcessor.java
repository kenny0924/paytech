package com.paytech.risk.control.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.control.core.dal.dataobject.ClientUnderwriting;
import com.paytech.risk.control.core.enums.UnderwritingStatus;
import com.paytech.risk.control.domain.underwriting.UnderwritingContent;
import com.paytech.risk.control.domain.underwriting.UnderwritingView;
import com.paytech.risk.control.domainservice.repository.ClientUnderwritingRepository;
import com.paytech.risk.control.domainservice.validator.underwriting.UnderwritingContentValidator;
import com.paytech.risk.control.service.UnderwritingService;
import com.paytech.risk.control.service.processor.request.SaveUnderwritingDraftRequest;

import lombok.AllArgsConstructor;

/**
 * 保存承保草稿（U6）：整体替换草稿的输入与结论并重算估算敞口
 * <li>只有 DRAFT 可保存（已提交或已决定的版本内容不可变）；期望版本须是当前版本，条件更新兜底。</li>
 */
@Component
@AllArgsConstructor
public class SaveUnderwritingDraftProcessor extends AbstractLockProcessor<SaveUnderwritingDraftRequest, UnderwritingView> {
    private final Clock clock;
    private final UnderwritingService service;
    private final ClientUnderwritingRepository underwritings;

    /** 按版本所属 Client 获取承保锁；版本不存在直接返回 NOT_FOUND。 */
    @Override
    protected LockCommand lockCommand(SaveUnderwritingDraftRequest request) {
        long clientId = UnderwritingLocks.requireUnderwriting(underwritings, request.getUnderwritingId()).getClientId();
        return LockCommand.tryLocks(List.of(UnderwritingLocks.client(clientId)), UnderwritingLocks.WAIT_TIME,
            UnderwritingLocks.LEASE_TIME);
    }

    @Override
    protected UnderwritingView processWithinLock(SaveUnderwritingDraftRequest request) {
        // 业务验证
        ClientUnderwriting row = UnderwritingLocks.requireUnderwriting(underwritings, request.getUnderwritingId());
        UnderwritingLocks.requireStatus(row, UnderwritingStatus.DRAFT);
        UnderwritingLocks.requireVersion(row, request.getExpectedVersion());
        UnderwritingContentValidator.validateDraft(request.getInput(), request.getTerms());

        // 保存
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        service.saveDraft(row, new UnderwritingContent(request.getInput(), request.getTerms()), now);
        return UnderwritingView.of(row, null);
    }
}
