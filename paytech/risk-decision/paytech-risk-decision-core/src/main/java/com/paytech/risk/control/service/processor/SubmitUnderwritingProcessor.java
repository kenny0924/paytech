package com.paytech.risk.control.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.control.core.dal.dataobject.ClientUnderwriting;
import com.paytech.risk.control.core.enums.UnderwritingDecisionSource;
import com.paytech.risk.control.core.enums.UnderwritingOutcome;
import com.paytech.risk.control.core.enums.UnderwritingRiskLevel;
import com.paytech.risk.control.core.enums.UnderwritingStatus;
import com.paytech.risk.control.domain.limittier.LimitTierEstablishContext;
import com.paytech.risk.control.domain.limittier.TierClientProfile;
import com.paytech.risk.control.domain.underwriting.UnderwritingContent;
import com.paytech.risk.control.domain.underwriting.UnderwritingDecisionContext;
import com.paytech.risk.control.domain.underwriting.UnderwritingException;
import com.paytech.risk.control.domain.underwriting.UnderwritingException.Reason;
import com.paytech.risk.control.domain.underwriting.UnderwritingInput;
import com.paytech.risk.control.domain.underwriting.UnderwritingValidationException;
import com.paytech.risk.control.domain.underwriting.UnderwritingValidationException.Issue;
import com.paytech.risk.control.domain.underwriting.UnderwritingVerdict;
import com.paytech.risk.control.domain.underwriting.UnderwritingView;
import com.paytech.risk.control.domainservice.port.ControlIdentifierGenerator;
import com.paytech.risk.control.domainservice.repository.ClientLimitTierRepository;
import com.paytech.risk.control.domainservice.repository.ClientUnderwritingRepository;
import com.paytech.risk.control.domainservice.repository.RiskListRepository;
import com.paytech.risk.control.service.ClientLimitTierService;
import com.paytech.risk.control.domainservice.validator.underwriting.UnderwritingContentValidator;
import com.paytech.risk.control.service.UnderwritingService;
import com.paytech.risk.control.service.processor.request.SubmitUnderwritingRequest;

import lombok.AllArgsConstructor;

/**
 * 提交承保草稿（U7）：冻结输入快照并按复核级别推进
 * <li>复核级别 = CRR，CRR 缺失按 MEDIUM；LOW / MEDIUM 由承保员提交即决定，APPROVED 时同一事务替代上一有效版本。</li>
 * <li>HIGH 只能提交为 SUBMITTED 等待风控负责人复核（四眼）；HIGH 提交拒绝返回 DECLINE_REQUIRES_REVIEW。</li>
 * <li>批准时 Client 尚无档位（首次或修订批准），与结论同一事务建 VERIFICATION 限额档位（REQ-04）；已有档位不改。</li>
 */
@Component
@AllArgsConstructor
public class SubmitUnderwritingProcessor extends AbstractLockProcessor<SubmitUnderwritingRequest, UnderwritingView> {
    private final Clock clock;
    private final RiskListRepository lists;
    private final UnderwritingService service;
    private final ClientLimitTierService tierService;
    private final ClientLimitTierRepository tiers;
    private final ControlIdentifierGenerator identifiers;
    private final PlatformTransactionManager transactionManager;
    private final ClientUnderwritingRepository underwritings;

    /** 拒绝须写原因；只依赖输入，锁前判断。 */
    @Override
    protected void validate(SubmitUnderwritingRequest request) {
        boolean decline = request.getOutcome() == UnderwritingOutcome.DECLINE;
        if (decline && (request.getDeclineReason() == null || request.getDeclineReason().isBlank())) {
            throw new UnderwritingValidationException(List.of(new Issue("declineReason", "拒绝时须写原因")));
        }
    }

    /** 按版本所属 Client 获取承保锁；版本不存在直接返回 NOT_FOUND。 */
    @Override
    protected LockCommand lockCommand(SubmitUnderwritingRequest request) {
        long clientId = UnderwritingLocks.requireUnderwriting(underwritings, request.getUnderwritingId()).getClientId();
        return LockCommand.tryLocks(List.of(UnderwritingLocks.client(clientId)), UnderwritingLocks.WAIT_TIME,
            UnderwritingLocks.LEASE_TIME);
    }

    @Override
    protected UnderwritingView processWithinLock(SubmitUnderwritingRequest request) {
        // 业务验证
        ClientUnderwriting row = UnderwritingLocks.requireUnderwriting(underwritings, request.getUnderwritingId());
        UnderwritingLocks.requireStatus(row, UnderwritingStatus.DRAFT);
        UnderwritingLocks.requireVersion(row, request.getExpectedVersion());
        UnderwritingContentValidator.validateSubmission(request.getInput(), request.getTerms());
        UnderwritingContent content = new UnderwritingContent(request.getInput(), request.getTerms());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        // HIGH：提交复核
        if (request.getInput().reviewLevel() == UnderwritingRiskLevel.HIGH) {
            requireApproveForHigh(request);
            service.submitForReview(new UnderwritingDecisionContext(row, content, null, null, request.getActorAccountId(), now));
            return UnderwritingView.of(row, null);
        }

        // LOW / MEDIUM：提交即决定，首次批准同一事务建 VERIFICATION 档位
        boolean approve = request.getOutcome() == UnderwritingOutcome.APPROVE;
        ClientUnderwriting superseded = approve ? underwritings.findEffective(row.getClientId()).orElse(null) : null;
        UnderwritingDecisionContext context = new UnderwritingDecisionContext(row, content,
            verdict(request, superseded, now), superseded, request.getActorAccountId(), now);
        LimitTierEstablishContext tier = approve ? tierInitialization(row, request, now) : null;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.decide(context);
            if (tier != null) {
                tierService.establish(tier);
            }
        });
        return UnderwritingView.of(row, null);
    }

    /** 批准时 Client 尚无档位则建 VERIFICATION（首次或修订）；展示字段与 CRR 取本次提交冻结的输入。 */
    LimitTierEstablishContext tierInitialization(ClientUnderwriting row, SubmitUnderwritingRequest request, Instant now) {
        UnderwritingInput input = request.getInput();
        TierClientProfile client = new TierClientProfile(row.getClientId(), input.getClientDisplayName(),
            input.getClientNo(), input.getClientType(), input.getJurisdictionCode());
        return LimitTierCommands.underwritingInitialization(tiers, lists, identifiers, client,
            row.getUnderwritingId(), input.getCrr(), request.getActorAccountId(), now);
    }

    /** HIGH 风险承保员不能直接拒绝，只能提交复核。 */
    void requireApproveForHigh(SubmitUnderwritingRequest request) {
        if (request.getOutcome() == UnderwritingOutcome.DECLINE) {
            throw new UnderwritingException(Reason.DECLINE_REQUIRES_REVIEW,
                "HIGH 风险承保须提交复核，由复核人拒绝: " + request.getUnderwritingId());
        }
    }

    /** 承保员提交即决定：提交人即决定人。 */
    static UnderwritingVerdict verdict(SubmitUnderwritingRequest request, ClientUnderwriting superseded, Instant now) {
        boolean approve = request.getOutcome() == UnderwritingOutcome.APPROVE;
        return new UnderwritingVerdict(approve ? UnderwritingStatus.APPROVED : UnderwritingStatus.DECLINED,
            UnderwritingDecisionSource.MANUAL, request.getActorAccountId(), approve ? null : request.getDeclineReason(), null,
            superseded == null ? null : superseded.getUnderwritingId(), request.getActorAccountId(), now);
    }
}
