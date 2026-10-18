package com.paytech.risk.decision.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.core.dal.dataobject.ReleaseShadowRun;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.ShadowRunStatus;
import com.paytech.risk.decision.domain.release.ExecutionReleaseView;
import com.paytech.risk.decision.domainservice.repository.ExecutionReleaseRepository;
import com.paytech.risk.decision.domainservice.repository.ReleaseShadowRunRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.domainservice.service.ExecutionReleaseService;
import com.paytech.risk.decision.domainservice.service.ExecutionReleaseService.ActivationContext;
import com.paytech.risk.decision.service.processor.request.ExecutionReleaseCommandRequest;
import com.paytech.risk.decision.service.query.ExecutionReleaseViewReader;

import lombok.AllArgsConstructor;

/**
 * 激活 TEST 执行发布（单人审批，一期修订 R2）
 * <li>候选须为 SHADOWING；已有生效发布时，候选最近的影子批次必须以当前生效发布为基线、已完成且技术门槛通过。</li>
 * <li>同一事务内原生效发布进入 SUPERSEDED，候选经 REVIEW_PENDING、APPROVED 进入 ACTIVE，并写入审批审计。</li>
 * <li>PRODUCTION 发布仍要求双人审批，一期不开放激活。</li>
 */
@Component
@AllArgsConstructor
public class ActivateExecutionReleaseProcessor
    extends AbstractLockProcessor<ExecutionReleaseCommandRequest, ExecutionReleaseView> {
    private final Clock clock;
    private final ExecutionReleaseService service;
    private final ExecutionReleaseViewReader views;
    private final ExecutionReleaseRepository releases;
    private final ReleaseShadowRunRepository shadowRuns;

    /** 激活会替代生效发布，全部激活串行。 */
    @Override
    protected LockCommand lockCommand(ExecutionReleaseCommandRequest request) {
        return LockCommand.tryLocks(List.of(ExecutionReleaseLocks.activation()), DecisionResourceLocks.WAIT_TIME,
            DecisionResourceLocks.LEASE_TIME);
    }

    @Override
    protected ExecutionReleaseView processWithinLock(ExecutionReleaseCommandRequest request) {
        // 业务验证
        ExecutionRelease candidate = releases.findById(request.getReleaseId())
            .orElseThrow(DecisionConfigurationNotFoundException::new);
        if (candidate.getEnvironmentCode() != DecisionEnvironment.TEST) {
            throw new BizCheckFailException("一期只允许激活 TEST 环境发布");
        }
        if (candidate.getStatus() != DecisionReleaseStatus.SHADOWING) {
            throw new BizCheckFailException("只有已通过静态校验的发布可以激活: " + candidate.getReleaseId());
        }
        ExecutionRelease baseline = releases.findActive(candidate.getDecisionPointId(), candidate.getEnvironmentCode())
            .orElse(null);
        Long shadowRunId = baseline == null ? null : requirePassedShadowRun(candidate, baseline).getShadowRunId();

        // 保存
        service.activate(new ActivationContext(candidate, baseline, shadowRunId, request.getActorAccountId(),
            clock.instant().truncatedTo(ChronoUnit.MICROS)));
        return views.read(candidate);
    }

    /** 最近一次影子批次必须以当前生效发布为基线、已完成且技术门槛通过。 */
    ReleaseShadowRun requirePassedShadowRun(ExecutionRelease candidate, ExecutionRelease baseline) {
        return shadowRuns.findLatestByCandidate(candidate.getReleaseId())
            .filter(run -> run.getStatus() == ShadowRunStatus.COMPLETED && "PASSED".equals(run.getCheckStatus())
                && run.getBaselineReleaseId() == baseline.getReleaseId())
            .orElseThrow(() -> new BizCheckFailException("激活前必须完成与当前生效发布的影子运行且技术门槛通过"));
    }
}
