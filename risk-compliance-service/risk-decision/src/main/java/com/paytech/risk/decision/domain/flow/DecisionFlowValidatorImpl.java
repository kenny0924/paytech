package com.paytech.risk.decision.domain.flow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.NodeType;
import com.paytech.risk.decision.domain.enums.OperandRole;
import com.paytech.risk.decision.domain.enums.OperandSource;
import com.paytech.risk.decision.domain.flow.DecisionFlow.*;
import com.paytech.risk.decision.domain.rule.ChangeReasonPolicy;
import com.paytech.risk.decision.domain.rule.RuleAstValidator;
import com.paytech.risk.decision.domain.variable.*;
import com.paytech.risk.decision.domain.enums.VariableFormat;
import com.paytech.risk.decision.domainservice.service.RiskVariableException;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.*;

/** 仅检查有界 DAG 和条件配置，绝不求值或推断条件覆盖关系。 */
@Component
public final class DecisionFlowValidatorImpl implements DecisionFlowValidator {
    private final RuleAstValidator ruleValidator;
    private final DecisionFlowJsonCodec codec;

    public DecisionFlowValidatorImpl(RuleAstValidator ruleValidator, DecisionFlowJsonCodec codec) {
        this.ruleValidator = ruleValidator;
        this.codec = codec;
    }

