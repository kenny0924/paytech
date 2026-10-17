package com.paytech.risk.decision.domain.flow;

import java.util.*;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor.OutputDescriptor;
import com.paytech.risk.decision.domain.flow.DecisionFlow.*;
import com.paytech.risk.decision.domain.rule.ChangeReasonPolicy;
import com.paytech.risk.decision.domain.rule.RuleAstValidator;
import com.paytech.risk.decision.domainservice.service.RiskVariableException;

/**
 * 决策流保存命令的静态校验：仅检查有界 DAG 和条件配置，绝不求值或推断条件覆盖关系
 * <li>问题按 基本信息 → 节点 → 连线 → 出口 → 环 → 可达性 的固定顺序收集，最多保留 100 项。</li>
 * <li>返回规范化命令：文本与条件已规范化，放行结束节点补齐附加输出默认值，节点按键排序，连线按源节点、顺序、键排序。</li>
 * <li>任何字段为空都以校验问题报告，不抛空指针。</li>
 * <li>条件出口的结构与值域校验见 {@link DecisionFlowConditionNormalizer}。</li>
 */
@Component
public final class DecisionFlowValidatorImpl implements DecisionFlowValidator {
    private static final String START = "START";
    private static final String RULE_SET = "RULE_SET";
    private static final String END = "END";

    private static final String UNCONDITIONAL = "UNCONDITIONAL";
    private static final String CONDITIONAL = "CONDITIONAL";
    private static final String ELSE = "ELSE";

    private static final String DENY = "DENY";
    private static final String ALLOW = "ALLOW";

    private static final Set<String> END_DECISIONS = Set.of(ALLOW, "REVIEW", DENY);

    private static final Set<String> DIRECT_EDGE_TYPES = Set.of(UNCONDITIONAL, ELSE);

    private static final int MAX_NODES = 50;
    private static final int MAX_EDGES = 100;
    private static final int MAX_COORDINATE = 100000;
    private static final int MAX_PRIORITY = 100;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_LONG_TEXT_LENGTH = 500;
    private static final int MAX_ISSUES = 100;

    private final DecisionFlowConditionNormalizer conditions;

    /** 条件规范化依赖规则 AST 校验与条件编码。 */
    public DecisionFlowValidatorImpl(RuleAstValidator ruleValidator, DecisionFlowJsonCodec codec) {
        this.conditions = new DecisionFlowConditionNormalizer(ruleValidator, codec);
    }

    /** 缺少图时只报告一个问题并原样返回；否则执行完整校验。 */
    @Override
    public Checked check(Save command) {
        if (command == null || command.graph() == null) {
            Errors errors = new Errors();
            errors.add("INVALID_GRAPH", null, null, "graph", "请配置完整流程");
            return new Checked(command, errors.report());
        }
        return new Check(command).run();
    }

    /** 一次校验的工作状态：错误、按键索引的节点、出入边与规范化结果。 */
    private final class Check {
        private final Save command;
        private final Graph graph;
        private final Errors errors = new Errors();

        /** 键合法且首次出现的节点；重复键保留第一个。 */
        private final Map<String, Node> nodes = new HashMap<>();
        private final List<Node> starts = new ArrayList<>();
        private final List<Node> normalizedNodes = new ArrayList<>();

        /** 只登记两端都存在的连线。 */
        private final Map<String, List<Edge>> outgoing = new HashMap<>();
        private final Map<String, List<Edge>> incoming = new HashMap<>();
        private final Set<String> edgeKeys = new HashSet<>();
        private final List<Edge> normalizedEdges = new ArrayList<>();

        /** 存在端点缺失的连线时不做可达性分析，避免重复报告。 */
        private boolean dangling;

        private String flowName;
        private String changeReason;

        /** 绑定保存命令。 */
        Check(Save command) {
            this.command = command;
            this.graph = command.graph();
        }

