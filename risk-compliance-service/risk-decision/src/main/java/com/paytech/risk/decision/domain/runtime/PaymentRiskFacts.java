package com.paytech.risk.decision.domain.runtime;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.core.dal.dataobject.DecisionEvaluation;
import com.paytech.risk.decision.core.dal.dataobject.EvaluationFeatureResult;
import com.paytech.risk.decision.core.dal.dataobject.EvaluationRuleTrace;
import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.core.dal.dataobject.TransactionRiskAssessment;
import com.paytech.risk.decision.core.dal.dataobject.VelocityEvent;
import com.paytech.risk.decision.core.dal.dataobject.VelocityEventSubject;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.EvaluationMode;
import com.paytech.risk.decision.domain.enums.RuleTraceType;
import com.paytech.risk.decision.domain.enums.TechnicalStatus;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;

/**
 * 由一次正式支付评估的输入、结果与时间构造需要保存的全部事实；纯内存构造
 * <li>稳定输入摘要只含 Payment 能重放的字段，IP 与 User-Agent 只进入受限输入摘要。</li>
 * <li>只有成功评估写入累计事件：每次写评估事件，结论为审核或拒绝时再写拒绝事件。</li>
 */
public final class PaymentRiskFacts {
    /** 稳定输入摘要合同。 */
    static final String STABLE_SCHEMA = "CODE-MOR:RISK:PAYMENT_ATTEMPT_STABLE_INPUT:1";

    /** 受限输入摘要合同。 */
    static final String RESTRICTED_SCHEMA = "CODE-MOR:RISK:PAYMENT_ATTEMPT_RESTRICTED_INPUT:1";

    /** 累计事件摘要合同。 */
    static final String EVENT_SCHEMA = "CODE-MOR:RISK:VELOCITY_EVENT:1";

    /** 累计事件来源类型。 */
    static final String SOURCE_TYPE = "TRANSACTION_RISK_ASSESSMENT";

    /** 写入累计事件主体的类型；卡 BIN 只用于名单。 */
    static final List<RiskSubjectType> EVENT_SUBJECTS = List.of(RiskSubjectType.CLIENT,
        RiskSubjectType.OPERATING_ENTITY, RiskSubjectType.CONSUMER, RiskSubjectType.IP_ADDRESS,
        RiskSubjectType.CARD_FINGERPRINT);

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
     * @param input 冻结输入
     * @param release 执行发布
     * @param run 执行结果、时间与技术状态
     * @param ids 标识生成
     * @return 评估上下文
     */
    public static PaymentRiskAssessmentContext build(PaymentRiskInput input, ExecutionRelease release, Run run,
        LongSupplier ids) {
        long evaluationId = ids.getAsLong();
        long assessmentId = ids.getAsLong();
        String restrictedHash = RuntimeHashes.sha256(RESTRICTED_SCHEMA, restrictedFields(input));
        DecisionEvaluation evaluation = evaluation(evaluationId, input, release, run, restrictedHash);
        TransactionRiskAssessment assessment = assessment(assessmentId, evaluationId, input, run, restrictedHash);
        if (run.status() != TechnicalStatus.SUCCEEDED) {
            return new PaymentRiskAssessmentContext(evaluation, assessment, List.of(), List.of(), List.of(), List.of());
        }
        List<VelocityEvent> events = new ArrayList<>();
        List<VelocityEventSubject> subjects = new ArrayList<>();
        addEvent(VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED, assessmentId, input, run.evaluationTime(), ids, events, subjects);
        if (run.outcome().decision() != DecisionValue.ALLOW) {
            addEvent(VelocityEventCode.PAYMENT_ATTEMPT_REJECTED, assessmentId, input, run.evaluationTime(), ids, events,
                subjects);
        }
        return new PaymentRiskAssessmentContext(evaluation, assessment, featureResults(evaluationId, run, ids),
            ruleTraces(evaluationId, run, ids), events, subjects);
    }

