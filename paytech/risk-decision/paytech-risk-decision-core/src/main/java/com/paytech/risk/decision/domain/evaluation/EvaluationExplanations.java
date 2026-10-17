package com.paytech.risk.decision.domain.evaluation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.DecisionEvaluation;
import com.paytech.risk.decision.core.dal.dataobject.EvaluationFeatureResult;
import com.paytech.risk.decision.core.dal.dataobject.EvaluationFlowTrace;
import com.paytech.risk.decision.core.dal.dataobject.EvaluationRuleTrace;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.RuleTraceType;
import com.paytech.risk.decision.domain.enums.TechnicalStatus;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.evaluation.EvaluationExplanationView.Predicate;
import com.paytech.risk.decision.domain.evaluation.EvaluationExplanationView.Rule;
import com.paytech.risk.decision.domain.evaluation.EvaluationExplanationView.Shadow;
import com.paytech.risk.decision.domain.evaluation.EvaluationExplanationView.Step;
import com.paytech.risk.decision.domain.evaluation.EvaluationExplanationView.Variable;
import com.paytech.risk.decision.domain.flow.DecisionFlow;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.RuleView;
import com.paytech.risk.decision.domain.runtime.ExecutionSnapshot;
import com.paytech.risk.decision.domain.variable.RiskVariable;

/**
 * 由评估记录与评估所用发布的冻结内容组装解释；纯内存构造
 * <li>节点、规则集、规则的名称与编码取自发布冻结的流程与规则集版本，不随后续配置变化。</li>
 * <li>常量与集合不随评估冻结，按当前值返回并标注 CURRENT。</li>
 */
public final class EvaluationExplanations {
    /** 评估时的取值。 */
    static final String EVALUATION = "EVALUATION";

    /** 当前值，可能与评估时不同。 */
    static final String CURRENT = "CURRENT";

    /** 纯构造工具。 */
    private EvaluationExplanations() { }

    /**
     * 经过的节点
     *
     * @param traces 流程轨迹，按经过顺序
     * @param snapshot 评估所用发布的冻结内容
     * @return 带名称与规则集坐标的节点
     *         <li>轨迹引用的节点、连线或规则集不在冻结内容中属于数据损坏，以系统错误抛出。</li>
     */
    public static List<Step> path(List<EvaluationFlowTrace> traces, ExecutionSnapshot snapshot) {
        Map<String, DecisionFlow.Node> nodes = snapshot.graph().nodes().stream()
            .collect(Collectors.toMap(DecisionFlow.Node::nodeKey, Function.identity()));
        Map<String, DecisionFlow.Edge> edges = snapshot.graph().edges().stream()
            .collect(Collectors.toMap(DecisionFlow.Edge::edgeKey, Function.identity()));
        return traces.stream().map(trace -> step(trace, required(nodes, trace.getNodeKey(), "节点"),
            trace.getExitEdgeKey() == null ? null : required(edges, trace.getExitEdgeKey(), "连线"), snapshot)).toList();
    }

    /** 一个节点；规则集坐标只在规则集节点上给出。 */
    static Step step(EvaluationFlowTrace trace, DecisionFlow.Node node, DecisionFlow.Edge exit, ExecutionSnapshot snapshot) {
        DecisionRuleSetAggregate.RuleSetVersionView ruleSet = trace.getRuleSetVersionId() == null ? null
            : required(snapshot.ruleSets(), trace.getRuleSetVersionId(), "规则集版本").version();
        return new Step(trace.getStepNo(), trace.getNodeKey(), trace.getNodeType().getCode(), node.name(),
            trace.getRuleSetVersionId(), ruleSet == null ? null : ruleSet.ruleSetCode(),
            ruleSet == null ? null : ruleSet.versionNo(), trace.getMatchedFlag(),
            trace.getNodeDecision() == null ? null : trace.getNodeDecision().getCode(), trace.getReusedFlag(),
            trace.getExitEdgeKey(), exit == null ? null : exit.name());
    }

    /**
     * 执行过的规则及其谓词结果
     *
     * @param traces 规则与谓词轨迹
     * @param snapshot 评估所用发布的冻结内容
     * @return 按执行顺序的规则
     */
    public static List<Rule> rules(List<EvaluationRuleTrace> traces, ExecutionSnapshot snapshot) {
        Map<Long, List<Predicate>> predicates = traces.stream().filter(t -> t.getTraceType() == RuleTraceType.PREDICATE)
            .sorted(Comparator.comparingInt(EvaluationRuleTrace::getExecutionSequence))
            .collect(Collectors.groupingBy(EvaluationRuleTrace::getRuleId,
                Collectors.mapping(t -> new Predicate(t.getNodeId(), Boolean.TRUE.equals(t.getResultValue())),
                    Collectors.toList())));
        return traces.stream().filter(t -> t.getTraceType() == RuleTraceType.RULE)
            .sorted(Comparator.comparingInt(EvaluationRuleTrace::getExecutionSequence))
            .map(t -> rule(t, snapshot, predicates.getOrDefault(t.getRuleId(), List.of()))).toList();
    }

