package com.paytech.risk.decision.domain.runtime;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.core.dal.dataobject.ClientAllowedDailyTotal;
import com.paytech.risk.decision.core.dal.dataobject.DecisionEvaluation;
import com.paytech.risk.decision.core.dal.dataobject.EvaluationFeatureResult;
import com.paytech.risk.decision.core.dal.dataobject.EvaluationFlowTrace;
import com.paytech.risk.decision.core.dal.dataobject.EvaluationRuleTrace;
import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.core.dal.dataobject.VelocityEvent;
import com.paytech.risk.decision.core.dal.dataobject.VelocityEventSubject;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.EvaluationMode;
import com.paytech.risk.decision.domain.enums.FlowNodeType;
import com.paytech.risk.decision.domain.enums.RuleTraceType;
import com.paytech.risk.decision.domain.enums.TechnicalStatus;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;

/**
 * 各运行期风控点共用的评估事实构造；纯内存构造
 * <li>正式评估行、Feature 结果、规则与谓词轨迹、流程轨迹与累计事件的形状与风控点无关；风控点专属的快照由各点自行构造，累计事件的来源、事件编码与主体由各点传入。</li>
 * <li>失败与超时也保存已有的过程：已完成（含失败项）的 Feature 结果；引擎已执行完时的规则轨迹与流程轨迹。</li>
 */
public final class RuntimeEvaluationFacts {
    /** 累计事件摘要合同。 */
    static final String EVENT_SCHEMA = "CODE-MOR:RISK:VELOCITY_EVENT:1";

    /** 纯构造工具。 */
    private RuntimeEvaluationFacts() { }

    /**
     * 正式评估行；技术失败时两个决定为空并记录失败代码
     *
     * @param evaluationId 评估 ID
     * @param requestId 正式评估的请求身份，“风控点:业务身份”
     * @param release 执行发布
     * @param run 执行结果、时间与技术状态
     * @param inputHash 输入快照摘要
     * @return 评估行
     */
    public static DecisionEvaluation primaryEvaluation(long evaluationId, String requestId, ExecutionRelease release,
        Run run, String inputHash) {
        DecisionEvaluation row = new DecisionEvaluation();
        row.setEvaluationId(evaluationId);
        row.setEvaluationRequestId(requestId);
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
            .map(value -> featureResult(evaluationId, value, run, ids)).toList();
    }