    @Override
    public Checked check(Save command) {
        var errors = new Errors();
        if (command == null || command.graph() == null) {
            errors.add("INVALID_GRAPH", null, null, "graph", "请配置完整流程");
            return new Checked(command, errors.report());
        }
        if (!"CHECKOUT_PREPARE".equals(command.pointCode()))
            errors.add("INVALID_POINT", null, null, "pointCode", "当前决策点不支持流程配置");
        if (!code(command.flowCode())) errors.add("INVALID_CODE", null, null, "flowCode", "流程编码格式无效");
        if ((command.baseFlowVersionId() == null) != (command.expectedVersion() == null))
            errors.add("BASE_CONFLICT", null, null, "baseFlowVersionId", "编辑基准必须同时提供");
        String name = text(command.flowName(), 100, "flowName", null, null, errors);
        String reason = text(command.changeReason(), 500, "changeReason", null, null, errors);
        Graph graph = command.graph();
        if (graph.schemaVersion() != 1 || graph.nodes().size() > 50 || graph.edges().size() > 100)
            errors.add("INVALID_GRAPH", null, null, "graph", "流程格式或节点连线数量超限");
        Map<String, Node> nodes = new HashMap<>();
        List<Node> normalizedNodes = new ArrayList<>();
        List<Node> starts = new ArrayList<>();
        for (int index = 0; index < graph.nodes().size(); index++) {
            Node node = graph.nodes().get(index);
            String field = "graph.nodes[" + index + "]";
            if (!key(node.nodeKey()) || nodes.putIfAbsent(node.nodeKey(), node) != null)
                errors.add("INVALID_NODE_KEY", node.nodeKey(), null, field + ".nodeKey", "节点键重复或格式无效");
            String nodeName = text(node.name(), 100, field + ".name", node.nodeKey(), null, errors);
            String message = node.reasonMessage() == null ? null
                : text(node.reasonMessage(), 500, field + ".reasonMessage", node.nodeKey(), null, errors);
            if (Math.abs((long) node.x()) > 100000 || Math.abs((long) node.y()) > 100000)
                errors.add("INVALID_POSITION", node.nodeKey(), null, field + ".position", "节点坐标超出画布范围");
            switch (node.type()) {
                case "START" -> {
                    starts.add(node);
                    if (node.ruleSetVersionId() != null || node.decisionCode() != null || node.reasonCode() != null || message != null)
                        errors.add("INVALID_NODE_CONFIG", node.nodeKey(), null, field, "开始节点不能配置规则或决定");
                }
                case "RULE_SET" -> {
                    if (node.ruleSetVersionId() == null || node.ruleSetVersionId() <= 0)
                        errors.add("RULE_SET_REQUIRED", node.nodeKey(), null, field + ".ruleSetVersionId", "请选择准确规则版本");
                    if (node.decisionCode() != null || node.reasonCode() != null || message != null)
                        errors.add("INVALID_NODE_CONFIG", node.nodeKey(), null, field, "规则集节点不配置结束决定");
                }
                case "END" -> {
                    if (node.decisionCode() == null || !Set.of("ALLOW", "REVIEW", "DENY").contains(node.decisionCode()))
                        errors.add("END_DECISION_REQUIRED", node.nodeKey(), null, field + ".decisionCode", "请选择结束决定");
                    if (node.ruleSetVersionId() != null)
                        errors.add("INVALID_NODE_CONFIG", node.nodeKey(), null, field, "结束节点不能引用规则版本");
                    if ("DENY".equals(node.decisionCode())) {
                        if (!code(node.reasonCode()) || message == null)
                            errors.add("REASON_REQUIRED", node.nodeKey(), null, field + ".reasonCode", "拒绝必须配置原因和文案");
                    } else if (node.reasonCode() != null || message != null)
                        errors.add("INVALID_NODE_CONFIG", node.nodeKey(), null, field, "非拒绝结束节点不能保存拒绝原因");
                }
                default -> errors.add("INVALID_NODE_TYPE", node.nodeKey(), null, field + ".type", "节点类型不支持");
            }
            normalizedNodes.add(new Node(node.nodeKey(), node.type(), nodeName, node.x(), node.y(),
                node.ruleSetVersionId(), node.decisionCode(), node.reasonCode(), message));
        }
        if (starts.size() != 1) errors.add("MISSING_START", null, null, "graph.nodes", "流程必须恰有一个开始节点");
        if (nodes.values().stream().noneMatch(n -> "END".equals(n.type())))
            errors.add("MISSING_END", null, null, "graph.nodes", "请添加结束节点");
        var outgoing = new HashMap<String, List<Edge>>();
        var incoming = new HashMap<String, List<Edge>>();
        var edgeKeys = new HashSet<String>();
        var normalizedEdges = new ArrayList<Edge>();
        boolean dangling = false;
        for (int index = 0; index < graph.edges().size(); index++) {
            Edge edge = graph.edges().get(index);
            String field = "graph.edges[" + index + "]";
            if (!key(edge.edgeKey()) || !edgeKeys.add(edge.edgeKey()))
                errors.add("INVALID_EDGE_KEY", null, edge.edgeKey(), field + ".edgeKey", "连线键重复或格式无效");
            String edgeName = text(edge.name(), 100, field + ".name", null, edge.edgeKey(), errors);
            Node source = nodes.get(edge.sourceNodeKey());
            Node target = nodes.get(edge.targetNodeKey());
            if (source == null || target == null) {
                dangling = true;
                errors.add("MISSING_ENDPOINT", null, edge.edgeKey(), field, "连线端点不存在");
            } else {
                outgoing.computeIfAbsent(source.nodeKey(), ignored -> new ArrayList<>()).add(edge);
                incoming.computeIfAbsent(target.nodeKey(), ignored -> new ArrayList<>()).add(edge);
                if (source.nodeKey().equals(target.nodeKey()))
                    errors.add("CYCLE", source.nodeKey(), edge.edgeKey(), field, "连线不能指向自身");
                if ("END".equals(source.type()) || "START".equals(target.type()))
                    errors.add("INVALID_ENDPOINT", source.nodeKey(), edge.edgeKey(), field, "结束不能出线，开始不能入线");
            }
            Condition condition = edge.condition();
            if ("CONDITIONAL".equals(edge.type())) {
                if (edge.priority() < 1 || edge.priority() > 100 || condition == null)
                    errors.add("INVALID_CONDITION", null, edge.edgeKey(), field, "条件出口必须配置条件和正整数顺序");
                else {
                    try {
                        if (condition.schemaVersion() != 1 && condition.schemaVersion() != 2) throw new IllegalArgumentException();
                        condition = new Condition(condition.schemaVersion(), normalize(condition.root(), 1, new int[]{0}, condition.schemaVersion()));
                        if (codec.conditionBytes(condition).length > 8192) throw new IllegalArgumentException();
                    } catch (FailException | IllegalArgumentException | RiskVariableException failure) {
                        errors.add("INVALID_CONDITION", null, edge.edgeKey(), field + ".condition", "条件来源、类型、值域或大小不合法");
                    }
                }
            } else if (!Set.of("UNCONDITIONAL", "ELSE").contains(edge.type()) || edge.priority() != 0 || condition != null)
                errors.add("INVALID_EDGE_CONFIG", null, edge.edgeKey(), field, "非条件连线的顺序须为零且不能携带条件");
            normalizedEdges.add(new Edge(edge.edgeKey(), edge.sourceNodeKey(), edge.targetNodeKey(), edge.type(),
                edge.priority(), edgeName, condition));
        }
        for (Node node : nodes.values().stream().sorted(Comparator.comparing(Node::nodeKey)).toList()) {
            List<Edge> edges = outgoing.getOrDefault(node.nodeKey(), List.of());
            if ("START".equals(node.type())) {
                if (edges.size() != 1 || !"UNCONDITIONAL".equals(edges.getFirst().type()))
                    errors.add("START_EXIT_REQUIRED", node.nodeKey(), null, "graph.edges", "开始节点需要一条无条件出口");
            } else if ("RULE_SET".equals(node.type())) {
                if (edges.size() == 1 && "UNCONDITIONAL".equals(edges.getFirst().type())) continue;
                List<Integer> priorities = edges.stream().filter(e -> "CONDITIONAL".equals(e.type()))
                    .map(Edge::priority).sorted().toList();
                long fallback = edges.stream().filter(e -> "ELSE".equals(e.type())).count();
                if (priorities.isEmpty() || edges.size() != priorities.size() + fallback)
                    errors.add("INVALID_EXITS", node.nodeKey(), null, "graph.edges", "请选择直连或条件加其他情况出口");
                if (fallback != 1) errors.add("ELSE_REQUIRED", node.nodeKey(), null, "graph.edges", "条件分流必须有一个其他情况出口");
                for (int i = 0; i < priorities.size(); i++) if (priorities.get(i) != i + 1) {
                    errors.add("DUPLICATE_PRIORITY", node.nodeKey(), null, "graph.edges", "条件顺序必须从一连续排列且不能重复");
                    break;
                }
            }
        }
        // Kahn 检查整个图（包括独立不可达环），不会因画布顺序改变结果。
        var degree = new HashMap<String, Integer>();
        nodes.keySet().forEach(k -> degree.put(k, incoming.getOrDefault(k, List.of()).size()));
        var ready = new ArrayDeque<String>();
        degree.forEach((k,v) -> { if (v == 0) ready.add(k); });
        int visited = 0;
        while (!ready.isEmpty()) {
            String next = ready.remove(); visited++;
            for (Edge edge : outgoing.getOrDefault(next, List.of()))
                if (degree.compute(edge.targetNodeKey(), (k,v) -> v - 1) == 0) ready.add(edge.targetNodeKey());
        }
        if (visited != nodes.size()) errors.add("CYCLE", null, null, "graph.edges", "流程不能包含循环");
        if (starts.size() == 1 && !dangling) {
            Set<String> reached = reachable(List.of(starts.getFirst().nodeKey()), outgoing, false);
            nodes.keySet().stream().sorted().filter(k -> !reached.contains(k)).forEach(k ->
                errors.add("UNREACHABLE_NODE", k, null, "graph.nodes", "节点无法从开始到达"));
            // 断出口已有直接问题，不再重复报告其所有上游无法到达结束。
            boolean exitsComplete = nodes.values().stream().allMatch(n -> "END".equals(n.type())
                || !outgoing.getOrDefault(n.nodeKey(), List.of()).isEmpty());
            if (exitsComplete && visited == nodes.size()) {
                var ends = nodes.values().stream().filter(n -> "END".equals(n.type())).map(Node::nodeKey).toList();
                Set<String> finishes = reachable(ends, incoming, true);
                nodes.keySet().stream().sorted().filter(k -> !finishes.contains(k)).forEach(k ->
                    errors.add("NO_END_PATH", k, null, "graph.nodes", "节点无法到达结束"));
            }
        }
        normalizedNodes.sort(Comparator.comparing(Node::nodeKey));
        normalizedEdges.sort(Comparator.comparing(Edge::sourceNodeKey).thenComparingInt(Edge::priority).thenComparing(Edge::edgeKey));
        return new Checked(new Save(command.pointCode(), command.flowCode(), name, command.baseFlowVersionId(),
            command.expectedVersion(), reason, new Graph(1, normalizedNodes, normalizedEdges)), errors.report());
    }

