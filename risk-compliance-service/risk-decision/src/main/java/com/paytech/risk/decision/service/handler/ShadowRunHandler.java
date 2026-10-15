package com.paytech.risk.decision.service.handler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.risk.decision.core.dal.dataobject.DecisionEvaluation;
import com.paytech.risk.decision.core.dal.dataobject.ReleaseShadowRun;
import com.paytech.risk.decision.core.dal.dataobject.TransactionRiskAssessment;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.EvaluationMode;
import com.paytech.risk.decision.domain.enums.ShadowRunStatus;
import com.paytech.risk.decision.domain.enums.TechnicalStatus;
import com.paytech.risk.decision.domain.release.ShadowRunSummaries;
import com.paytech.risk.decision.domain.runtime.DecisionEngine;
import com.paytech.risk.decision.domain.runtime.EngineOutcome;
import com.paytech.risk.decision.domain.runtime.ExecutionSnapshot;
import com.paytech.risk.decision.domain.runtime.FeatureComputationException;
import com.paytech.risk.decision.domain.runtime.FeatureValue;
import com.paytech.risk.decision.domain.runtime.PaymentRiskFacts;
import com.paytech.risk.decision.domain.runtime.PaymentRiskInput;
import com.paytech.risk.decision.domain.runtime.ShadowSampleContext;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.decision.domainservice.service.ShadowRunService;
import com.paytech.risk.decision.service.query.ExecutionSnapshotReader;
import com.paytech.risk.decision.service.query.FeatureValueReader;
import com.paytech.risk.decision.service.query.ShadowReplayReader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 影子回放：候选发布按基线评估的冻结输入与原评估时间重新执行
 * <li>eventKey 是批次 ID；批次不再运行时直接成功。已回放的样本按（批次, 基线评估）唯一跳过，重试可续跑。</li>
 * <li>回放不写累计事件与规则轨迹，只写影子评估及其 Feature 结果；完成后写入批次汇总。</li>
 */
@Slf4j
@Component
@AllArgsConstructor
public class ShadowRunHandler implements CompensationEventHandler {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "DECISION_SHADOW_RUN";

    /** 候选执行失败代码，与正式评估一致。 */
    static final String EVALUATION_ERROR = "EVALUATION_ERROR";

    /** 候选执行超时代码，与正式评估一致。 */
    static final String EVALUATION_TIMEOUT = "EVALUATION_TIMEOUT";

    private final Clock clock;
    private final ShadowReplayReader reader;
    private final FeatureValueReader features;
    private final ExecutionSnapshotReader snapshots;
    private final ShadowRunService service;
    private final DecisionIdentifierGenerator identifiers;

    /** 纯工厂：与批次创建在同一本地事务登记。 */
    public static CompensationEventRequest buildEvent(long shadowRunId) {
        if (shadowRunId <= 0) {
            throw new IllegalArgumentException("Invalid shadow run identity");
        }
        return new CompensationEventRequest(Long.toString(shadowRunId), EVENT_TYPE, "-");
    }

    /** 事件类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 每次尝试最多 90 秒（短于 2 分钟 Lease），最多 5 次，10 秒起指数退避；已回放样本按唯一身份跳过，重试续跑。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(90), 5, Duration.ofSeconds(10), Duration.ofMinutes(5),
            2, 0.2);
    }

    /**
     * 回放并汇总
     * <li>批次不存在或事件格式错误为终态失败；读取候选内容失败可重试。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("SHADOW_RUN_TRANSACTION_ACTIVE", "Shadow run must execute outside a transaction");
        }
        if (event.payloadJson() != null || !event.eventKey().matches("[1-9][0-9]{0,18}")) {
            return CompensationHandlerResult.terminalFailure("SHADOW_RUN_INVALID", "Shadow run event is malformed");
        }
        ReleaseShadowRun run = reader.run(Long.parseLong(event.eventKey())).orElse(null);
        if (run == null) {
            return CompensationHandlerResult.terminalFailure("SHADOW_RUN_INVALID", "Shadow run does not exist");
        }
        if (run.getStatus() != ShadowRunStatus.RUNNING) {
            return CompensationHandlerResult.success();
        }
        try {
            replay(run);
            return CompensationHandlerResult.success();
        } catch (RuntimeException retryable) {
            log.warn("影子回放未完成, shadowRunId={}", run.getShadowRunId(), retryable);
            return CompensationHandlerResult.retryableFailure("SHADOW_RUN_INCOMPLETE", "Shadow run requires retry");
        }
    }

    /** 回放尚未比较的样本后写入汇总。 */
    void replay(ReleaseShadowRun run) {
        ExecutionSnapshot snapshot = snapshots.read(reader.candidate(run));
        List<DecisionEvaluation> samples = reader.samples(run);
        Set<Long> done = reader.replayed(run).stream().map(DecisionEvaluation::getBaselineEvaluationId)
            .collect(Collectors.toSet());
        for (DecisionEvaluation sample : samples) {
            if (!done.contains(sample.getEvaluationId())) {
                saveSample(run, snapshot, sample);
            }
        }
        List<DecisionEvaluation> replayed = reader.replayed(run);
        int missing = (int)replayed.stream().filter(sample -> reader.featureResults(sample.getEvaluationId()).stream()
            .anyMatch(result -> result.getNumberValue() == null && result.getBooleanValue() == null)).count();
        Map<Long, DecisionEvaluation> baselines = samples.stream()
            .collect(Collectors.toMap(DecisionEvaluation::getEvaluationId, Function.identity()));
        long expectedVersion = run.getVersion();
        ShadowRunSummaries.complete(run, replayed, baselines, missing, now());
        service.complete(run, expectedVersion);
    }

