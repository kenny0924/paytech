package com.paytech.risk.decision.service.processor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.decision.core.configuration.DecisionRuntimeProperties;
import com.paytech.risk.decision.core.dal.dataobject.DecisionEvaluation;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.core.dal.dataobject.TransactionRiskAssessment;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.TechnicalStatus;
import com.paytech.risk.decision.domain.runtime.*;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.decision.domainservice.repository.DecisionEvaluationRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.repository.ExecutionReleaseRepository;
import com.paytech.risk.decision.domainservice.repository.TransactionRiskAssessmentRepository;
import com.paytech.risk.decision.domainservice.service.PaymentRiskAssessmentService;
import com.paytech.risk.decision.domainservice.service.PaymentRiskNotConfiguredException;
import com.paytech.risk.decision.service.processor.request.AssessPaymentRiskRequest;
import com.paytech.risk.decision.service.processor.response.AssessPaymentRiskResponse;
import com.paytech.risk.decision.service.query.ExecutionSnapshotReader;
import com.paytech.risk.decision.service.query.FeatureValueReader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 支付尝试风控评估（PAYMENT_ATTEMPT_RISK）
 * <li>按支付尝试加锁；同一支付尝试只评估一次，同风控 Action 且稳定输入相同的重放返回原结果，否则 409。</li>
 * <li>读取发布、计算 Feature 与执行流程都在事务外且只读；一个本地事务保存评估、快照、轨迹与累计事件。</li>
 * <li>执行异常记为 FAILED，超过决策点超时记为 TIMED_OUT，二者都落库并返回，Payment 不得放行。</li>
 * <li>没有生效发布时不写评估，抛出未配置异常。</li>
 */
@Slf4j
@Component
@AllArgsConstructor
public class AssessPaymentRiskProcessor
    extends AbstractLockProcessor<AssessPaymentRiskRequest, AssessPaymentRiskResponse> {
    /** 执行失败代码。 */
    static final String EVALUATION_ERROR = "EVALUATION_ERROR";

    /** 执行超时代码。 */
    static final String EVALUATION_TIMEOUT = "EVALUATION_TIMEOUT";

    private final Clock clock;
    private final FeatureValueReader features;
    private final DecisionRuleSetRepository points;
    private final ExecutionSnapshotReader snapshots;
    private final DecisionRuntimeProperties properties;
    private final PaymentRiskAssessmentService service;
    private final DecisionIdentifierGenerator identifiers;
    private final ExecutionReleaseRepository releases;
    private final DecisionEvaluationRepository evaluations;
    private final TransactionRiskAssessmentRepository assessments;

    /** 立即尝试获取支付尝试锁；同一支付尝试的并发评估只有一个进入。 */
    @Override
    protected LockCommand lockCommand(AssessPaymentRiskRequest request) {
        return LockCommand.tryLocks(List.of("risk:payment-attempt:" + request.getInput().paymentAttemptId()),
            DecisionResourceLocks.WAIT_TIME, DecisionResourceLocks.LEASE_TIME);
    }

    @Override
    protected AssessPaymentRiskResponse processWithinLock(AssessPaymentRiskRequest request) {
        // 原评估重放
        PaymentRiskInput input = request.getInput();
        TransactionRiskAssessment existing = assessments.findByAttempt(input.paymentAttemptId()).orElse(null);
        if (existing != null) {
            return replay(existing, input);
        }

        // 读取生效发布
        ExecutionRelease release = activeRelease();

        // 事务外执行
        PaymentRiskFacts.Run run = execute(release, input);

        // 保存与响应
        PaymentRiskAssessmentContext context = PaymentRiskFacts.build(input, release, run, identifiers::nextIdentifier);
        service.save(context);
        return response(context.getAssessment(), context.getEvaluation());
    }

    /** 同一风控 Action 且稳定输入相同才返回原结果。 */
    AssessPaymentRiskResponse replay(TransactionRiskAssessment existing, PaymentRiskInput input) {
        if (existing.getRiskActionId() != input.riskActionId()
            || !existing.getStableInputHash().equals(PaymentRiskFacts.stableHash(input))) {
            throw new ConcurrentRequestException("支付尝试已有不同内容的风控评估: " + input.paymentAttemptId());
        }
        DecisionEvaluation evaluation = evaluations.findById(existing.getEvaluationId())
            .orElseThrow(() -> new ErrorException("交易评估缺少正式评估: " + existing.getAssessmentId()));
        return response(existing, evaluation);
    }

    /** 当前环境的支付决策点生效发布。 */
    ExecutionRelease activeRelease() {
        String pointCode = DecisionPointAuthoringDescriptor.paymentAttemptRisk().point().pointCode();
        DecisionPoint point =
            points.findPointByCode(pointCode).orElseThrow(() -> new ErrorException("支付决策点未初始化: " + pointCode));
        return releases.findActive(point.decisionPointId(), properties.getEnvironment())
            .orElseThrow(() -> new PaymentRiskNotConfiguredException(pointCode, properties.getEnvironment().getCode()));
    }

    /** 读取执行内容、计算 Feature 并执行流程；异常与超时转为技术终态。 */
    PaymentRiskFacts.Run execute(ExecutionRelease release, PaymentRiskInput input) {
        Instant started = now();
        try {
            ExecutionSnapshot snapshot = snapshots.read(release);
            Map<Long, FeatureValue> values = features.read(snapshot, input, started);
            EngineOutcome outcome = DecisionEngine.evaluate(snapshot, input, values);
            Instant completed = now();
            if (Duration.between(started, completed).toMillis() > snapshot.descriptor().point().timeoutMillis()) {
                return new PaymentRiskFacts.Run(started, completed, TechnicalStatus.TIMED_OUT, EVALUATION_TIMEOUT, null,
                    null);
            }
            return new PaymentRiskFacts.Run(started, completed, TechnicalStatus.SUCCEEDED, null, outcome, values);
        } catch (RuntimeException failure) {
            // 技术失败是评估合同的一部分：记录为 FAILED 并返回，Payment 据此不放行。
            log.error("支付风控评估执行失败, paymentAttemptId={}, releaseId={}", input.paymentAttemptId(), release.getReleaseId(),
                failure);
            return new PaymentRiskFacts.Run(started, now(), TechnicalStatus.FAILED, EVALUATION_ERROR, null, null);
        }
    }

    /** 由快照与评估构造结果；技术失败返回技术状态。 */
    static AssessPaymentRiskResponse response(TransactionRiskAssessment assessment, DecisionEvaluation evaluation) {
        String result = evaluation.getTechnicalStatus() == TechnicalStatus.SUCCEEDED
            ? evaluation.getBusinessDecision().getCode() : evaluation.getTechnicalStatus().getCode();
        return new AssessPaymentRiskResponse(assessment.getAssessmentId(), evaluation.getEvaluationId(), result,
            assessment.getAuthenticationRequirement());
    }

    /** UTC 微秒时间。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