    /** 评估行；技术失败时两个决定为空并记录失败代码。 */
    static DecisionEvaluation evaluation(long evaluationId, PaymentRiskInput input, ExecutionRelease release, Run run,
        String inputHash) {
        DecisionEvaluation row = new DecisionEvaluation();
        row.setEvaluationId(evaluationId);
        row.setEvaluationRequestId(requestId(input.paymentAttemptId()));
        row.setDecisionPointId(release.getDecisionPointId());
        row.setPointVersionId(release.getPointVersionId());
        row.setReleaseId(release.getReleaseId());
        row.setEvaluationMode(EvaluationMode.PRIMARY);
        row.setEvaluationTime(run.evaluationTime());
        row.setTechnicalStatus(run.status());
        DecisionValue decision = run.status() == TechnicalStatus.SUCCEEDED ? run.outcome().decision() : null;
        row.setProposedDecision(decision);
        row.setBusinessDecision(decision);
        row.setStartedTime(run.evaluationTime());
        row.setCompletedTime(run.completedTime());
        row.setElapsedMillis(run.elapsedMillis());
        row.setInputSnapshotHash(inputHash);
        row.setFailureCode(run.failureCode());
        row.setCreateTime(run.completedTime());
        row.setUpdateTime(run.completedTime());
        return row;
    }

    /** 交易评估快照；认证要求仅放行时非空。 */
    static TransactionRiskAssessment assessment(long assessmentId, long evaluationId, PaymentRiskInput input, Run run,
        String restrictedHash) {
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
        row.setAuthenticationRequirement(run.status() == TechnicalStatus.SUCCEEDED
            ? run.outcome().authenticationRequirement() : null);
        row.setStableInputHash(stableHash(input));
        row.setRestrictedInputHash(restrictedHash);
        row.setCreateTime(run.completedTime());
        return row;
    }

    /** 每个 Feature 一行，按执行顺序。 */
    static List<EvaluationFeatureResult> featureResults(long evaluationId, Run run, LongSupplier ids) {
        return run.features().values().stream().sorted(Comparator.comparingInt(FeatureValue::executionSequence))
            .map(value -> {
                EvaluationFeatureResult row = new EvaluationFeatureResult();
                row.setResultId(ids.getAsLong());
                row.setEvaluationId(evaluationId);
                row.setFeatureVersionId(value.featureVersionId());
                row.setExecutionSequence(value.executionSequence());
                row.setTechnicalStatus(TechnicalStatus.SUCCEEDED);
                row.setValueType(value.valueType());
                row.setNumberValue(value.valueType() == DecisionValueType.NUMBER && value.value() != null
                    ? new BigDecimal(value.value().toString()) : null);
                row.setBooleanValue(value.valueType() == DecisionValueType.BOOLEAN ? (Boolean)value.value() : null);
                row.setElapsedMillis(value.elapsedMillis());
                row.setCreateTime(run.completedTime());
                return row;
            }).toList();
    }

    /**
     * 影子评估的 Feature 结果
     *
     * @param evaluationId 影子评估 ID
     * @param values 候选计算的 Feature
     * @param time 写入时间
     * @param ids 标识生成
     * @return 按执行顺序的结果行
     */
    public static List<EvaluationFeatureResult> shadowFeatureResults(long evaluationId, Map<Long, FeatureValue> values,
        Instant time, LongSupplier ids) {
        return featureResults(evaluationId, new Run(time, time, TechnicalStatus.SUCCEEDED, null, null, values), ids);
    }