    /** 回放一个样本，保存结论、Feature 结果与轨迹；并发重复写入视为已完成。 */
    void saveSample(ReleaseShadowRun run, ExecutionSnapshot snapshot, DecisionEvaluation sample) {
        TransactionRiskAssessment assessment = reader.assessment(sample);
        PaymentRiskFacts.Run replay = replay(snapshot, ShadowReplayReader.input(assessment), sample.getEvaluationTime());
        DecisionEvaluation row = shadowEvaluation(run, snapshot, sample, assessment, replay);
        long evaluationId = row.getEvaluationId();
        ShadowSampleContext context = new ShadowSampleContext(row,
            PaymentRiskFacts.featureResults(evaluationId, replay, identifiers::nextIdentifier),
            PaymentRiskFacts.ruleTraces(evaluationId, replay, identifiers::nextIdentifier),
            PaymentRiskFacts.flowTraces(evaluationId, replay, identifiers::nextIdentifier));
        try {
            service.saveSample(context);
        } catch (ConcurrentRequestException alreadyReplayed) {
            log.info("影子样本已回放, shadowRunId={}, baselineEvaluationId={}", run.getShadowRunId(), sample.getEvaluationId());
        }
    }

    /**
     * 以样本的原评估时间执行候选发布
     * <li>候选执行失败是比较结果的一部分：Feature 失败记 FEATURE_ERROR，其他异常记 EVALUATION_ERROR，均保留已算出的 Feature。</li>
     * <li>超过决策点超时记 TIMED_OUT，保留引擎结果供解释。</li>
     */
    PaymentRiskFacts.Run replay(ExecutionSnapshot snapshot, PaymentRiskInput input, Instant evaluationTime) {
        Instant started = now();
        Map<Long, FeatureValue> values = null;
        try {
            values = features.read(snapshot, input, evaluationTime);
            EngineOutcome outcome = DecisionEngine.evaluate(snapshot, input, values);
            Instant completed = now();
            if (Duration.between(started, completed).toMillis() > snapshot.timeoutMillis()) {
                return new PaymentRiskFacts.Run(started, completed, TechnicalStatus.TIMED_OUT, EVALUATION_TIMEOUT, outcome,
                    values);
            }
            return new PaymentRiskFacts.Run(started, completed, TechnicalStatus.SUCCEEDED, null, outcome, values);
        } catch (FeatureComputationException failure) {
            return new PaymentRiskFacts.Run(started, now(), TechnicalStatus.FAILED,
                FeatureComputationException.FEATURE_ERROR, null, failure.results());
        } catch (RuntimeException failure) {
            return new PaymentRiskFacts.Run(started, now(), TechnicalStatus.FAILED, EVALUATION_ERROR, null, values);
        }
    }

    /** 影子评估行；比较字段完整，评估时间沿用样本以便按同一时刻比较。 */
    DecisionEvaluation shadowEvaluation(ReleaseShadowRun run, ExecutionSnapshot snapshot, DecisionEvaluation sample,
        TransactionRiskAssessment assessment, PaymentRiskFacts.Run replay) {
        boolean succeeded = replay.status() == TechnicalStatus.SUCCEEDED;
        DecisionValue decision = succeeded ? replay.outcome().decision() : null;
        boolean actionChanged = succeeded
            && !Objects.equals(PaymentRiskFacts.authentication(replay.outcome()), assessment.getAuthenticationRequirement());
        DecisionEvaluation row = new DecisionEvaluation();
        row.setEvaluationId(identifiers.nextIdentifier());
        row.setEvaluationRequestId("SHADOW:" + run.getShadowRunId() + ":" + sample.getEvaluationId());
        row.setDecisionPointId(snapshot.release().getDecisionPointId());
        row.setPointVersionId(snapshot.release().getPointVersionId());
        row.setReleaseId(run.getCandidateReleaseId());
        row.setEvaluationMode(EvaluationMode.SHADOW);
        row.setEvaluationTime(sample.getEvaluationTime());
        row.setTechnicalStatus(replay.status());
        row.setProposedDecision(decision);
        row.setBusinessDecision(decision);
        row.setStartedTime(replay.evaluationTime());
        row.setCompletedTime(replay.completedTime());
        row.setElapsedMillis(replay.elapsedMillis());
        row.setInputSnapshotHash(sample.getInputSnapshotHash());
        row.setFailureCode(replay.failureCode());
        row.setShadowRunId(run.getShadowRunId());
        row.setBaselineEvaluationId(sample.getEvaluationId());
        row.setDecisionChangedFlag(decision != sample.getBusinessDecision());
        row.setActionChangedFlag(actionChanged);
        row.setComparisonSeverity(ShadowRunSummaries.severity(sample.getBusinessDecision(), decision, actionChanged));
        row.setComparedTime(replay.completedTime());
        row.setCreateTime(replay.completedTime());
        row.setUpdateTime(replay.completedTime());
        return row;
    }

    /** UTC 微秒时间。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
