package com.paytech.risk.decision.service.query;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.DecisionEvaluation;
import com.paytech.risk.decision.core.dal.dataobject.EvaluationFeatureResult;
import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.core.dal.dataobject.ReleaseShadowRun;
import com.paytech.risk.decision.core.dal.dataobject.TransactionRiskAssessment;
import com.paytech.risk.decision.domain.runtime.PaymentRiskInput;
import com.paytech.risk.decision.domainservice.repository.DecisionEvaluationRepository;
import com.paytech.risk.decision.domainservice.repository.EvaluationFeatureResultRepository;
import com.paytech.risk.decision.domainservice.repository.ExecutionReleaseRepository;
import com.paytech.risk.decision.domainservice.repository.ReleaseShadowRunRepository;
import com.paytech.risk.decision.domainservice.repository.TransactionRiskAssessmentRepository;

import lombok.AllArgsConstructor;

/** 影子回放需要的只读事实：批次、候选发布、基线样本及其冻结输入、已回放的样本。 */
@Component
@AllArgsConstructor
public class ShadowReplayReader {
    private final ExecutionReleaseRepository releases;
    private final ReleaseShadowRunRepository shadowRuns;
    private final DecisionEvaluationRepository evaluations;
    private final EvaluationFeatureResultRepository featureResults;
    private final TransactionRiskAssessmentRepository assessments;

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

    /** 样本评估冻结的交易评估快照。 */
    public TransactionRiskAssessment assessment(DecisionEvaluation sample) {
        return assessments.findByEvaluation(sample.getEvaluationId())
            .orElseThrow(() -> new ErrorException("正式评估缺少交易评估快照: " + sample.getEvaluationId()));
    }

    /** 已回放的样本评估。 */
    public List<DecisionEvaluation> replayed(ReleaseShadowRun run) {
        return evaluations.findByShadowRun(run.getShadowRunId());
    }

    /** 一次评估的 Feature 结果。 */
    public List<EvaluationFeatureResult> featureResults(long evaluationId) {
        return featureResults.findByEvaluation(evaluationId);
    }

    /** 由交易评估快照还原冻结输入。 */
    public static PaymentRiskInput input(TransactionRiskAssessment row) {
        return new PaymentRiskInput(row.getPaymentAttemptId(), row.getPaymentIntentId(), row.getRiskActionId(),
            row.getOrderId(), row.getClientId(), row.getEntityNo(), row.getPaymentMethodCode(), row.getCaptureMode(),
            row.getAmount(), row.getCurrency(), row.getIndustryCode(), row.getMccCode(), row.getConsumerCountryCode(),
            row.getConsumerReference(), row.getClientIpAddress(), row.getUserAgent(), row.getCardFingerprint(),
            row.getCardBin());
    }
}
