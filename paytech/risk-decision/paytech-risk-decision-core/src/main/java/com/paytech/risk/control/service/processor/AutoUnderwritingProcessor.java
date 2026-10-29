package com.paytech.risk.control.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.control.core.dal.dataobject.ClientUnderwriting;
import com.paytech.risk.control.core.enums.UnderwritingDecisionSource;
import com.paytech.risk.control.core.enums.UnderwritingStatus;
import com.paytech.risk.control.domain.underwriting.UnderwritingAutoRule;
import com.paytech.risk.control.domain.underwriting.UnderwritingContent;
import com.paytech.risk.control.domain.underwriting.UnderwritingDecisionContext;
import com.paytech.risk.control.domain.underwriting.UnderwritingException;
import com.paytech.risk.control.domain.underwriting.UnderwritingException.Reason;
import com.paytech.risk.control.domain.underwriting.UnderwritingRule;
import com.paytech.risk.control.domain.underwriting.UnderwritingTerms;
import com.paytech.risk.control.domain.underwriting.UnderwritingView;
import com.paytech.risk.control.domainservice.port.ControlIdentifierGenerator;
import com.paytech.risk.control.domainservice.repository.ClientUnderwritingRepository;
import com.paytech.risk.control.domainservice.validator.underwriting.UnderwritingContentValidator;
import com.paytech.risk.control.service.UnderwritingService;
import com.paytech.risk.control.service.processor.request.AutoUnderwritingRequest;

import lombok.AllArgsConstructor;

/**
 * 按自动承保规则生成 APPROVED（U9）
 * <li>服务端按 ACTIVE 规则重判输入，全部检查通过才生成；结论取标准模板，审批来源 AUTO_RULE 并记规则版本。</li>
 * <li>审计身份：created_by 为触发人，submitted_by / decided_by 为空，提交与决定时间为生成时间；不需四眼。</li>
 * <li>同一 Client 已有进行中承保时拒绝；有有效 APPROVED 时同一事务替代。</li>
 */
@Component
@AllArgsConstructor
public class AutoUnderwritingProcessor extends AbstractLockProcessor<AutoUnderwritingRequest, UnderwritingView> {
    private final Clock clock;
    private final UnderwritingService service;
    private final ControlIdentifierGenerator identifiers;
    private final ClientUnderwritingRepository underwritings;

    /** 在等待时间内尝试获取 Client 承保锁。 */
    @Override
    protected LockCommand lockCommand(AutoUnderwritingRequest request) {
        return LockCommand.tryLocks(List.of(UnderwritingLocks.client(request.getClientId())), UnderwritingLocks.WAIT_TIME,
            UnderwritingLocks.LEASE_TIME);
    }

    @Override
    protected UnderwritingView processWithinLock(AutoUnderwritingRequest request) {
        // 业务验证
        UnderwritingLocks.requireNoOpen(underwritings, request.getClientId());
        UnderwritingRule rule = requireEligible(request);
        UnderwritingTerms terms = UnderwritingTerms.standardTemplate();
        UnderwritingContentValidator.validateSubmission(request.getInput(), terms);
        ClientUnderwriting superseded = underwritings.findEffective(request.getClientId()).orElse(null);

        // 保存
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ClientUnderwriting row = approvedRow(request, new UnderwritingContent(request.getInput(), terms), rule, superseded, now);
        service.autoApprove(new UnderwritingDecisionContext(row, null, null, superseded, request.getActorAccountId(), now));
        return UnderwritingView.of(row, rule.getVersionNo());
    }

    /** 全部检查通过才可生成，返回判定使用的 ACTIVE 规则。 */
    UnderwritingRule requireEligible(AutoUnderwritingRequest request) {
        UnderwritingAutoRule autoRule = UnderwritingAutoRule.evaluate(request.getInput(), service.findActiveRule().orElse(null));
        if (!autoRule.eligible()) {
            throw new UnderwritingException(Reason.AUTO_NOT_ELIGIBLE,
                "Client " + request.getClientId() + " 不满足自动承保条件，未通过：" + autoRule.failedCodes());
        }
        return autoRule.getRule();
    }

    /** 直接以 APPROVED 插入的自动承保版本行。 */
    ClientUnderwriting approvedRow(AutoUnderwritingRequest request, UnderwritingContent content, UnderwritingRule rule,
        ClientUnderwriting superseded, Instant now) {
        ClientUnderwriting row = UnderwritingLocks.newDraft(identifiers.nextIdentifier(), request.getClientId(),
            UnderwritingLocks.nextVersionNo(underwritings, request.getClientId()), content, request.getActorAccountId(), now);
        row.setStatus(UnderwritingStatus.APPROVED);
        row.setOpenSlot(null);
        row.setEffectiveSlot(Boolean.TRUE);
        row.setDecisionSource(UnderwritingDecisionSource.AUTO_RULE);
        row.setRuleVersionId(rule.getRuleVersionId());
        row.setSupersedesUnderwritingId(superseded == null ? null : superseded.getUnderwritingId());
        row.setSubmittedTime(now);
        row.setDecidedTime(now);
        return row;
    }
}