    private Expression normalize(Expression expression, int depth, int[] count, int schemaVersion) {
        if (expression == null || depth > 8 || ++count[0] > 32) throw new IllegalArgumentException();
        if (expression instanceof Group group) {
            boolean not = "NOT".equals(group.operator());
            if ((!not && !Set.of("AND", "OR").contains(group.operator()))
                || (not ? group.children().size() != 1 : group.children().size() < 2)) throw new IllegalArgumentException();
            return new Group(group.operator(), group.children().stream().map(e -> normalize(e, depth + 1, count, schemaVersion)).toList());
        }
        Predicate predicate = (Predicate) expression;
        if (schemaVersion == 2) {
            if (predicate.sourceType() != null || predicate.fieldCode() != null || predicate.values() != null
                || predicate.operands() == null || predicate.operands().isEmpty() || predicate.operands().size() > 101)
                throw new IllegalArgumentException();
            ComparisonOperator.valueOf(predicate.operator());
            List<VariableOperand> operands = predicate.operands().stream().map(o -> {
                VariableOperandJson.validateShape(o);
                var literal = o.sourceType() == OperandSource.VARIABLE ? NullLiteral.INSTANCE
                    : VariableConditionValidator.literal(VariableValueNormalizer.normalize(o.valueType(),
                        o.valueType() == DecisionValueType.STRING ? VariableFormat.TEXT : VariableFormat.NONE,
                        VariableConditionValidator.value(o.literalValue())));
                return new VariableOperand(o.operandRole(), o.valueSequence(), o.sourceType(), o.variableCode(), o.fieldCode(),
                    o.valueType(), literal, o.numericUnit(), o.currencyCode(), o.currencyVariableCode());
            }).sorted(Comparator.comparing(VariableOperand::operandRole).thenComparingInt(VariableOperand::valueSequence)).toList();
            for (OperandRole role : OperandRole.values()) {
                var selected = operands.stream().filter(o -> o.operandRole() == role).toList();
                for (int index = 0; index < selected.size(); index++) if (selected.get(index).valueSequence() != index + 1) throw new IllegalArgumentException();
            }
            VariableOperandJson.validatePredicate(ComparisonOperator.valueOf(predicate.operator()), operands);
            return new Predicate(null, null, predicate.operator(), null, operands);
        }
        if (predicate.operands() != null || predicate.values() == null) throw new IllegalArgumentException();
        if ("NODE_RESULT".equals(predicate.sourceType())) {
            if (!"matched".equals(predicate.fieldCode()) || !Set.of("EQ", "NE").contains(predicate.operator())
                || predicate.values().size() != 1 || !(predicate.values().getFirst() instanceof BooleanLiteral))
                throw new IllegalArgumentException();
            return predicate;
        }
        if (!"CONTEXT".equals(predicate.sourceType())) throw new IllegalArgumentException();
        var descriptor = CheckoutPrepareAuthoringDescriptor.v1().fields().stream()
            .filter(f -> f.fieldCode().equals(predicate.fieldCode())).findFirst().orElseThrow(IllegalArgumentException::new);
        ComparisonOperator operator = java.util.Arrays.stream(ComparisonOperator.values())
            .filter(v -> v.getCode().equals(predicate.operator())).findFirst().orElseThrow(IllegalArgumentException::new);
        var operands = new ArrayList<OperandDraft>();
        operands.add(new OperandDraft(OperandRole.LEFT, 1, OperandSource.CONTEXT, predicate.fieldCode(),
            descriptor.valueType(), NullLiteral.INSTANCE));
        for (int i = 0; i < predicate.values().size(); i++)
            operands.add(new OperandDraft(OperandRole.RIGHT, i + 1, OperandSource.LITERAL, null,
                descriptor.valueType(), predicate.values().get(i)));
        var normalized = (NormalizedPredicate) ruleValidator.validateCondition(List.of(new NodeDraft("predicate", null,
            1, NodeType.PREDICATE, null, operator, operands)));
        return new Predicate(predicate.sourceType(), predicate.fieldCode(), predicate.operator(), normalized.operands()
            .stream().filter(o -> o.operandRole() == OperandRole.RIGHT).map(NormalizedOperand::literalValue).toList());
    }