    /** 一条规则；编码、名称与动作取自冻结规则集版本。 */
    static Rule rule(EvaluationRuleTrace trace, ExecutionSnapshot snapshot, List<Predicate> predicates) {
        RuleView rule = required(snapshot.ruleSets(), trace.getRuleSetVersionId(), "规则集版本").rules().stream()
            .filter(value -> value.ruleId() == trace.getRuleId()).findFirst()
            .orElseThrow(() -> new ErrorException("评估轨迹引用的规则不在冻结规则集中: " + trace.getRuleId()));
        return new Rule(trace.getRuleSetVersionId(), rule.ruleId(), rule.ruleCode(), rule.ruleName(), rule.priorityNo(),
            Boolean.TRUE.equals(trace.getResultValue()), Boolean.TRUE.equals(trace.getTerminalFlag()),
            rule.action() == null ? null : rule.action().action().actionCode(), predicates);
    }

    /**
     * 发布引用的变量取值，按变量编码排序；节点输出在路径中体现，不重复列出
     *
     * @param snapshot 评估所用发布的内容（变量为当前定义与当前值）
     * @param contractValues 评估冻结的输入变量取值
     * @param features 本次评估的 Feature 结果
     * @return 变量取值；评估未计算到的 Feature 变量不列出
     */
    public static List<Variable> variables(ExecutionSnapshot snapshot, Map<String, Object> contractValues,
        List<EvaluationFeatureResult> features) {
        Map<Long, EvaluationFeatureResult> byFeature = features.stream()
            .collect(Collectors.toMap(EvaluationFeatureResult::getFeatureVersionId, Function.identity()));
        List<Variable> result = new ArrayList<>();
        snapshot.variables().values().stream()
            .sorted(Comparator.comparing((RiskVariable.Entry entry) -> entry.definition().variableCode()))
            .forEach(entry -> addVariable(result, entry, snapshot, contractValues, byFeature));
        return result;
    }

    /** 按来源取值并登记一个变量。 */
    static void addVariable(List<Variable> result, RiskVariable.Entry entry, ExecutionSnapshot snapshot,
        Map<String, Object> contractValues, Map<Long, EvaluationFeatureResult> features) {
        RiskVariable.Definition definition = entry.definition();
        VariableSource source = definition.sourceType();
        if (source == VariableSource.NODE_OUTPUT) {
            return;
        }
        if (definition.featureBacked()) {
            EvaluationFeatureResult feature = features.get(definition.featureVersionId());
            if (feature != null) {
                result.add(variable(entry, featureValue(feature), null, EVALUATION, feature));
            }
            return;
        }
        if (source == VariableSource.COLLECTION) {
            List<String> members = snapshot.collections().getOrDefault(definition.variableCode(), List.of()).stream()
                .map(String::valueOf).toList();
            result.add(variable(entry, null, members, CURRENT, null));
            return;
        }
        Object value = source == VariableSource.INPUT ? contractValues.get(definition.sourceField()) : entry.currentValue();
        result.add(variable(entry, scalar(definition, value), null, source == VariableSource.INPUT ? EVALUATION : CURRENT,
            null));
    }

    /** 标量规范文本；数值统一为去尾零的十进制，与 Feature 结果一致。 */
    static String scalar(RiskVariable.Definition definition, Object value) {
        if (value == null) {
            return null;
        }
        if (definition.valueType() == DecisionValueType.NUMBER) {
            return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
        }
        return value.toString();
    }

    /** 变量取值行；Feature 附技术状态与耗时。 */
    static Variable variable(RiskVariable.Entry entry, String value, List<String> values, String basis,
        EvaluationFeatureResult feature) {
        RiskVariable.Definition definition = entry.definition();
        return new Variable(definition.variableCode(), entry.variableName(), definition.sourceType().getCode(),
            definition.valueType().getCode(), value, values, basis,
            feature == null ? null : feature.getTechnicalStatus().getCode(), feature == null ? null : feature.getFailureCode(),
            feature == null ? null : feature.getElapsedMillis());
    }

    /** Feature 结果的规范值；失败或无值为空。 */
    static String featureValue(EvaluationFeatureResult feature) {
        if (feature.getTechnicalStatus() != TechnicalStatus.SUCCEEDED) {
            return null;
        }
        BigDecimal number = feature.getNumberValue();
        if (number != null) {
            return number.stripTrailingZeros().toPlainString();
        }
        return feature.getBooleanValue() == null ? null : feature.getBooleanValue().toString();
    }

    /**
     * 影子评估的比较结果
     *
     * @param shadows 以本评估为基线的影子评估
     * @return 比较结果，顺序不变
     */
    public static List<Shadow> shadows(List<DecisionEvaluation> shadows) {
        return shadows.stream().map(row -> new Shadow(row.getEvaluationId(), row.getShadowRunId(), result(row),
            Boolean.TRUE.equals(row.getDecisionChangedFlag()), Boolean.TRUE.equals(row.getActionChangedFlag()),
            row.getComparisonSeverity())).toList();
    }

    /**
     * 评估的对外结论：成功时为业务决定，否则为技术状态
     *
     * @param evaluation 评估
     * @return ALLOW、REVIEW、DENY、FAILED 或 TIMED_OUT
     */
    public static String result(DecisionEvaluation evaluation) {
        if (evaluation.getTechnicalStatus() != TechnicalStatus.SUCCEEDED) {
            return evaluation.getTechnicalStatus().getCode();
        }
        return evaluation.getBusinessDecision().getCode();
    }

    /** 冻结内容中必须存在的引用。 */
    static <K, V> V required(Map<K, V> values, K key, String kind) {
        V value = values.get(key);
        if (value == null) {
            throw new ErrorException("评估轨迹引用的" + kind + "不在评估所用发布中: " + key);
        }
        return value;
    }
}
