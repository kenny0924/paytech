package com.paytech.risk.decision.domain.runtime;

import java.util.*;
import java.util.function.LongSupplier;

import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.*;

/**
 * 由一次正式支付评估的输入、结果与时间构造需要保存的全部事实；纯内存构造
 * <li>稳定输入摘要只含 Payment 能重放的字段，IP 与 User-Agent 只进入受限输入摘要。</li>
 * <li>只有成功评估写入累计事件：每次写评估事件，结论为审核或拒绝时再写拒绝事件。</li>
 * <li>评估行、Feature 结果与轨迹由 {@link RuntimeEvaluationFacts} 构造，与其他运行期风控点一致。</li>
 */
public final class PaymentRiskFacts {
    /** 稳定输入摘要合同。 */
    static final String STABLE_SCHEMA = "CODE-MOR:RISK:PAYMENT_ATTEMPT_STABLE_INPUT:1";

    /** 受限输入摘要合同。 */
    static final String RESTRICTED_SCHEMA = "CODE-MOR:RISK:PAYMENT_ATTEMPT_RESTRICTED_INPUT:1";

    /** 累计事件来源类型，与来源表同名。 */
    static final String SOURCE_TYPE = "TRANSACTION_RISK_ASSESSMENT";

    /** 纯构造工具。 */
    private PaymentRiskFacts() { }

    /** 正式评估的请求身份。 */
    public static String requestId(long paymentAttemptId) {
        return "PAYMENT_ATTEMPT_RISK:" + paymentAttemptId;
    }

    /**
     * Payment 可重放字段的摘要
     *
     * @param input 冻结输入
     * @return SHA-256 小写十六进制
     */
    public static String stableHash(PaymentRiskInput input) {
        return RuntimeHashes.sha256(STABLE_SCHEMA, stableFields(input));
    }

    /**
     * 构造需要保存的事实
     *
     * @param input
     *            冻结输入
     * @param release
     *            执行发布
     * @param run
     *            执行结果、时间与技术状态
     * @param ids
     *            标识生成
     * @return 评估上下文
     */
    public static PaymentRiskAssessmentContext build(PaymentRiskInput input, ExecutionRelease release,
        RuntimeEvaluationFacts.Run run, LongSupplier ids) {
        long evaluationId = ids.getAsLong();
        long assessmentId = ids.getAsLong();
        String restrictedHash = RuntimeHashes.sha256(RESTRICTED_SCHEMA, restrictedFields(input));
        DecisionEvaluation evaluation = RuntimeEvaluationFacts.primaryEvaluation(evaluationId,
            requestId(input.paymentAttemptId()), release, run, restrictedHash);
        TransactionRiskAssessment assessment = assessment(assessmentId, evaluationId, input, run, restrictedHash);
        RuntimeEvaluationFacts.VelocityFacts velocity = RuntimeEvaluationFacts.velocityFacts(SOURCE_TYPE, assessmentId,
            input, run, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED, VelocityEventCode.PAYMENT_ATTEMPT_REJECTED,
            DecisionPointAuthoringDescriptor.paymentAttemptRisk().velocitySubjects(), ids);
        return new PaymentRiskAssessmentContext(evaluation, assessment,
            RuntimeEvaluationFacts.featureResults(evaluationId, run, ids),
            RuntimeEvaluationFacts.ruleTraces(evaluationId, run, ids),
            RuntimeEvaluationFacts.flowTraces(evaluationId, run, ids), velocity.events(), velocity.subjects());
    }

    /**
     * 支付决策点的认证要求输出
     *
     * @param outcome
     *            引擎结论
     * @return 放行时按结束节点输出解析的认证要求；其他结论为空
     */
    public static AuthenticationRequirement authentication(EngineOutcome outcome) {
        String value = outcome.outputs().get(DecisionPointAuthoringDescriptor.AUTHENTICATION_REQUIREMENT);
        return value == null ? null : AuthenticationRequirement.valueOf(value);
    }

    /** 交易评估快照；认证要求仅放行时非空。 */
    static TransactionRiskAssessment assessment(long assessmentId, long evaluationId, PaymentRiskInput input,
        RuntimeEvaluationFacts.Run run, String restrictedHash) {
        TransactionRiskAssessment row = new TransactionRiskAssessment();
        row.setAssessmentId(assessmentId);
        row.setEvaluationId(evaluationId);
        row.setPaymentIntentId(input.paymentIntentId());
        row.setPaymentAttemptId(input.paymentAttemptId());
        row.setRiskActionId(input.riskActionId());
        row.setOrderId(input.orderId());
        row.setClientId(input.clientId());
        row.setEntityNo(input.entityNo());
        row.setPaymentMethodCode(input.paymentMethodCode());
        row.setCaptureMode(input.captureMode());
        row.setAmount(input.amount());
        row.setCurrency(input.currency());
        row.setIndustryCode(input.industryCode());
        row.setMccCode(input.mccCode());
        row.setConsumerCountryCode(input.consumerCountryCode());
        row.setConsumerReference(input.consumerReference());
        row.setClientIpAddress(input.clientIpAddress());
        row.setUserAgent(input.userAgent());
        row.setCardFingerprint(input.cardFingerprint());
        row.setCardBin(input.cardBin());
        row.setAuthenticationRequirement(
            run.status() == TechnicalStatus.SUCCEEDED ? authentication(run.outcome()) : null);
        row.setStableInputHash(stableHash(input));
        row.setRestrictedInputHash(restrictedHash);
        row.setCreateTime(run.completedTime());
        return row;
    }

    /** Payment 能重放的字段。 */
    static Map<String, String> stableFields(PaymentRiskInput input) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("paymentAttemptId", Long.toString(input.paymentAttemptId()));
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
        values.put("cardFingerprint", input.cardFingerprint());
        values.put("cardBin", input.cardBin());
        return values;
    }

    /** 全部冻结输入。 */
    static Map<String, String> restrictedFields(PaymentRiskInput input) {
        Map<String, String> values = stableFields(input);
        values.put("clientIpAddress", input.clientIpAddress());
        values.put("userAgent", input.userAgent());
        return values;
    }
}
