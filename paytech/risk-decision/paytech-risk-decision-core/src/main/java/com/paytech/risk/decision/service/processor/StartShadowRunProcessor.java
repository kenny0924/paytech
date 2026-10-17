package com.paytech.risk.decision.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.core.dal.dataobject.ReleaseShadowRun;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.ShadowRunStatus;
import com.paytech.risk.decision.domain.release.ExecutionReleaseView;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.decision.domainservice.repository.ExecutionReleaseRepository;
import com.paytech.risk.decision.domainservice.repository.ReleaseShadowRunRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.domainservice.service.ExecutionReleaseService;
import com.paytech.risk.decision.service.handler.ShadowRunHandler;
import com.paytech.risk.decision.service.processor.request.StartShadowRunRequest;
import com.paytech.risk.decision.service.query.ExecutionReleaseViewReader;

import lombok.AllArgsConstructor;

/**
 * 启动候选发布的影子运行：回放当前生效发布最近的正式评估
 * <li>候选须已通过静态校验（SHADOWING）；没有生效发布时首个发布无需影子运行。</li>
 * <li>批次与回放任务同一事务登记，回放在提交后由补偿任务执行；同一候选同时只允许一个运行中批次。</li>
 */
@Component
@AllArgsConstructor
public class StartShadowRunProcessor extends AbstractLockProcessor<StartShadowRunRequest, ExecutionReleaseView> {
    private final Clock clock;
    private final ExecutionReleaseService service;
    private final ExecutionReleaseViewReader views;
    private final ExecutionReleaseRepository releases;
    private final ReleaseShadowRunRepository shadowRuns;
    private final DecisionIdentifierGenerator identifiers;

    /** 与同一发布的校验串行。 */
    @Override
    protected LockCommand lockCommand(StartShadowRunRequest request) {
        return LockCommand.tryLocks(List.of(ExecutionReleaseLocks.release(request.getReleaseId())),
            DecisionResourceLocks.WAIT_TIME, DecisionResourceLocks.LEASE_TIME);
    }

    @Override
    protected ExecutionReleaseView processWithinLock(StartShadowRunRequest request) {
        // 业务验证
        ExecutionRelease candidate = releases.findById(request.getReleaseId())
            .orElseThrow(DecisionConfigurationNotFoundException::new);
        if (candidate.getStatus() != DecisionReleaseStatus.SHADOWING) {
            throw new BizCheckFailException("只有已通过静态校验的发布可以影子运行: " + candidate.getReleaseId());
        }
        ExecutionRelease baseline = releases.findActive(candidate.getDecisionPointId(), candidate.getEnvironmentCode())
            .orElseThrow(() -> new BizCheckFailException("当前没有生效发布，首个发布无需影子运行，可直接激活"));
        boolean running = shadowRuns.findLatestByCandidate(candidate.getReleaseId())
            .filter(run -> run.getStatus() == ShadowRunStatus.RUNNING).isPresent();
        if (running) {
            throw new BizCheckFailException("候选发布已有运行中的影子批次: " + candidate.getReleaseId());
        }

        // 保存批次并登记回放
        ReleaseShadowRun run = buildRun(candidate, baseline, request.getSampleTarget());
        service.startShadowRun(run, ShadowRunHandler.buildEvent(run.getShadowRunId()));
        return views.read(candidate);
    }

    /** 运行中的新批次。 */
    ReleaseShadowRun buildRun(ExecutionRelease candidate, ExecutionRelease baseline, int sampleTarget) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ReleaseShadowRun run = new ReleaseShadowRun();
        run.setShadowRunId(identifiers.nextIdentifier());
        run.setRunNo(Long.toString(run.getShadowRunId()));
        run.setBaselineReleaseId(baseline.getReleaseId());
        run.setCandidateReleaseId(candidate.getReleaseId());
        run.setStatus(ShadowRunStatus.RUNNING);
        run.setSampleTarget(sampleTarget);
        run.setStartedTime(now);
        run.setVersion(1);
        run.setCreateTime(now);
        run.setUpdateTime(now);
        return run;
    }
}
