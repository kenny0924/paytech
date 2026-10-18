package com.paytech.risk.decision.service.evaluation;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.DecisionEvaluation;
import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.core.dal.dataobject.TransactionRiskAssessment;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.TechnicalStatus;
import com.paytech.risk.decision.domain.runtime.*;
import com.paytech.risk.decision.domain.runtime.EvaluationInputContract.Field;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.decision.domainservice.repository.DecisionEvaluationRepository;
import com.paytech.risk.decision.domainservice.repository.TransactionRiskAssessmentRepository;
import com.paytech.risk.decision.domainservice.service.PaymentRiskAssessmentService;
import com.paytech.risk.decision.service.processor.response.EvaluateDecisionResult;
import com.paytech.risk.decision.service.query.ActiveExecutionReleaseReader;

import lombok.AllArgsConstructor;

/**
 * 支付尝试风控点（PAYMENT_ATTEMPT_RISK，输入合同 PAYMENT_ATTEMPT v1）评估器
 * <li>业务身份为支付尝试 ID；同一支付尝试只评估一次，同风控 Action 且稳定输入相同的重放返回原结果，否则 409。</li>
 * <li>读取发布、计算 Feature 与执行流程都在事务外且只读；一个本地事务保存评估、快照、轨迹与累计事件。</li>
 * <li>执行异常记为 FAILED，超过决策点超时记为 TIMED_OUT，二者都落库并返回，Payment 不得放行。</li>
 */
@Component
@AllArgsConstructor
public class PaymentAttemptEvaluator implements DecisionPointEvaluator {
    /** 正整数标识，超过 BIGINT 上限由合同报告为违规。 */
    private static final Pattern POSITIVE_ID = EvaluationInputContract.POSITIVE_ID;

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    /** 支付输入合同；IP 字面量在构造输入时再校验。 */
    private static final EvaluationInputContract CONTRACT = new EvaluationInputContract(
        DecisionPointAuthoringDescriptor.paymentAttemptRisk().point().pointCode(), 1, POSITIVE_ID,
        List.of(new Field("paymentIntentId", true, POSITIVE_ID, 19), new Field("riskActionId", true, POSITIVE_ID, 19),
            new Field("orderId", true, POSITIVE_ID, 19), new Field("clientId", true, POSITIVE_ID, 19),
            new Field("entityNo", true, Pattern.compile("[A-Z0-9]{5}"), 5),
            new Field("paymentMethodCode", true, CODE, 64),
            new Field("captureMode", true, Pattern.compile("AUTOMATIC|MANUAL"), 9),
            new Field("amount", true, Pattern.compile("(?!0+(\\.0+)?$)(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?"), 25),
            new Field("currency", true, Pattern.compile("[A-Z]{3}"), 3), new Field("industryCode", true, CODE, 64),
            new Field("mccCode", true, Pattern.compile("[0-9]{4}"), 4),
            new Field("consumerCountryCode", false, Pattern.compile("[A-Z]{2}"), 2),
            new Field("consumerReference", false, Pattern.compile("[\\x21-\\x7E]{1,128}"), 128),
            new Field("clientIpAddress", false, null, 45), new Field("userAgent", false, null, 512),
            new Field("cardFingerprint", false, Pattern.compile("[A-Za-z0-9_\\-+/=]{8,128}"), 128),
            new Field("cardBin", false, Pattern.compile("[0-9]{6,8}"), 8)));

    private final RuntimeEvaluationExecutor executor;
    private final PaymentRiskAssessmentService service;
    private final DecisionIdentifierGenerator identifiers;
    private final ActiveExecutionReleaseReader activeReleases;
    private final DecisionEvaluationRepository evaluations;
    private final TransactionRiskAssessmentRepository assessments;

    /** 支付输入合同。 */
    @Override
    public EvaluationInputContract contract() {
        return CONTRACT;
    }

    /** 原评估重放，或读取生效发布后执行并保存。 */
    @Override
    public EvaluateDecisionResult evaluate(String businessKey, Map<String, String> inputs) {
        // 输入转换（IP 字面量非法时以参数错误拒绝）
        PaymentRiskInput input = toInput(businessKey, inputs);

        // 原评估重放
        TransactionRiskAssessment existing = assessments.findByAttempt(input.paymentAttemptId()).orElse(null);
        if (existing != null) {
            return replay(existing, input);
        }

        // 读取生效发布并在事务外执行
        ExecutionRelease release = activeReleases.require(CONTRACT.pointCode());
        RuntimeEvaluationFacts.Run run =
            executor.execute(release, input, PaymentRiskFacts.requestId(input.paymentAttemptId()));

        // 保存与响应
        PaymentRiskAssessmentContext context = PaymentRiskFacts.build(input, release, run, identifiers::nextIdentifier);
        service.save(context);
        return result(context.getAssessment(), context.getEvaluation());
    }

    /** 按支付尝试查找正式评估。 */
    @Override
    public Optional<Long> findPrimaryEvaluationId(String businessKey) {
        return assessments.findByAttempt(Long.parseLong(businessKey)).map(TransactionRiskAssessment::getEvaluationId);
    }

    /** 交易评估快照中的支付尝试、17 个输入字段与认证要求。 */
    @Override
    public EvaluationSubject subject(long primaryEvaluationId) {
        TransactionRiskAssessment assessment = assessment(primaryEvaluationId);
        PaymentRiskInput input = input(assessment);
        return new EvaluationSubject(Long.toString(input.paymentAttemptId()), inputs(input), input.contractValues(),
            outputs(assessment));
    }