    /** 规则行与谓词行各自从 1 编号。 */
    static List<EvaluationRuleTrace> ruleTraces(long evaluationId, Run run, LongSupplier ids) {
        List<EvaluationRuleTrace> rows = new ArrayList<>();
        int sequence = 0;
        for (EngineOutcome.RuleTrace trace : run.outcome().ruleTraces()) {
            EvaluationRuleTrace row = trace(evaluationId, RuleTraceType.RULE, trace.ruleId(), ++sequence, run, ids);
            row.setRuleSetVersionId(trace.ruleSetVersionId());
            row.setResultValue(trace.matched());
            row.setTerminalFlag(trace.terminal());
            row.setRuleResultSlot(Boolean.TRUE);
            rows.add(row);
        }
        sequence = 0;
        for (EngineOutcome.PredicateTrace trace : run.outcome().predicateTraces()) {
            EvaluationRuleTrace row = trace(evaluationId, RuleTraceType.PREDICATE, trace.ruleId(), ++sequence, run, ids);
            row.setNodeId(trace.nodeId());
            row.setResultValue(trace.result());
            row.setPredicateResultSlot(Boolean.TRUE);
            rows.add(row);
        }
        return rows;
    }

    /** 轨迹公共字段。 */
    static EvaluationRuleTrace trace(long evaluationId, RuleTraceType type, long ruleId, int sequence, Run run,
        LongSupplier ids) {
        EvaluationRuleTrace row = new EvaluationRuleTrace();
        row.setTraceId(ids.getAsLong());
        row.setEvaluationId(evaluationId);
        row.setTraceType(type);
        row.setRuleId(ruleId);
        row.setExecutionSequence(sequence);
        row.setCreateTime(run.completedTime());
        return row;
    }

    /** 写入一条累计事件及本次存在的主体。 */
    static void addEvent(VelocityEventCode code, long assessmentId, PaymentRiskInput input, Instant time,
        LongSupplier ids, List<VelocityEvent> events, List<VelocityEventSubject> subjects) {
        VelocityEvent event = new VelocityEvent();
        event.setVelocityEventId(ids.getAsLong());
        event.setEventCode(code);
        event.setSourceType(SOURCE_TYPE);
        event.setSourceId(assessmentId);
        event.setSourceVersion(1);
        event.setOccurredTime(time);
        event.setAmount(input.amount());
        event.setCurrency(input.currency());
        event.setEventHash(RuntimeHashes.sha256(EVENT_SCHEMA, Map.of("eventCode", code.getCode(), "sourceType", SOURCE_TYPE,
            "sourceId", Long.toString(assessmentId), "occurredTime", time.toString(),
            "amount", input.amount().stripTrailingZeros().toPlainString(), "currency", input.currency())));
        event.setCreateTime(time);
        events.add(event);
        for (RiskSubjectType type : EVENT_SUBJECTS) {
            String key = input.subjectKey(type);
            if (key != null) {
                subjects.add(subject(ids.getAsLong(), event, type, key));
            }
        }
    }

    /** 事件主体，冻结事件编码与时间供窗口查询。 */
    static VelocityEventSubject subject(long id, VelocityEvent event, RiskSubjectType type, String key) {
        VelocityEventSubject subject = new VelocityEventSubject();
        subject.setEventSubjectId(id);
        subject.setVelocityEventId(event.getVelocityEventId());
        subject.setSubjectType(type);
        subject.setSubjectKey(key);
        subject.setEventCode(event.getEventCode());
        subject.setOccurredTime(event.getOccurredTime());
        subject.setCreateTime(event.getCreateTime());
        return subject;
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

    /**
     * 一次执行的结果与时间
     *
     * @param evaluationTime 评估时间锚点，即开始时间
     * @param completedTime 完成时间
     * @param status 技术状态
     * @param failureCode 失败代码；成功时为空
     * @param outcome 引擎结论；技术失败时为空
     * @param features Feature 结果；技术失败时为空
     */
    public record Run(Instant evaluationTime, Instant completedTime, TechnicalStatus status, String failureCode,
        EngineOutcome outcome, Map<Long, FeatureValue> features) {
        /** 执行耗时毫秒。 */
        public int elapsedMillis() {
            return (int)Duration.between(evaluationTime, completedTime).toMillis();
        }
    }
}