        /** 按固定顺序执行全部检查并组装规范化命令。 */
        Checked run() {
            checkBasics();
            checkNodes();
            checkEdges();
            checkExits();
            boolean acyclic = checkAcyclic();
            checkReachability(acyclic);
            return normalized();
        }

        // ---------------------------------------------------------------- 基本信息

        /** 决策点、流程编码、编辑基准、名称、变更原因与图规模。 */
        void checkBasics() {
            if (DecisionPointAuthoringDescriptor.find(command.pointCode()).isEmpty()) {
                errors.add("INVALID_POINT", null, null, "pointCode", "当前决策点不支持流程配置");
            }
            if (!isCode(command.flowCode())) {
                errors.add("INVALID_CODE", null, null, "flowCode", "流程编码格式无效");
            }
            if ((command.baseFlowVersionId() == null) != (command.expectedVersion() == null)) {
                errors.add("BASE_CONFLICT", null, null, "baseFlowVersionId", "编辑基准必须同时提供");
            }
            flowName = text(command.flowName(), MAX_NAME_LENGTH, "flowName", null, null);
            changeReason = text(command.changeReason(), MAX_LONG_TEXT_LENGTH, "changeReason", null, null);
            if (graph.schemaVersion() != 1 || graph.nodes().size() > MAX_NODES || graph.edges().size() > MAX_EDGES) {
                errors.add("INVALID_GRAPH", null, null, "graph", "流程格式或节点连线数量超限");
            }
        }

        // ---------------------------------------------------------------- 节点

        /** 逐个校验节点，再要求恰有一个开始节点且至少一个结束节点。 */
        void checkNodes() {
            for (int index = 0; index < graph.nodes().size(); index++) {
                normalizedNodes.add(checkNode(graph.nodes().get(index), "graph.nodes[" + index + "]"));
            }
            if (starts.size() != 1) {
                errors.add("MISSING_START", null, null, "graph.nodes", "流程必须恰有一个开始节点");
            }
            if (nodes.values().stream().noneMatch(node -> END.equals(node.type()))) {
                errors.add("MISSING_END", null, null, "graph.nodes", "请添加结束节点");
            }
        }

        /** 校验单个节点并返回文本规范化后的节点。 */
        Node checkNode(Node node, String field) {
            registerNodeKey(node, field);
            String name = text(node.name(), MAX_NAME_LENGTH, field + ".name", node.nodeKey(), null);
            String message = node.reasonMessage() == null ? null
                : text(node.reasonMessage(), MAX_LONG_TEXT_LENGTH, field + ".reasonMessage", node.nodeKey(), null);
            if (Math.abs((long)node.x()) > MAX_COORDINATE || Math.abs((long)node.y()) > MAX_COORDINATE) {
                errors.add("INVALID_POSITION", node.nodeKey(), null, field + ".position", "节点坐标超出画布范围");
            }
            switch (node.type() == null ? "" : node.type()) {
                case START -> checkStartNode(node, field, message);
                case RULE_SET -> checkRuleSetNode(node, field, message);
                case END -> checkEndNode(node, field, message);
                default -> errors.add("INVALID_NODE_TYPE", node.nodeKey(), null, field + ".type", "节点类型不支持");
            }
            if (!END.equals(node.type()) && !node.outputs().isEmpty()) {
                errors.add("INVALID_NODE_CONFIG", node.nodeKey(), null, field + ".outputs", "只有结束节点可以配置附加输出");
            }
            return new Node(node.nodeKey(), node.type(), name, node.x(), node.y(), node.ruleSetVersionId(),
                node.decisionCode(), node.reasonCode(), message, outputsWithDefaults(node));
        }