    /** 交易评估快照还原的冻结输入与已保存的认证要求。 */
    @Override
    public ReplaySample replaySample(long primaryEvaluationId) {
        TransactionRiskAssessment assessment = assessment(primaryEvaluationId);
        return new ReplaySample(input(assessment), outputs(assessment));
    }

    /** 正式评估的交易评估快照；缺失属于数据损坏。 */
    TransactionRiskAssessment assessment(long primaryEvaluationId) {
        return assessments.findByEvaluation(primaryEvaluationId)
            .orElseThrow(() -> new ErrorException("正式评估缺少交易评估快照: " + primaryEvaluationId));
    }

    /** 已保存的认证要求输出；仅放行时有值。 */
    static Map<String, String> outputs(TransactionRiskAssessment assessment) {
        return assessment.getAuthenticationRequirement() == null ? Map.of()
            : Map.of(DecisionPointAuthoringDescriptor.AUTHENTICATION_REQUIREMENT,
                assessment.getAuthenticationRequirement().getCode());
    }

    /** 由交易评估快照还原冻结输入。 */
    static PaymentRiskInput input(TransactionRiskAssessment row) {
        return new PaymentRiskInput(row.getPaymentAttemptId(), row.getPaymentIntentId(), row.getRiskActionId(),
            row.getOrderId(), row.getClientId(), row.getEntityNo(), row.getPaymentMethodCode(), row.getCaptureMode(),
            row.getAmount(), row.getCurrency(), row.getIndustryCode(), row.getMccCode(), row.getConsumerCountryCode(),
            row.getConsumerReference(), row.getClientIpAddress(), row.getUserAgent(), row.getCardFingerprint(),
            row.getCardBin());
    }

    /** 统一评估请求的输入字段原值，按合同字段顺序。 */
    static Map<String, String> inputs(PaymentRiskInput input) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("paymentIntentId", Long.toString(input.paymentIntentId()));
        values.put("riskActionId", Long.toString(input.riskActionId()));
        values.put("orderId", Long.toString(input.orderId()));
        values.put("clientId", Long.toString(input.clientId()));
        values.put("entityNo", input.entityNo());
        values.put("paymentMethodCode", input.paymentMethodCode());
        values.put("captureMode", input.captureMode());
        values.put("amount", input.amount().stripTrailingZeros().toPlainString());
        values.put("currency", input.currency());
        values.put("industryCode", input.industryCode());
        values.put("mccCode", input.mccCode());
        values.put("consumerCountryCode", input.consumerCountryCode());
        values.put("consumerReference", input.consumerReference());
        values.put("clientIpAddress", input.clientIpAddress());
        values.put("userAgent", input.userAgent());
        values.put("cardFingerprint", input.cardFingerprint());
        values.put("cardBin", input.cardBin());
        return values;
    }

    /** 已通过格式校验的输入转换为冻结输入。 */
    static PaymentRiskInput toInput(String businessKey, Map<String, String> inputs) {
        return new PaymentRiskInput(Long.parseLong(businessKey), Long.parseLong(inputs.get("paymentIntentId")),
            Long.parseLong(inputs.get("riskActionId")), Long.parseLong(inputs.get("orderId")),
            Long.parseLong(inputs.get("clientId")), inputs.get("entityNo"), inputs.get("paymentMethodCode"),
            inputs.get("captureMode"), new BigDecimal(inputs.get("amount")), inputs.get("currency"),
            inputs.get("industryCode"), inputs.get("mccCode"), inputs.get("consumerCountryCode"),
            inputs.get("consumerReference"), inputs.get("clientIpAddress"), inputs.get("userAgent"),
            inputs.get("cardFingerprint"), inputs.get("cardBin"));
    }

    /** 同一风控 Action 且稳定输入相同才返回原结果。 */
    EvaluateDecisionResult replay(TransactionRiskAssessment existing, PaymentRiskInput input) {
        if (existing.getRiskActionId() != input.riskActionId()
            || !existing.getStableInputHash().equals(PaymentRiskFacts.stableHash(input))) {
            throw new ConcurrentRequestException("支付尝试已有不同内容的风控评估: " + input.paymentAttemptId());
        }
        DecisionEvaluation evaluation = evaluations.findById(existing.getEvaluationId())
            .orElseThrow(() -> new ErrorException("交易评估缺少正式评估: " + existing.getAssessmentId()));
        return result(existing, evaluation);
    }

    /** 由快照与评估构造结论；放行时附认证要求输出，技术失败返回技术状态。 */
    static EvaluateDecisionResult result(TransactionRiskAssessment assessment, DecisionEvaluation evaluation) {
        if (evaluation.getTechnicalStatus() != TechnicalStatus.SUCCEEDED) {
            return new EvaluateDecisionResult(evaluation.getEvaluationId(), evaluation.getTechnicalStatus().getCode(),
                Map.of());
        }
        DecisionValue decision = evaluation.getBusinessDecision();
        Map<String,
            String> outputs = decision == DecisionValue.ALLOW && assessment.getAuthenticationRequirement() != null
                ? Map.of(DecisionPointAuthoringDescriptor.AUTHENTICATION_REQUIREMENT,
                    assessment.getAuthenticationRequirement().getCode())
                : Map.of();
        return new EvaluateDecisionResult(evaluation.getEvaluationId(), decision.getCode(), outputs);
    }
}
