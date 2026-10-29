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
import com.paytech.risk.control.domain.underwriting.UnderwritingException;
import com.paytech.risk.control.domain.underwriting.UnderwritingException.Reason;
import com.paytech.risk.control.domain.underwriting.UnderwritingInput;
import com.paytech.risk.control.domain.underwriting.UnderwritingTerms;
import com.paytech.risk.control.domain.underwriting.UnderwritingView;
import com.paytech.risk.control.domainservice.port.ControlIdentifierGenerator;
import com.paytech.risk.control.domainservice.repository.ClientUnderwritingRepository;
import com.paytech.risk.control.domainservice.validator.underwriting.UnderwritingContentValidator;
import com.paytech.risk.control.service.UnderwritingService;
import com.paytech.risk.control.service.processor.request.ReviseUnderwritingRequest;

import lombok.AllArgsConstructor;

/**
 * 基于有效 APPROVED 建修订草稿（U10）
 * <li>路径版本须是当前有效版本且期望版本一致；同一 Client 不能已有进行中版本。</li>
 * <li>结论复制自有效版本，输入用 BFF 按最新来源重新取得的快照；新版本批准前旧版本仍有效。</li>
 * <li>新输入的卡组织高风险标记与复制来的登记标记不同且旧版本没有理由时，登记标记回到默认值，由承保员提交前确认。</li>
 */
@Component
@AllArgsConstructor
public class ReviseUnderwritingProcessor extends AbstractLockProcessor<ReviseUnderwritingRequest, UnderwritingView> {
    private final Clock clock;
    private final UnderwritingService service;
    private final ControlIdentifierGenerator identifiers;
    private final ClientUnderwritingRepository underwritings;

    /** 按有效版本所属 Client 获取承保锁；版本不存在直接返回 NOT_FOUND。 */
    @Override
    protected LockCommand lockCommand(ReviseUnderwritingRequest request) {
        long clientId = UnderwritingLocks.requireUnderwriting(underwritings, request.getUnderwritingId()).getClientId();
        return LockCommand.tryLocks(List.of(UnderwritingLocks.client(clientId)), UnderwritingLocks.WAIT_TIME,
            UnderwritingLocks.LEASE_TIME);
    }

    @Override
    protected UnderwritingView processWithinLock(ReviseUnderwritingRequest request) {
        // 业务验证
        ClientUnderwriting effective = UnderwritingLocks.requireUnderwriting(underwritings, request.getUnderwritingId());
        requireEffective(effective);
        UnderwritingLocks.requireVersion(effective, request.getExpectedVersion());
        UnderwritingLocks.requireNoOpen(underwritings, effective.getClientId());
        UnderwritingTerms terms = copiedTerms(effective, request.getInput());
        UnderwritingContentValidator.validateDraft(request.getInput(), terms);

        // 保存
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ClientUnderwriting row = UnderwritingLocks.newDraft(identifiers.nextIdentifier(), effective.getClientId(),
            UnderwritingLocks.nextVersionNo(underwritings, effective.getClientId()),
            new UnderwritingContent(request.getInput(), terms), request.getActorAccountId(), now);
        row.setRevisionReason(request.getRevisionReason());
        service.createDraft(row);
        return UnderwritingView.of(row, null);
    }

    /** 只能修订当前有效的 APPROVED 版本。 */
    static void requireEffective(ClientUnderwriting row) {
        if (row.getStatus() != UnderwritingStatus.APPROVED || !Boolean.TRUE.equals(row.getEffectiveSlot())) {
            throw new UnderwritingException(Reason.STATE_INVALID,
                "只能修订当前有效的已批准承保: " + row.getUnderwritingId());
        }
    }

    /** 复制有效版本的结论；登记标记与新默认值不同且没有理由时回到默认值。 */
    static UnderwritingTerms copiedTerms(ClientUnderwriting effective, UnderwritingInput input) {
        UnderwritingTerms terms = UnderwritingContent.of(effective).getTerms();
        boolean differs = terms.getHighRiskMcc() != null && terms.getHighRiskMcc() != input.isNetworkHighRiskMcc();
        if (differs && (terms.getHighRiskMccReason() == null || terms.getHighRiskMccReason().isBlank())) {
            terms.setHighRiskMcc(input.isNetworkHighRiskMcc());
        }
        return terms;
    }
}