        /**
         * 放行结束节点补齐决策点声明的附加输出：未配置的取合同默认值
         * <li>默认值在保存时写入流程版本，随流程图进入执行发布摘要；运行期只读结束节点上的值。</li>
         *
         * @return 补齐后的输出；非放行结束节点或决策点无声明时原样返回
         */
        Map<String, String> outputsWithDefaults(Node node) {
            if (!END.equals(node.type()) || !OutputDescriptor.END_DECISION.getCode().equals(node.decisionCode())) {
                return node.outputs();
            }
            DecisionPointAuthoringDescriptor descriptor = DecisionPointAuthoringDescriptor.find(command.pointCode())
                .orElse(null);
            if (descriptor == null || descriptor.outputs().isEmpty()) {
                return node.outputs();
            }
            Map<String, String> outputs = new HashMap<>(node.outputs());
            descriptor.outputs().forEach(output -> outputs.putIfAbsent(output.outputCode(), output.defaultValue()));
            return outputs;
        }

        /** 节点键格式合法且未出现过才登记；格式无效的节点不进入索引。 */
        void registerNodeKey(Node node, String field) {
            if (!isKey(node.nodeKey()) || nodes.putIfAbsent(node.nodeKey(), node) != null) {
                errors.add("INVALID_NODE_KEY", node.nodeKey(), null, field + ".nodeKey", "节点键重复或格式无效");
            }
        }

        /** 开始节点不引用规则、不配置决定与原因。 */
        void checkStartNode(Node node, String field, String message) {
            starts.add(node);
            if (node.ruleSetVersionId() != null || node.decisionCode() != null || node.reasonCode() != null
                || message != null) {
                errors.add("INVALID_NODE_CONFIG", node.nodeKey(), null, field, "开始节点不能配置规则或决定");
            }
        }

        /** 规则集节点必须引用准确规则版本，且不配置决定与原因。 */
        void checkRuleSetNode(Node node, String field, String message) {
            if (node.ruleSetVersionId() == null || node.ruleSetVersionId() <= 0) {
                errors.add("RULE_SET_REQUIRED", node.nodeKey(), null, field + ".ruleSetVersionId", "请选择准确规则版本");
            }
            if (node.decisionCode() != null || node.reasonCode() != null || message != null) {
                errors.add("INVALID_NODE_CONFIG", node.nodeKey(), null, field, "规则集节点不配置结束决定");
            }
        }

        /** 结束节点：决定必选、不引用规则、拒绝原因与附加输出。 */
        void checkEndNode(Node node, String field, String message) {
            if (node.decisionCode() == null || !END_DECISIONS.contains(node.decisionCode())) {
                errors.add("END_DECISION_REQUIRED", node.nodeKey(), null, field + ".decisionCode", "请选择结束决定");
            }
            if (node.ruleSetVersionId() != null) {
                errors.add("INVALID_NODE_CONFIG", node.nodeKey(), null, field, "结束节点不能引用规则版本");
            }
            checkEndReason(node, field, message);
            checkEndOutputs(node, field);
        }

        /** 拒绝必须配置原因码与文案；其他决定不能保存拒绝原因。 */
        void checkEndReason(Node node, String field, String message) {
            if (DENY.equals(node.decisionCode())) {
                if (!isCode(node.reasonCode()) || message == null) {
                    errors.add("REASON_REQUIRED", node.nodeKey(), null, field + ".reasonCode", "拒绝必须配置原因和文案");
                }
                return;
            }
            if (node.reasonCode() != null || message != null) {
                errors.add("INVALID_NODE_CONFIG", node.nodeKey(), null, field, "非拒绝结束节点不能保存拒绝原因");
            }
        }

        /** 附加输出只允许放行结束节点配置，编码与取值必须是该决策点合同声明的值。 */
        void checkEndOutputs(Node node, String field) {
            if (node.outputs().isEmpty()) {
                return;
            }
            if (!OutputDescriptor.END_DECISION.getCode().equals(node.decisionCode())) {
                errors.add("INVALID_NODE_CONFIG", node.nodeKey(), null, field + ".outputs", "只有放行结束节点可以配置附加输出");
                return;
            }
            DecisionPointAuthoringDescriptor descriptor =
                DecisionPointAuthoringDescriptor.find(command.pointCode()).orElse(null);
            for (Map.Entry<String, String> entry : node.outputs().entrySet()) {
                OutputDescriptor declared = descriptor == null ? null : descriptor.output(entry.getKey()).orElse(null);
                if (declared == null || !declared.allowedValues().contains(entry.getValue())) {
                    errors.add("INVALID_OUTPUT", node.nodeKey(), null, field + ".outputs." + entry.getKey(),
                        "附加输出未声明或取值不允许");
                }
            }
        }