    /** 一个 Feature 结果行。 */
    static EvaluationFeatureResult featureResult(long evaluationId, FeatureValue value, Run run, LongSupplier ids) {
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

    /**
     * 成功评估的累计事件与主体：每次写评估事件，结论为放行时再写放行事件，结论为审核或拒绝时再写拦截事件；失败与超时不写
     * <li>放行且本次有 CLIENT 主体时，另产生一条 Client 当日放行汇总增量（REQ-04 日 / 月额度）。</li>
     *
     * @param sourceType 来源类型，与来源表同名
     * @param sourceId 来源行主键，即本点评估事实行
     * @param input 冻结输入，提供金额、币种与主体键
     * @param run 执行结果、时间与技术状态
     * @param codes 本风控点的评估、放行与拦截事件
     * @param subjects 写入的主体类型，本次没有值的主体跳过
     * @param ids 标识生成
     * @return 事件与主体行（评估事件在前）及日汇总增量
     */
    public static VelocityFacts velocityFacts(String sourceType, long sourceId, RuntimeRiskInput input, Run run,
        VelocityEventCodes codes, List<RiskSubjectType> subjects, LongSupplier ids) {
        List<VelocityEvent> events = new ArrayList<>();
        List<VelocityEventSubject> rows = new ArrayList<>();
        if (run.status() != TechnicalStatus.SUCCEEDED) {
            return new VelocityFacts(events, rows, List.of());
        }
        addEvent(codes.assessed(), sourceType, sourceId, input, run.evaluationTime(), subjects, ids, events, rows);
        boolean allowed = run.outcome().decision() == DecisionValue.ALLOW;
        addEvent(allowed ? codes.allowed() : codes.rejected(), sourceType, sourceId, input, run.evaluationTime(), subjects,
            ids, events, rows);
        return new VelocityFacts(events, rows, allowed ? dailyTotal(codes.allowed(), input, run.evaluationTime(), ids)
            : List.of());
    }

    /** 放行的 Client 当日汇总增量；本次没有 CLIENT 主体或金额时为空。 */
    static List<ClientAllowedDailyTotal> dailyTotal(VelocityEventCode code, RuntimeRiskInput input, Instant time,
        LongSupplier ids) {
        String client = input.subjectKey(RiskSubjectType.CLIENT);
        if (client == null || input.amount() == null || input.currency() == null) {
            return List.of();
        }
        ClientAllowedDailyTotal total = new ClientAllowedDailyTotal();
        total.setDailyTotalId(ids.getAsLong());
        total.setClientId(Long.parseLong(client));
        total.setEventCode(code);
        total.setCurrency(input.currency());
        total.setEventDate(LocalDate.ofInstant(time, ZoneOffset.UTC));
        total.setAmount(input.amount());
        total.setEventCount(1);
        total.setCreateTime(time);
        total.setUpdateTime(time);
        return List.of(total);
    }

    /**
     * 一个风控点的累计事件编码
     *
     * @param assessed 每次成功评估写入的评估事件
     * @param allowed 结论为放行时写入的放行事件
     * @param rejected 结论为审核或拒绝时写入的拦截事件
     */
    public record VelocityEventCodes(VelocityEventCode assessed, VelocityEventCode allowed, VelocityEventCode rejected) {
        /** 支付尝试点。 */
        public static VelocityEventCodes paymentAttempt() {
            return new VelocityEventCodes(VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED,
                VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED, VelocityEventCode.PAYMENT_ATTEMPT_REJECTED);
        }

        /** 结账点。 */
        public static VelocityEventCodes checkout() {
            return new VelocityEventCodes(VelocityEventCode.CHECKOUT_ASSESSED, VelocityEventCode.CHECKOUT_ALLOWED,
                VelocityEventCode.CHECKOUT_REJECTED);
        }
    }

    /** 写入一条累计事件及本次存在的主体。 */
    static void addEvent(VelocityEventCode code, String sourceType, long sourceId, RuntimeRiskInput input, Instant time,
        List<RiskSubjectType> subjects, LongSupplier ids, List<VelocityEvent> events, List<VelocityEventSubject> rows) {
        VelocityEvent event = new VelocityEvent();
        event.setVelocityEventId(ids.getAsLong());
        event.setEventCode(code);
        event.setSourceType(sourceType);
        event.setSourceId(sourceId);
        event.setSourceVersion(1);
        event.setOccurredTime(time);
        event.setAmount(input.amount());
        event.setCurrency(input.currency());
        event.setEventHash(RuntimeHashes.sha256(EVENT_SCHEMA, Map.of("eventCode", code.getCode(), "sourceType", sourceType,
            "sourceId", Long.toString(sourceId), "occurredTime", time.toString(),
            "amount", input.amount().stripTrailingZeros().toPlainString(), "currency", input.currency())));
        event.setCreateTime(time);
        events.add(event);
        for (RiskSubjectType type : subjects) {
            String key = input.subjectKey(type);
            if (key != null) {
                rows.add(subject(ids.getAsLong(), event, type, key));
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

    /**
     * 一次评估的累计事件与事件主体
     *
     * @param events 事件，评估事件在前
     * @param subjects 事件主体，按事件与主体登记顺序
     * @param dailyTotals Client 当日放行汇总增量；非放行或无 CLIENT 主体时为空
     */
    public record VelocityFacts(List<VelocityEvent> events, List<VelocityEventSubject> subjects,
        List<ClientAllowedDailyTotal> dailyTotals) { }

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