    private static Set<String> reachable(List<String> seeds, Map<String, List<Edge>> adjacency, boolean reverse) {
        var found = new HashSet<String>(); var pending = new ArrayDeque<>(seeds);
        while (!pending.isEmpty()) {
            String key = pending.remove();
            if (found.add(key)) for (Edge edge : adjacency.getOrDefault(key, List.of()))
                pending.add(reverse ? edge.sourceNodeKey() : edge.targetNodeKey());
        }
        return found;
    }

    private static boolean key(String value) { return value != null && value.matches("[A-Za-z][A-Za-z0-9_-]{0,63}"); }
    private static boolean code(String value) { return value != null && value.matches("[A-Z][A-Z0-9_]{0,63}"); }
    private static String text(String value, int max, String field, String node, String edge, Errors errors) {
        try {
            if (value == null || !value.equals(value.strip()) || value.codePointCount(0, value.length()) > max)
                throw new IllegalArgumentException();
            return ChangeReasonPolicy.normalize(value);
        } catch (FailException | IllegalArgumentException | RiskVariableException failure) {
            errors.add("INVALID_TEXT", node, edge, field, "文本不能为空、首尾不能有空白且须满足长度和内容约束");
            return value;
        }
    }

    private static final class Errors {
        private final List<Issue> issues = new ArrayList<>();
        private boolean truncated;
        void add(String code, String node, String edge, String field, String message) {
            if (issues.size() < 100) issues.add(new Issue(code, node, edge, field, message)); else truncated = true;
        }
        Validation report() { return new Validation(issues.isEmpty(), issues, truncated); }
    }
}