        // ---------------------------------------------------------------- 连线

        /** 逐条校验连线。 */
        void checkEdges() {
            for (int index = 0; index < graph.edges().size(); index++) {
                normalizedEdges.add(checkEdge(graph.edges().get(index), "graph.edges[" + index + "]"));
            }
        }

        /** 校验单条连线并返回名称与条件规范化后的连线。 */
        Edge checkEdge(Edge edge, String field) {
            if (!isKey(edge.edgeKey()) || !edgeKeys.add(edge.edgeKey())) {
                errors.add("INVALID_EDGE_KEY", null, edge.edgeKey(), field + ".edgeKey", "连线键重复或格式无效");
            }
            String name = text(edge.name(), MAX_NAME_LENGTH, field + ".name", null, edge.edgeKey());
            linkEndpoints(edge, field);
            Condition condition =
                CONDITIONAL.equals(edge.type()) ? checkConditionalEdge(edge, field) : checkDirectEdge(edge, field);
            return new Edge(edge.edgeKey(), edge.sourceNodeKey(), edge.targetNodeKey(), edge.type(), edge.priority(),
                name, condition);
        }

        /** 两端都存在时登记出入边，并拒绝自环、结束出线与开始入线。 */
        void linkEndpoints(Edge edge, String field) {
            Node source = nodes.get(edge.sourceNodeKey());
            Node target = nodes.get(edge.targetNodeKey());
            if (source == null || target == null) {
                dangling = true;
                errors.add("MISSING_ENDPOINT", null, edge.edgeKey(), field, "连线端点不存在");
                return;
            }
            outgoing.computeIfAbsent(source.nodeKey(), ignored -> new ArrayList<>()).add(edge);
            incoming.computeIfAbsent(target.nodeKey(), ignored -> new ArrayList<>()).add(edge);
            if (source.nodeKey().equals(target.nodeKey())) {
                errors.add("CYCLE", source.nodeKey(), edge.edgeKey(), field, "连线不能指向自身");
            }
            if (END.equals(source.type()) || START.equals(target.type())) {
                errors.add("INVALID_ENDPOINT", source.nodeKey(), edge.edgeKey(), field, "结束不能出线，开始不能入线");
            }
        }

        /**
         * 条件连线：顺序 1～100 且必须有条件
         *
         * @return 规范化成功的条件；结构不合法时为原条件，超出大小时为已规范化的条件
         */
        Condition checkConditionalEdge(Edge edge, String field) {
            Condition condition = edge.condition();
            if (edge.priority() < 1 || edge.priority() > MAX_PRIORITY || condition == null) {
                errors.add("INVALID_CONDITION", null, edge.edgeKey(), field, "条件出口必须配置条件和正整数顺序");
                return condition;
            }
            try {
                condition = conditions.normalize(condition);
                conditions.requireWithinSize(condition);
            } catch (FailException | IllegalArgumentException | RiskVariableException failure) {
                errors.add("INVALID_CONDITION", null, edge.edgeKey(), field + ".condition", "条件来源、类型、值域或大小不合法");
            }
            return condition;
        }

        /** 无条件与其他情况连线：顺序为零且不带条件。 */
        Condition checkDirectEdge(Edge edge, String field) {
            if (edge.type() == null || !DIRECT_EDGE_TYPES.contains(edge.type()) || edge.priority() != 0
                || edge.condition() != null) {
                errors.add("INVALID_EDGE_CONFIG", null, edge.edgeKey(), field, "非条件连线的顺序须为零且不能携带条件");
            }
            return edge.condition();
        }

        // ---------------------------------------------------------------- 出口

