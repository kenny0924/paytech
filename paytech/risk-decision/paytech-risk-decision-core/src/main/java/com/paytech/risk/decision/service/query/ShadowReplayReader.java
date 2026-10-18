package com.paytech.risk.decision.service.query;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.domainservice.repository.*;

import lombok.AllArgsConstructor;

/** 影子回放需要的只读事实：批次、候选发布、基线样本、已回放的样本；样本冻结输入由各风控点评估器提供。 */
@Component
@AllArgsConstructor
public class ShadowReplayReader {
    private final ExecutionReleaseRepository releases;
    private final ReleaseShadowRunRepository shadowRuns;
    private final DecisionEvaluationRepository evaluations;
    private final EvaluationFeatureResultRepository featureResults;

    /** 按 ID 读取批次。 */
    public Optional<ReleaseShadowRun> run(long shadowRunId) {
        return shadowRuns.findById(shadowRunId);
    }

    /** 候选发布。 */
    public ExecutionRelease candidate(ReleaseShadowRun run) {
        return releases.findById(run.getCandidateReleaseId())
            .orElseThrow(() -> new ErrorException("影子批次的候选发布不存在: " + run.getShadowRunId()));
    }

    /** 基线发布最近的成功正式评估，最多目标样本数。 */
    public List<DecisionEvaluation> samples(ReleaseShadowRun run) {
        return evaluations.findRecentPrimary(run.getBaselineReleaseId(), run.getSampleTarget());
    }

    /** 已回放的样本评估。 */
    public List<DecisionEvaluation> replayed(ReleaseShadowRun run) {
        return evaluations.findByShadowRun(run.getShadowRunId());
    }

    /** 一次评估的 Feature 结果。 */
    public List<EvaluationFeatureResult> featureResults(long evaluationId) {
        return featureResults.findByEvaluation(evaluationId);
    }
}
