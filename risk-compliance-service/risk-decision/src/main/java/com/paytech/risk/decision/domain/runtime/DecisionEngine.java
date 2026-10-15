package com.paytech.risk.decision.domain.runtime;

import java.util.*;
import java.util.function.Function;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.NodeType;
import com.paytech.risk.decision.domain.enums.NumericUnit;
import com.paytech.risk.decision.domain.flow.DecisionFlow;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.NodeView;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.RuleView;
import com.paytech.risk.decision.domain.runtime.EngineOutcome.FlowStep;
import com.paytech.risk.decision.domain.runtime.EngineOutcome.PredicateTrace;
import com.paytech.risk.decision.domain.runtime.EngineOutcome.RuleTrace;
import com.paytech.risk.decision.domain.variable.RiskVariable;

/**
 * 按冻结决策流执行一次评估；纯内存计算，不访问数据库
 * <li>规则集节点内按优先级执行启用规则，首个带终态动作的命中规则结束本节点（FIRST_TERMINAL）。</li>
 * <li>节点结果只作为连线条件输入；最终决定只由结束节点给出。</li>
 * <li>同一规则集版本在一次评估中只执行一次，再次经过时复用结果。</li>
 */
public final class DecisionEngine {
    /** 防御性的节点执行上限；流程保存时已校验无环。 */
    static final int MAX_STEPS = 50;

    private final ExecutionSnapshot snapshot;
    private final Map<String, Object> inputs;
    private final Map<Long, FeatureValue> features;
    private final Map<Long, NodeResult> ruleSetResults = new HashMap<>();
    private final List<RuleTrace> ruleTraces = new ArrayList<>();
    private final List<PredicateTrace> predicateTraces = new ArrayList<>();
    private final List<FlowStep> flowSteps = new ArrayList<>();
    private NodeResult currentNode;

    /** 绑定一次评估的输入；实例只能执行一次。 */
    private DecisionEngine(ExecutionSnapshot snapshot, PaymentRiskInput input, Map<Long, FeatureValue> features) {
        this.snapshot = snapshot;
        this.inputs = input.contractValues();
        this.features = features;
    }

    /**
     * 执行评估
     *
     * @param snapshot
     *            执行发布内容
     * @param input
     *            冻结输入
     * @param features
     *            已计算的 Feature 结果，按 Feature Version 索引
     * @return 结论与轨迹
     *         <li>流程损坏、引用缺失等异常以系统错误抛出，由调用方记为技术失败。</li>
     */
    public static EngineOutcome evaluate(ExecutionSnapshot snapshot, PaymentRiskInput input,
        Map<Long, FeatureValue> features) {
        return new DecisionEngine(snapshot, input, features).run();
    }

    /** 从开始节点沿连线走到结束节点，并按经过顺序记录节点。 */
    EngineOutcome run() {
        Map<String, DecisionFlow.Node> nodes = new HashMap<>();
        snapshot.graph().nodes().forEach(node -> nodes.put(node.nodeKey(), node));
        DecisionFlow.Node node = snapshot.graph().nodes().stream().filter(value -> "START".equals(value.type()))
            .findFirst().orElseThrow(() -> new ErrorException("决策流缺少开始节点"));
        for (int step = 0; step < MAX_STEPS; step++) {
            if ("END".equals(node.type())) {
                flowSteps.add(new FlowStep(node.nodeKey(), node.type(), null, null, node.decisionCode(), null, null));
                return outcome(node);
            }
            Boolean reused = null;
            if ("RULE_SET".equals(node.type())) {
                reused = ruleSetResults.containsKey(node.ruleSetVersionId());
                currentNode = ruleSetResults.computeIfAbsent(node.ruleSetVersionId(), this::runRuleSet);
            }
            DecisionFlow.Edge exit = exitEdge(node);
            flowSteps.add(step(node, reused, exit));
            node = nodes.get(exit.targetNodeKey());
            if (node == null) {
                throw new ErrorException("决策流连线指向不存在的节点");
            }
        }
        throw new ErrorException("决策流执行超过节点上限");
    }

