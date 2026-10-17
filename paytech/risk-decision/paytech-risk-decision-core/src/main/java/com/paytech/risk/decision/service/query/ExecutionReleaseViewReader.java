package com.paytech.risk.decision.service.query;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.core.dal.dataobject.ReleaseShadowRun;
import com.paytech.risk.decision.domain.enums.ReleaseAuditEventType;
import com.paytech.risk.decision.domain.enums.StaticCheckCode;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.StaticCheckResult;
import com.paytech.risk.decision.domain.release.ExecutionReleaseContent;
import com.paytech.risk.decision.domain.release.ExecutionReleaseView;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.repository.ReleaseAuditEventRepository;
import com.paytech.risk.decision.domainservice.repository.ReleaseShadowRunRepository;

import lombok.AllArgsConstructor;

/** 组合执行发布、静态校验审计与最近一次影子运行；多表只读查询，允许观察相邻已提交状态。 */
@Component
@AllArgsConstructor
public class ExecutionReleaseViewReader {
    private final DecisionRuleSetRepository points;
    private final ReleaseAuditEventRepository audits;
    private final ReleaseShadowRunRepository shadowRuns;

    /**
     * 构造查询结果
     *
     * @param release
     *            执行发布
     * @return 发布、按固定顺序的静态校验与最近影子运行
     */
    public ExecutionReleaseView read(ExecutionRelease release) {
        String pointCode = points.findPoint(release.getDecisionPointId())
            .orElseThrow(() -> new ErrorException("发布的决策点不存在: " + release.getReleaseId())).pointCode();
        List<StaticCheckResult> checks = audits.findByRelease(release.getReleaseId()).stream()
            .filter(event -> event.eventType() == ReleaseAuditEventType.STATIC_CHECK)
            .map(event -> new StaticCheckResult(StaticCheckCode.valueOf(event.checkCode()), event.checkStatus(),
                event.severity(), event.errorCode(), event.errorSummary()))
            .sorted(Comparator.comparingInt(check -> ExecutionReleaseContent.CHECK_ORDER.indexOf(check.checkCode())))
            .toList();
        ReleaseShadowRun run = shadowRuns.findLatestByCandidate(release.getReleaseId()).orElse(null);
        return new ExecutionReleaseView(release.getReleaseId(), release.getReleaseNo(), pointCode,
            release.getDecisionFlowVersionId(), release.getEnvironmentCode().getCode(), release.getStatus().getCode(),
            release.getVersion(), release.getContentHash(), release.getActivatedTime(), release.getSupersededTime(), checks,
            run == null ? null : summary(run));
    }

    /** 影子批次汇总。 */
    static ExecutionReleaseView.ShadowRunSummary summary(ReleaseShadowRun run) {
        return new ExecutionReleaseView.ShadowRunSummary(run.getShadowRunId(), run.getBaselineReleaseId(),
            run.getStatus().getCode(), run.getSampleTarget(), run.getSampleCount(), run.getAllowToReviewCount(),
            run.getAllowToDenyCount(), run.getDenyToAllowCount(), run.getActionChangeCount(), run.getFeatureMissingCount(),
            run.getFailedCount(), run.getTimedOutCount(), run.getP95Millis(), run.getP99Millis(), run.getCheckStatus());
    }
}