        /** 按节点键顺序检查开始与规则集节点的出口组合。 */
        void checkExits() {
            for (Node node : nodes.values().stream().sorted(Comparator.comparing(Node::nodeKey)).toList()) {
                List<Edge> exits = outgoing.getOrDefault(node.nodeKey(), List.of());
                if (START.equals(node.type())) {
                    checkStartExit(node, exits);
                } else if (RULE_SET.equals(node.type())) {
                    checkRuleSetExits(node, exits);
                }
            }
        }

        /** 开始节点恰有一条无条件出口。 */
        void checkStartExit(Node node, List<Edge> exits) {
            if (exits.size() != 1 || !UNCONDITIONAL.equals(exits.getFirst().type())) {
                errors.add("START_EXIT_REQUIRED", node.nodeKey(), null, "graph.edges", "开始节点需要一条无条件出口");
            }
        }

        /** 规则集节点：一条无条件出口，或若干条件出口加恰好一个其他情况出口，条件顺序从一连续。 */
        void checkRuleSetExits(Node node, List<Edge> exits) {
            if (exits.size() == 1 && UNCONDITIONAL.equals(exits.getFirst().type())) {
                return;
            }
            List<Integer> priorities =
                exits.stream().filter(edge -> CONDITIONAL.equals(edge.type())).map(Edge::priority).sorted().toList();
            long fallbacks = exits.stream().filter(edge -> ELSE.equals(edge.type())).count();
            if (priorities.isEmpty() || exits.size() != priorities.size() + fallbacks) {
                errors.add("INVALID_EXITS", node.nodeKey(), null, "graph.edges", "请选择直连或条件加其他情况出口");
            }
            if (fallbacks != 1) {
                errors.add("ELSE_REQUIRED", node.nodeKey(), null, "graph.edges", "条件分流必须有一个其他情况出口");
            }
            if (!consecutiveFromOne(priorities)) {
                errors.add("DUPLICATE_PRIORITY", node.nodeKey(), null, "graph.edges", "条件顺序必须从一连续排列且不能重复");
            }
        }

        // ---------------------------------------------------------------- 环与可达性

        /**
         * Kahn 拓扑排序检查整张图（包括不可达的独立环），结果与画布顺序无关
         *
         * @return 无环时为 true
         */
        boolean checkAcyclic() {
            Map<String, Integer> degree = new HashMap<>();
            nodes.keySet().forEach(key -> degree.put(key, incoming.getOrDefault(key, List.of()).size()));
            ArrayDeque<String> ready = new ArrayDeque<>();
            degree.forEach((key, value) -> {
                if (value == 0) {
                    ready.add(key);
                }
            });
            int visited = 0;
            while (!ready.isEmpty()) {
                String next = ready.remove();
                visited++;
                for (Edge edge : outgoing.getOrDefault(next, List.of())) {
                    if (degree.compute(edge.targetNodeKey(), (key, value) -> value - 1) == 0) {
                        ready.add(edge.targetNodeKey());
                    }
                }
            }
            boolean acyclic = visited == nodes.size();
            if (!acyclic) {
                errors.add("CYCLE", null, null, "graph.edges", "流程不能包含循环");
            }
            return acyclic;
        }

        /**
         * 恰有一个开始节点且没有悬空连线时，报告从开始不可达、以及无法到达结束的节点
         * <li>有断出口或环时已有直接问题，不再重复报告其上游无法到达结束。</li>
         */
        void checkReachability(boolean acyclic) {
            if (starts.size() != 1 || dangling) {
                return;
            }
            Set<String> reached = reachable(List.of(starts.getFirst().nodeKey()), outgoing, false);
            nodes.keySet().stream().sorted().filter(key -> !reached.contains(key))
                .forEach(key -> errors.add("UNREACHABLE_NODE", key, null, "graph.nodes", "节点无法从开始到达"));
            if (!exitsComplete() || !acyclic) {
                return;
            }
            List<String> ends =
                nodes.values().stream().filter(node -> END.equals(node.type())).map(Node::nodeKey).toList();
            Set<String> finishes = reachable(ends, incoming, true);
            nodes.keySet().stream().sorted().filter(key -> !finishes.contains(key))
                .forEach(key -> errors.add("NO_END_PATH", key, null, "graph.nodes", "节点无法到达结束"));
        }