    /** 非结束节点的经过记录；规则集节点附带本节点结果。 */
    FlowStep step(DecisionFlow.Node node, Boolean reused, DecisionFlow.Edge exit) {
        if (reused == null) {
            return new FlowStep(node.nodeKey(), node.type(), null, null, null, null, exit.edgeKey());
        }
        return new FlowStep(node.nodeKey(), node.type(), node.ruleSetVersionId(), currentNode.matched(),
            currentNode.decision(), reused, exit.edgeKey());
    }

    /** 由结束节点形成结论；放行时返回该节点冻结的附加输出（保存时已补齐默认值），其他结论没有输出。 */
    EngineOutcome outcome(DecisionFlow.Node end) {
        DecisionValue decision = DecisionValue.valueOf(end.decisionCode());
        Map<String, String> outputs = decision == DecisionValue.ALLOW ? end.outputs() : Map.of();
        return new EngineOutcome(decision, end.reasonCode(), Map.copyOf(outputs), List.copyOf(ruleTraces),
            List.copyOf(predicateTraces), List.copyOf(flowSteps));
    }

    /** 条件连线按优先级取首个成立的，否则走 ELSE；无条件连线直接走。 */
    DecisionFlow.Edge exitEdge(DecisionFlow.Node node) {
        List<DecisionFlow.Edge> edges =
            snapshot.graph().edges().stream().filter(edge -> edge.sourceNodeKey().equals(node.nodeKey())).toList();
        for (DecisionFlow.Edge edge : edges.stream().filter(edge -> "CONDITIONAL".equals(edge.type()))
            .sorted(Comparator.comparingInt(DecisionFlow.Edge::priority)).toList()) {
            if (matches(edge.condition().root())) {
                return edge;
            }
        }
        return edges.stream().filter(edge -> "ELSE".equals(edge.type()) || "UNCONDITIONAL".equals(edge.type()))
            .findFirst().orElseThrow(() -> new ErrorException("决策流节点没有可用出口: " + node.nodeKey()));
    }

    /** 连线条件只接受统一变量格式。 */
    boolean matches(DecisionFlow.Expression expression) {
        if (expression instanceof DecisionFlow.Group group) {
            return combine(group.operator(), group.children().stream().map(this::matches).toList());
        }
        DecisionFlow.Predicate predicate = (DecisionFlow.Predicate)expression;
        if (predicate.operands() == null) {
            throw new ErrorException("运行期不支持旧格式流程条件");
        }
        return PredicateEvaluator.evaluate(
            com.paytech.risk.decision.domain.enums.ComparisonOperator.valueOf(predicate.operator()),
            predicate.operands(), this::resolve);
    }

    /** 执行一个规则集版本，FIRST_TERMINAL。 */
    NodeResult runRuleSet(Long ruleSetVersionId) {
        DecisionRuleSetAggregate ruleSet = snapshot.ruleSets().get(ruleSetVersionId);
        if (ruleSet == null) {
            throw new ErrorException("发布缺少规则集版本: " + ruleSetVersionId);
        }
        boolean matched = false;
        List<RuleView> rules = ruleSet.rules().stream().filter(RuleView::enabledFlag)
            .sorted(Comparator.comparingInt(RuleView::priorityNo)).toList();
        for (RuleView rule : rules) {
            boolean hit = condition(rule);
            boolean terminal = hit && rule.action() != null;
            ruleTraces.add(new RuleTrace(ruleSetVersionId, rule.ruleId(), hit, terminal));
            matched |= hit;
            if (terminal) {
                return new NodeResult(true,
                    DecisionActionRegistry.instance().require(rule.action().action()).decisionCode().getCode());
            }
        }
        return new NodeResult(matched, null);
    }

