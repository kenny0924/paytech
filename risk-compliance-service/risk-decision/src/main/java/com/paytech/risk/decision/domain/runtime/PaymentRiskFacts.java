package com.paytech.risk.decision.domain.runtime;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.LongSupplier;

import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.*;

/**
 * 由一次正式支付评估的输入、结果与时间构造需要保存的全部事实；纯内存构造
 * <li>稳定输入摘要只含 Payment 能重放的字段，IP 与 User-Agent 只进入受限输入摘要。</li>
 * <li>只有成功评估写入累计事件：每次写评估事件，结论为审核或拒绝时再写拒绝事件。</li>
 * <li>失败与超时也保存已有的过程：已完成（含失败项）的 Feature 结果；引擎已执行完时的规则轨迹与流程轨迹。</li>
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
    public static PaymentRiskAssessmentContext build(PaymentRiskInput input, ExecutionRelease release, Run run,
        LongSupplier ids) {
        long evaluationId = ids.getAsLong();
        long assessmentId = ids.getAsLong();
        String restrictedHash = RuntimeHashes.sha256(RESTRICTED_SCHEMA, restrictedFields(input));
        DecisionEvaluation evaluation = evaluation(evaluationId, input, release, run, restrictedHash);
        TransactionRiskAssessment assessment = assessment(assessmentId, evaluationId, input, run, restrictedHash);
        List<VelocityEvent> events = new ArrayList<>();
        List<VelocityEventSubject> subjects = new ArrayList<>();
        if (run.status() == TechnicalStatus.SUCCEEDED) {
            addEvent(VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED, assessmentId, input, run.evaluationTime(), ids, events,
                subjects);
        }
        if (run.status() == TechnicalStatus.SUCCEEDED && run.outcome().decision() != DecisionValue.ALLOW) {
            addEvent(VelocityEventCode.PAYMENT_ATTEMPT_REJECTED, assessmentId, input, run.evaluationTime(), ids, events,
                subjects);
        }
        return new PaymentRiskAssessmentContext(evaluation, assessment, featureResults(evaluationId, run, ids),
            ruleTraces(evaluationId, run, ids), flowTraces(evaluationId, run, ids), events, subjects);
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
        row.setAuthenticationRequirement(
            run.status() == TechnicalStatus.SUCCEEDED ? authentication(run.outcome()) : null);
        row.setStableInputHash(stableHash(input));
        row.setRestrictedInputHash(restrictedHash);
        row.setCreateTime(run.completedTime());
        return row;
    }

    /**
     * Feature 结果行，按执行顺序；失败项记 FAILED 与失败代码
     *
     * @param evaluationId 评估 ID
     * @param run 执行结果；没有算到 Feature 时为空集合
     * @param ids 标识生成
     * @return 结果行
     */
    public static List<EvaluationFeatureResult> featureResults(long evaluationId, Run run, LongSupplier ids) {
        if (run.features() == null) {
            return List.of();
        }
        return run.features().values().stream().sorted(Comparator.comparingInt(FeatureValue::executionSequence))
            .map(value -> {
                EvaluationFeatureResult row = new EvaluationFeatureResult();
                row.setResultId(ids.getAsLong());
                row.setEvaluationId(evaluationId);
                row.setFeatureVersionId(value.featureVersionId());
                row.setExecutionSequence(value.executionSequence());
                row.setTechnicalStatus(value.failed() ? TechnicalStatus.FAILED : TechnicalStatus.SUCCEEDED);
                row.setValueType(value.valueType());
                row.setNumberValue(value.valueType() == DecisionValueType.NUMBER && value.value() != null
                    ? new BigDecimal(value.value().toString()) : null);
                row.setBooleanValue(value.valueType() == DecisionValueType.BOOLEAN ? (Boolean)value.value() : null);
                row.setElapsedMillis(value.elapsedMillis());
                row.setFailureCode(value.failureCode());
                row.setCreateTime(run.completedTime());
                return row;
            }).toList();
    }

    /**
     * 规则行与谓词行，各自从 1 编号
     *
     * @param evaluationId 评估 ID
     * @param run 执行结果；引擎未执行完时为空集合
     * @param ids 标识生成
     * @return 轨迹行
     */
    public static List<EvaluationRuleTrace> ruleTraces(long evaluationId, Run run, LongSupplier ids) {
        if (run.outcome() == null) {
            return List.of();
        }
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

    /**
     * 流程轨迹行，按经过顺序从 1 编号
     *
     * @param evaluationId 评估 ID
     * @param run 执行结果；引擎未执行完时为空集合
     * @param ids 标识生成
     * @return 轨迹行
     */
    public static List<EvaluationFlowTrace> flowTraces(long evaluationId, Run run, LongSupplier ids) {
        if (run.outcome() == null) {
            return List.of();
        }
        List<EvaluationFlowTrace> rows = new ArrayList<>();
        for (EngineOutcome.FlowStep step : run.outcome().flowSteps()) {
            EvaluationFlowTrace row = new EvaluationFlowTrace();
            row.setTraceId(ids.getAsLong());
            row.setEvaluationId(evaluationId);
            row.setStepNo(rows.size() + 1);
            row.setNodeKey(step.nodeKey());
            row.setNodeType(FlowNodeType.valueOf(step.nodeType()));
            row.setRuleSetVersionId(step.ruleSetVersionId());
            row.setMatchedFlag(step.matched());
            row.setNodeDecision(step.decision() == null ? null : DecisionValue.valueOf(step.decision()));
            row.setReusedFlag(step.reused());
            row.setExitEdgeKey(step.exitEdgeKey());
            row.setCreateTime(run.completedTime());
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
     * @param evaluationTime
     *            评估时间锚点，即开始时间
     * @param completedTime
     *            完成时间
     * @param status
     *            技术状态
     * @param failureCode
     *            失败代码；成功时为空
     * @param outcome
     *            引擎结论；引擎未执行完时为空，超时时保留
     * @param features
     *            Feature 结果（含失败项）；一个都没有算到时为空
     */
    public record Run(Instant evaluationTime, Instant completedTime, TechnicalStatus status, String failureCode,
        EngineOutcome outcome, Map<Long, FeatureValue> features) {
        /** 执行耗时毫秒。 */
        public int elapsedMillis() {
            return (int)Duration.between(evaluationTime, completedTime).toMillis();
        }
    }
}
