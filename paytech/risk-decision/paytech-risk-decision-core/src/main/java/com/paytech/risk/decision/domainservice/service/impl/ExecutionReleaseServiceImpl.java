package com.paytech.risk.decision.domainservice.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.core.dal.dataobject.ReleaseAuditEvent;
import com.paytech.risk.decision.core.dal.dataobject.ReleaseShadowRun;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.ReleaseApprovalStatus;
import com.paytech.risk.decision.domain.enums.ReleaseAuditEventType;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;
import com.paytech.risk.decision.domain.release.DecisionReleaseAssembler;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.StaticCheckResult;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.decision.domainservice.repository.DecisionReleaseRepository;
import com.paytech.risk.decision.domainservice.repository.ExecutionReleaseRepository;
import com.paytech.risk.decision.domainservice.repository.ReleaseAuditEventRepository;
import com.paytech.risk.decision.domainservice.repository.ReleaseShadowRunRepository;
import com.paytech.risk.decision.domainservice.service.ExecutionReleaseService;

import lombok.AllArgsConstructor;

/** 执行发布状态机的本地事务保存；状态审计的聚合版本等于变化后的发布版本。 */
@Service
@AllArgsConstructor
public class ExecutionReleaseServiceImpl implements ExecutionReleaseService {
    private final Clock clock;
    private final ExecutionReleaseRepository releases;
    private final ReleaseAuditEventRepository audits;
    private final DecisionReleaseRepository releaseNumbers;
    private final ReleaseShadowRunRepository shadowRuns;
    private final CompensationEventRegistrar registrar;
    private final DecisionIdentifierGenerator identifiers;
    private final PlatformTransactionManager transactionManager;

    /** 发布编号在行锁下分配，与结账测试候选共用全局序列。 */
    @Override
    public void create(ExecutionRelease release, long actorId) {
        transaction().executeWithoutResult(status -> {
            release.setReleaseNo(Integer.toString(releaseNumbers.nextReleaseNoWhileCheckoutPointLocked()));
            releases.insert(release);
            audits.insert(stateChange(release, null, "EXECUTION_RELEASE_CREATED", actorId, release.getCreateTime()));
        });
    }

    /** 两次状态变化与八项检查同一事务提交。 */
    @Override
    public void validate(ExecutionRelease release, List<StaticCheckResult> checks, long actorId) {
        Instant time = now();
        boolean passed = checks.stream().allMatch(check -> check.checkStatus() == StaticCheckStatus.PASSED);
        transaction().executeWithoutResult(status -> {
            move(release, DecisionReleaseStatus.VALIDATING, "STATIC_VALIDATION_STARTED", actorId, time);
            audits.insertChecks(checks.stream().map(check -> DecisionReleaseAssembler.staticCheck(
                identifiers.nextIdentifier(), release.getReleaseId(), time, check)).toList());
            move(release, passed ? DecisionReleaseStatus.SHADOWING : DecisionReleaseStatus.REJECTED,
                passed ? "STATIC_VALIDATION_PASSED" : "STATIC_VALIDATION_FAILED", actorId, time);
        });
    }

    /** 批次与回放任务同一事务提交，任务在提交后执行。 */
    @Override
    public void startShadowRun(ReleaseShadowRun run, CompensationEventRequest event) {
        transaction().executeWithoutResult(status -> {
            shadowRuns.insert(run);
            registrar.register(event);
        });
    }

    /** 先替代原生效发布释放生效槽位，再推进候选并写入审批。 */
    @Override
    public void activate(ActivationContext context) {
        transaction().executeWithoutResult(status -> {
            ExecutionRelease baseline = context.baseline();
            if (baseline != null) {
                Instant activated = baseline.getActivatedTime();
                releases.transition(baseline, DecisionReleaseStatus.SUPERSEDED, null, activated, context.time(), context.time());
                audits.insert(stateChange(baseline, DecisionReleaseStatus.ACTIVE, "RELEASE_SUPERSEDED", context.actorId(),
                    context.time()));
            }
            ExecutionRelease candidate = context.candidate();
            move(candidate, DecisionReleaseStatus.REVIEW_PENDING, "SHADOW_ACCEPTED", context.actorId(), context.time());
            audits.insert(approval(candidate, context));
            move(candidate, DecisionReleaseStatus.APPROVED, "TEST_SINGLE_APPROVAL", context.actorId(), context.time());
            releases.transition(candidate, DecisionReleaseStatus.ACTIVE, Boolean.TRUE, context.time(), null, context.time());
            audits.insert(stateChange(candidate, DecisionReleaseStatus.APPROVED, "RELEASE_ACTIVATED", context.actorId(),
                context.time()));
        });
    }

    /** 推进到非生效状态并写入对应审计。 */
    void move(ExecutionRelease release, DecisionReleaseStatus to, String reason, long actorId, Instant time) {
        DecisionReleaseStatus from = release.getStatus();
        releases.transition(release, to, null, null, null, time);
        audits.insert(stateChange(release, from, reason, actorId, time));
    }

    /** 状态审计；聚合版本为变化后的发布版本。 */
    ReleaseAuditEvent stateChange(ExecutionRelease release, DecisionReleaseStatus from, String reason, long actorId,
        Instant time) {
        return DecisionReleaseAssembler.stateChange(identifiers.nextIdentifier(), release.getReleaseId(), time, from,
            release.getStatus(), reason, ReleaseAuditEvent.triggerId(release.getReleaseId(), reason), actorId,
            release.getVersion());
    }

    /** TEST 环境单人审批事实。 */
    ReleaseAuditEvent approval(ExecutionRelease candidate, ActivationContext context) {
        return new ReleaseAuditEvent(identifiers.nextIdentifier(), candidate.getReleaseId(), ReleaseAuditEventType.APPROVAL,
            context.time(), null, null, null, null, null, context.shadowRunId(),
            "activation:" + candidate.getReleaseId(), ReleaseApprovalStatus.APPROVED, context.actorId(),
            "TEST 环境单人激活", null, null, null, null, null, null, null, context.time());
    }

    /** 本地事务模板。 */
    TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    /** UTC 微秒时间。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