    /** 求值规则条件树，并记录每个谓词。 */
    boolean condition(RuleView rule) {
        Map<Long, List<NodeView>> children = new HashMap<>();
        NodeView root = null;
        for (NodeView node : rule.conditionNodes()) {
            if (node.parentNodeId() == null) {
                root = node;
            } else {
                children.computeIfAbsent(node.parentNodeId(), ignored -> new ArrayList<>()).add(node);
            }
        }
        if (root == null) {
            throw new ErrorException("规则条件缺少根节点: " + rule.ruleId());
        }
        return node(rule.ruleId(), root, children);
    }

    /** 组合节点求全部子节点以保留完整轨迹。 */
    boolean node(long ruleId, NodeView node, Map<Long, List<NodeView>> children) {
        if (node.nodeType() == NodeType.PREDICATE) {
            boolean result = PredicateEvaluator.evaluate(node.comparisonOperator(),
                node.operands().stream().map(DecisionRuleSetAggregate.OperandView::variableOperand).toList(),
                this::resolve);
            predicateTraces.add(new PredicateTrace(ruleId, node.nodeId(), result));
            return result;
        }
        List<Boolean> results = children.getOrDefault(node.nodeId(), List.of()).stream()
            .sorted(Comparator.comparingInt(NodeView::sequenceNo)).map(child -> node(ruleId, child, children)).toList();
        return combine(node.groupOperator().getCode(), results);
    }

    /** AND、OR、NOT 组合。 */
    static boolean combine(String operator, List<Boolean> results) {
        return switch (operator) {
            case "AND" -> results.stream().allMatch(Boolean::booleanValue);
            case "OR" -> results.stream().anyMatch(Boolean::booleanValue);
            case "NOT" -> results.size() == 1 && !results.getFirst();
            default -> throw new ErrorException("条件组合运算符无效: " + operator);
        };
    }

    /** 按变量来源取本次评估中的值。 */
    ResolvedValue resolve(String variableCode) {
        RiskVariable.Entry entry = snapshot.variables().get(variableCode);
        if (entry == null) {
            throw new ErrorException("发布引用的变量未加载: " + variableCode);
        }
        RiskVariable.Definition definition = entry.definition();
        if (definition.sourceType() == com.paytech.risk.decision.domain.enums.VariableSource.COLLECTION) {
            return new ResolvedValue(definition.valueType(), null, snapshot.collections().get(variableCode), null);
        }
        return ResolvedValue.scalar(definition.valueType(), scalar(entry), currency(definition, this::resolve));
    }

    /** 输入、常量、节点输出与 Feature 的标量值。 */
    Object scalar(RiskVariable.Entry entry) {
        RiskVariable.Definition definition = entry.definition();
        return switch (definition.sourceType()) {
            case INPUT -> inputs.get(definition.sourceField());
            case CONSTANT -> entry.currentValue();
            case NODE_OUTPUT -> currentNode == null ? null : currentNode.value(definition.sourceField());
            case VELOCITY, LIST -> {
                FeatureValue value = features.get(definition.featureVersionId());
                if (value == null) {
                    throw new ErrorException("Feature 未计算: " + definition.variableCode());
                }
                yield value.value();
            }
            case COLLECTION -> null;
        };
    }

    /** 金额变量的实际币种：固定币种或币种变量的当前值。 */
    static String currency(RiskVariable.Definition definition, Function<String, ResolvedValue> variables) {
        if (definition.numericUnit() != NumericUnit.MONEY) {
            return null;
        }
        return definition.currencyCode() != null ? definition.currencyCode()
            : (String)variables.apply(definition.currencyVariableCode()).value();
    }

    /**
     * 规则集节点的输出，供其出线条件使用
     *
     * @param matched
     *            是否有规则命中
     * @param decision
     *            终态动作的业务决定 Code；没有终态命中时为空
     */
    record NodeResult(boolean matched, String decision) {
        /** 按节点输出合同字段取值。 */
        Object value(String field) {
            return switch (field) {
                case "matched" -> matched;
                case "decision" -> decision;
                default -> throw new ErrorException("节点输出字段无效: " + field);
            };
        }
    }
}
