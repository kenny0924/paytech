package com.paytech.risk.control.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.control.core.dal.dataobject.ClientUnderwriting;
import com.paytech.risk.control.core.enums.UnderwritingDecisionSource;
import com.paytech.risk.control.core.enums.UnderwritingOutcome;
import com.paytech.risk.control.core.enums.UnderwritingStatus;
import com.paytech.risk.control.domain.limittier.LimitTierEstablishContext;
import com.paytech.risk.control.domain.underwriting.UnderwritingDecisionContext;
import com.paytech.risk.control.domain.underwriting.UnderwritingException;
import com.paytech.risk.control.domain.underwriting.UnderwritingException.Reason;
import com.paytech.risk.control.domain.underwriting.UnderwritingValidationException;
import com.paytech.risk.control.domain.underwriting.UnderwritingValidationException.Issue;
import com.paytech.risk.control.domain.underwriting.UnderwritingVerdict;
import com.paytech.risk.control.domain.underwriting.UnderwritingView;
import com.paytech.risk.control.domainservice.port.ControlIdentifierGenerator;
import com.paytech.risk.control.domainservice.repository.ClientLimitTierRepository;
import com.paytech.risk.control.domainservice.repository.ClientUnderwritingRepository;
import com.paytech.risk.control.domainservice.repository.RiskListRepository;
import com.paytech.risk.control.service.ClientLimitTierService;
import com.paytech.risk.control.service.UnderwritingService;
import com.paytech.risk.control.service.processor.request.ReviewUnderwritingRequest;

import lombok.AllArgsConstructor;

/**
 * 复核 HIGH 风险承保（U8，四眼）
 * <li>复核权限由 Gateway 路由 RISK_UNDERWRITING_APPROVE 绑定；本服务校验只对 SUBMITTED 生效且复核人不是提交人。</li>
 * <li>批准时同一事务替代上一有效版本；拒绝的意见保存为拒绝原因，批准的意见保存为复核意见。输入快照保持提交时的值。</li>
 * <li>批准时 Client 尚无档位（首次或修订批准），与结论同一事务建 VERIFICATION 限额档位（REQ-04）；已有档位不改。</li>
 */
@Component
@AllArgsConstructor
public class ReviewUnderwritingProcessor extends AbstractLockProcessor<ReviewUnderwritingRequest, UnderwritingView> {
    private final Clock clock;
    private final RiskListRepository lists;
    private final UnderwritingService service;
    private final ClientLimitTierService tierService;
    private final ClientLimitTierRepository tiers;
    private final ControlIdentifierGenerator identifiers;
    private final PlatformTransactionManager transactionManager;
    private final ClientUnderwritingRepository underwritings;

    /** 拒绝时须写复核意见；只依赖输入，锁前判断。 */
    @Override
    protected void validate(ReviewUnderwritingRequest request) {
        boolean decline = request.getOutcome() == UnderwritingOutcome.DECLINE;
        if (decline && (request.getComment() == null || request.getComment().isBlank())) {
            throw new UnderwritingValidationException(List.of(new Issue("comment", "拒绝时须写复核意见")));
        }
    }

    /** 按版本所属 Client 获取承保锁；版本不存在直接返回 NOT_FOUND。 */
    @Override
    protected LockCommand lockCommand(ReviewUnderwritingRequest request) {
        long clientId = UnderwritingLocks.requireUnderwriting(underwritings, request.getUnderwritingId()).getClientId();
        return LockCommand.tryLocks(List.of(UnderwritingLocks.client(clientId)), UnderwritingLocks.WAIT_TIME,
            UnderwritingLocks.LEASE_TIME);
    }

    @Override
    protected UnderwritingView processWithinLock(ReviewUnderwritingRequest request) {
        // 业务验证
        ClientUnderwriting row = UnderwritingLocks.requireUnderwriting(underwritings, request.getUnderwritingId());
        UnderwritingLocks.requireStatus(row, UnderwritingStatus.SUBMITTED);
        UnderwritingLocks.requireVersion(row, request.getExpectedVersion());
        requireOtherReviewer(row, request.getActorAccountId());

        // 保存
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        boolean approve = request.getOutcome() == UnderwritingOutcome.APPROVE;
        ClientUnderwriting superseded = approve ? underwritings.findEffective(row.getClientId()).orElse(null) : null;
        UnderwritingDecisionContext context = new UnderwritingDecisionContext(row, null, verdict(request, superseded, now),
            superseded, request.getActorAccountId(), now);
        LimitTierEstablishContext tier = approve ? LimitTierCommands.underwritingInitialization(tiers, lists, identifiers,
            LimitTierCommands.profile(row), row.getUnderwritingId(), row.getCrr(), request.getActorAccountId(),
            now) : null;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.decide(context);
            if (tier != null) {
                tierService.establish(tier);
            }
        });
        return UnderwritingView.of(row, null);
    }

    /** 四眼：复核人不能是提交人。 */
    static void requireOtherReviewer(ClientUnderwriting row, long reviewer) {
        if (Objects.equals(row.getSubmittedBy(), reviewer)) {
            throw new UnderwritingException(Reason.SELF_REVIEW, "提交人不能复核自己提交的承保: " + row.getUnderwritingId());
        }
    }

    /** 复核决定：复核人为决定人，提交人保持原值。 */
    static UnderwritingVerdict verdict(ReviewUnderwritingRequest request, ClientUnderwriting superseded, Instant now) {
        boolean approve = request.getOutcome() == UnderwritingOutcome.APPROVE;
        String comment = request.getComment() == null || request.getComment().isBlank() ? null : request.getComment();
        return new UnderwritingVerdict(approve ? UnderwritingStatus.APPROVED : UnderwritingStatus.DECLINED,
            UnderwritingDecisionSource.MANUAL, request.getActorAccountId(), approve ? null : comment, approve ? comment : null,
            superseded == null ? null : superseded.getUnderwritingId(), null, now);
    }
}