        /** 除结束节点外，每个节点都至少有一条出线。 */
        boolean exitsComplete() {
            return nodes.values().stream().allMatch(
                node -> END.equals(node.type()) || !outgoing.getOrDefault(node.nodeKey(), List.of()).isEmpty());
        }

        // ---------------------------------------------------------------- 结果

        /** 规范化命令：节点按键、连线按源节点/顺序/键排序，图格式版本固定为 1。 */
        Checked normalized() {
            Comparator<String> keys = Comparator.nullsFirst(Comparator.naturalOrder());
            normalizedNodes.sort(Comparator.comparing(Node::nodeKey, keys));
            normalizedEdges.sort(Comparator.comparing(Edge::sourceNodeKey, keys).thenComparingInt(Edge::priority)
                .thenComparing(Edge::edgeKey, keys));
            Save normalized = new Save(command.pointCode(), command.flowCode(), flowName, command.baseFlowVersionId(),
                command.expectedVersion(), changeReason, new Graph(1, normalizedNodes, normalizedEdges));
            return new Checked(normalized, errors.report());
        }

        /**
         * 文本校验与规范化
         * <li>不能为空、首尾不能有空白、不超过字符上限，并满足变更原因内容约束。</li>
         *
         * @return 规范化文本；不合法时记录问题并返回原值
         */
        String text(String value, int max, String field, String nodeKey, String edgeKey) {
            try {
                if (value == null || !value.equals(value.strip()) || value.codePointCount(0, value.length()) > max) {
                    throw new IllegalArgumentException();
                }
                return ChangeReasonPolicy.normalize(value);
            } catch (FailException | IllegalArgumentException | RiskVariableException failure) {
                errors.add("INVALID_TEXT", nodeKey, edgeKey, field, "文本不能为空、首尾不能有空白且须满足长度和内容约束");
                return value;
            }
        }
    }

    /** 从种子出发沿出边（或反向沿入边）可达的节点键，含种子本身。 */
    private static Set<String> reachable(List<String> seeds, Map<String, List<Edge>> adjacency, boolean reverse) {
        Set<String> found = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>(seeds);
        while (!pending.isEmpty()) {
            String key = pending.remove();
            if (!found.add(key)) {
                continue;
            }
            for (Edge edge : adjacency.getOrDefault(key, List.of())) {
                pending.add(reverse ? edge.sourceNodeKey() : edge.targetNodeKey());
            }
        }
        return found;
    }

    /** 已排序的条件顺序必须为 1、2、3……，不重复不跳号。 */
    private static boolean consecutiveFromOne(List<Integer> priorities) {
        for (int index = 0; index < priorities.size(); index++) {
            if (priorities.get(index) != index + 1) {
                return false;
            }
        }
        return true;
    }

    /** 节点键、连线键：字母开头，字母、数字、下划线或连字符，最长 64。 */
    private static boolean isKey(String value) {
        return value != null && value.matches("[A-Za-z][A-Za-z0-9_-]{0,63}");
    }

    /** 编码：大写字母开头，大写字母、数字或下划线，最长 64。 */
    private static boolean isCode(String value) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{0,63}");
    }

    /** 问题收集；超过上限后丢弃并标记截断。 */
    private static final class Errors {
        private final List<Issue> issues = new ArrayList<>();
        private boolean truncated;

        /** 追加一个问题。 */
        void add(String code, String nodeKey, String edgeKey, String field, String message) {
            if (issues.size() < MAX_ISSUES) {
                issues.add(new Issue(code, nodeKey, edgeKey, field, message));
            } else {
                truncated = true;
            }
        }

        /** 形成校验报告。 */
        Validation report() {
            return new Validation(issues.isEmpty(), issues, truncated);
        }
    }
}
