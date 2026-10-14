package com.paytech.risk.decision.domain.rule;

import java.time.Instant;
import java.util.*;

import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.ActionKey;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.PersistedActionVersion;
import com.paytech.risk.decision.domain.authoring.RuleActionConfigurationCatalog;
import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.DecisionPointVersionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.RuleSetVersionProjection;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.*;
import com.paytech.risk.decision.domain.variable.VariableOperand;
import com.paytech.risk.decision.domain.variable.VariableOperandJson;
import com.paytech.risk.decision.domain.variable.VariableRuleConfiguration;

/**
 * 一个规则版本的不可变内容与共享输入定义；companion 仅为已有旧编排的可选身份元数据。
 *
 * <p>该类型只接受能被确定性投影的树和引用形态。Repository 发现的重复、孤儿或顺序漂移不会在这里被
 * 静默修复。</p>
 */
public record DecisionRuleSetAggregate(
    RuleSetVersionView version,
    DecisionPointView point,
    PointVersionView companion,
    List<InputFieldView> inputFields,
    List<RuleView> rules,
    RuleSetVersionProjection ruleSetProjection,
    DecisionPointVersionProjection pointVersionProjection,
    List<PersistedActionVersion> persistedActions
) {

    private static final Comparator<NodeView> NODE_ORDER = Comparator.comparingInt(NodeView::sequenceNo)
        .thenComparingLong(NodeView::nodeId);

    private static final Comparator<OperandView> OPERAND_ORDER = Comparator
        .comparingInt((OperandView value) -> value.role() == OperandRole.LEFT ? 0 : 1)
        .thenComparingInt(OperandView::valueSequence)
        .thenComparingLong(OperandView::operandId);

    public DecisionRuleSetAggregate {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(ruleSetProjection, "ruleSetProjection");
        inputFields = immutable("inputFields", inputFields);
        rules = immutable("rules", rules);
        persistedActions = immutable("persistedActions", persistedActions);

        requirePositive(version.ruleSetVersionId(), "ruleSetVersionId");
        requirePositive(version.decisionPointId(), "decisionPointId");
        if (companion != null) requirePositive(companion.pointVersionId(), "pointVersionId");
        if (version.decisionPointId() != point.decisionPointId()
            || (companion != null && companion.decisionPointId() != point.decisionPointId())) {
            throw invalid("Rule Set 与 Point 坐标不一致");
        }
        if (!version.ruleSetCode().equals(ruleSetProjection.ruleSetCode())
            || version.versionNo() != ruleSetProjection.versionNo()
            || !point.pointCode().equals(ruleSetProjection.pointCode())
            || (pointVersionProjection != null && (companion == null
                || !point.pointCode().equals(pointVersionProjection.pointCode())
                || companion.versionNo() != pointVersionProjection.pointVersionNo()))) {
            throw invalid("Rule Set 规范投影坐标不一致");
        }
        requireHash(version.contentHash(), "ruleSetContentHash");
        if (companion != null) requireHash(companion.contentHash(), "pointVersionContentHash");
        requirePositive(version.versionNo(), "ruleSetVersionNo");
        if (companion != null) requirePositive(companion.versionNo(), "pointVersionNo");
        requirePositive(version.createdBy(), "createdBy");
        Objects.requireNonNull(version.createTime(), "createTime");

        if (companion != null) validateStatusPair(version.status(), companion.status());
        validateFields(inputFields);
        validateRules(rules, persistedActions, ruleSetProjection.contentFormat());
    }

    /** 仅既有顺序编排拥有此兼容坐标。 */
    public Long companionId() {
        return companion == null ? null : companion.pointVersionId();
    }

    private static void validateStatusPair(
        RuleSetVersionStatus ruleSetStatus,
        DecisionPointVersionStatus pointVersionStatus
    ) {
        Objects.requireNonNull(ruleSetStatus, "ruleSetStatus");
        Objects.requireNonNull(pointVersionStatus, "pointVersionStatus");
        boolean valid = switch (ruleSetStatus) {
            case DRAFT -> pointVersionStatus == DecisionPointVersionStatus.DRAFT;
            case FROZEN -> pointVersionStatus == DecisionPointVersionStatus.FROZEN;
            case RETIRED -> pointVersionStatus == DecisionPointVersionStatus.RETIRED;
        };
        if (!valid) {
            throw invalid("Rule Set 与 companion Point Version 状态不一致");
        }
    }

    private static void validateFields(List<InputFieldView> fields) {
        Set<Long> ids = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (InputFieldView field : fields) {
            if (field.inputFieldId() != null) requirePositive(field.inputFieldId(), "inputFieldId");
            if ((field.inputFieldId() != null && !ids.add(field.inputFieldId())) || !codes.add(requireText(field.fieldCode(), "fieldCode"))) {
                throw invalid("Input Field 重复");
            }
            Objects.requireNonNull(field.valueType(), "valueType");
            Objects.requireNonNull(field.dataClassification(), "dataClassification");
        }
    }

    private static void validateRules(List<RuleView> values, List<PersistedActionVersion> actions, String contentFormat) {
        if (values.isEmpty()) {
            throw invalid("Rule Set 不得为空");
        }
        Map<Long, PersistedActionVersion> actionById = new HashMap<>();
        for (PersistedActionVersion action : actions) {
            requirePositive(action.actionVersionId(), "actionVersionId");
            if (actionById.put(action.actionVersionId(), action) != null) {
                throw invalid("Action Version 重复");
            }
        }
        Set<Long> ruleIds = new HashSet<>();
        Set<String> ruleCodes = new HashSet<>();
        Set<Integer> priorities = new HashSet<>();
        Set<Long> globalNodeIds = new HashSet<>();
        Set<Long> globalOperandIds = new HashSet<>();
        Set<Long> ruleActionIds = new HashSet<>();
        for (RuleView rule : values) {
            requirePositive(rule.ruleId(), "ruleId");
            requirePositive(rule.priorityNo(), "priorityNo");
            if (!ruleIds.add(rule.ruleId()) || !ruleCodes.add(requireText(rule.ruleCode(), "ruleCode"))
                || !priorities.add(rule.priorityNo())) {
                throw invalid("Rule 业务坐标重复");
            }
            requireText(rule.ruleName(), "ruleName");
            if (rule.reasonCode() != null) {
                requireText(rule.reasonCode(), "reasonCode");
            }
            if (rule.action() == null && rule.additionalActions().isEmpty() && "LEGACY".equals(contentFormat))
                throw invalid("旧格式规则动作为空");
            int expectedSequence = 1;
            for (ConfiguredActionView extra : rule.additionalActions()) {
                if (extra.binding().sequenceNo() != expectedSequence || extra.configuration().sequenceNo() != expectedSequence)
                    throw invalid("附加动作顺序无效");
                validateAction(extra.binding(), actionById, ruleActionIds);
                if (actionById.get(extra.binding().actionVersionId()).terminalFlag()) throw invalid("附加动作不能是终态");
                expectedSequence++;
            }
            List<AdditionalAction> configured = rule.additionalActions().stream().map(ConfiguredActionView::configuration).toList();
            if (!RuleActionConfigurationCatalog.normalize(configured).equals(configured)) throw invalid("动作配置未规范化");
            if (rule.action() != null) {
                validateAction(rule.action(), actionById, ruleActionIds);
                if (rule.action().sequenceNo() != expectedSequence || !actionById.get(rule.action().actionVersionId()).terminalFlag())
                    throw invalid("终态动作顺序无效");
            }
            validateTree(rule.conditionNodes(), globalNodeIds, globalOperandIds, VariableRuleConfiguration.CONTENT_FORMAT.equals(contentFormat));
        }
    }

    private static void validateAction(
        RuleActionView action,
        Map<Long, PersistedActionVersion> actionById,
        Set<Long> ruleActionIds
    ) {
        Objects.requireNonNull(action, "action");
        requirePositive(action.ruleActionId(), "ruleActionId");
        requirePositive(action.actionVersionId(), "actionVersionId");
        if (!ruleActionIds.add(action.ruleActionId()) || action.sequenceNo() < 1) {
            throw invalid("Rule Action 形态无效");
        }
        PersistedActionVersion persisted = actionById.get(action.actionVersionId());
        if (persisted == null || !persisted.actionCode().equals(action.action().actionCode())
            || persisted.versionNo() != action.action().versionNo()) {
            throw invalid("Rule Action 引用无效");
        }
    }

    private static void validateTree(
        List<NodeView> nodes,
        Set<Long> globalNodeIds,
        Set<Long> globalOperandIds, boolean variableFormat
    ) {
        if (nodes.isEmpty()) {
            throw invalid("Rule 条件树不得为空");
        }
        Map<Long, NodeView> byId = new HashMap<>();
        List<NodeView> roots = new ArrayList<>();
        for (NodeView node : nodes) {
            requirePositive(node.nodeId(), "nodeId");
            requirePositive(node.sequenceNo(), "nodeSequenceNo");
            if (!globalNodeIds.add(node.nodeId()) || byId.put(node.nodeId(), node) != null) {
                throw invalid("Node 标识重复");
            }
            if (node.parentNodeId() == null) {
                roots.add(node);
            }
            validateNodeMatrix(node, globalOperandIds, variableFormat);
        }
        if (roots.size() != 1) {
            throw invalid("Rule 必须恰好包含一个 Root Node");
        }

        Map<Long, List<NodeView>> children = new HashMap<>();
        for (NodeView node : nodes) {
            if (node.parentNodeId() != null) {
                if (node.parentNodeId() == node.nodeId() || !byId.containsKey(node.parentNodeId())) {
                    throw invalid("Node Parent 引用无效");
                }
                children.computeIfAbsent(node.parentNodeId(), ignored -> new ArrayList<>()).add(node);
            }
        }
        for (List<NodeView> siblings : children.values()) {
            siblings.sort(NODE_ORDER);
            requireContiguousNodeSequence(siblings);
        }
        requireContiguousNodeSequence(roots);
        for (NodeView node : nodes) {
            int childCount = children.getOrDefault(node.nodeId(), List.of()).size();
            if (node.nodeType() == NodeType.PREDICATE && childCount != 0) {
                throw invalid("Predicate Node 不得包含子节点");
            }
            if (node.nodeType() == NodeType.GROUP) {
                boolean validArity = node.groupOperator() == GroupOperator.NOT
                    ? childCount == 1
                    : childCount >= 2;
                if (!validArity) {
                    throw invalid("Group Node 子节点数量无效");
                }
            }
        }

        List<NodeView> expectedPreorder = new ArrayList<>();
        appendPreorder(roots.getFirst(), children, new HashSet<>(), expectedPreorder);
        if (expectedPreorder.size() != nodes.size()) {
            throw invalid("Rule 条件树存在环或不可达 Node");
        }
        for (int index = 0; index < nodes.size(); index++) {
            if (nodes.get(index).nodeId() != expectedPreorder.get(index).nodeId()) {
                throw invalid("Rule Node 未按规范树前序排列");
            }
        }
    }

    private static void validateNodeMatrix(NodeView node, Set<Long> globalOperandIds, boolean variableFormat) {
        Objects.requireNonNull(node.nodeType(), "nodeType");
        if (node.nodeType() == NodeType.GROUP) {
            if (node.groupOperator() == null || node.comparisonOperator() != null || !node.operands().isEmpty()) {
                throw invalid("Group Node 字段矩阵无效");
            }
            return;
        }
        if (node.groupOperator() != null || node.comparisonOperator() == null || node.operands().isEmpty()) {
            throw invalid("Predicate Node 字段矩阵无效");
        }
        List<OperandView> sorted = node.operands().stream().sorted(OPERAND_ORDER).toList();
        if (!sorted.equals(node.operands())) {
            throw invalid("Operand 未按规范顺序排列");
        }
        if (variableFormat) {
            VariableOperandJson.validatePredicate(node.comparisonOperator(), node.operands().stream().map(OperandView::variableOperand).toList());
        }
        Map<OperandRole, Integer> nextSequence = new HashMap<>();
        for (OperandView operand : node.operands()) {
            requirePositive(operand.operandId(), "operandId");
            if (!globalOperandIds.add(operand.operandId())) {
                throw invalid("Operand 标识重复");
            }
            int expected = nextSequence.getOrDefault(operand.role(), 1);
            if (operand.valueSequence() != expected) {
                throw invalid("Operand Role 内序号不连续");
            }
            nextSequence.put(operand.role(), expected + 1);
            if (variableFormat) {
                VariableOperandJson.validateShape(operand.variableOperand());
            } else {
                if (operand.variableCode() != null || operand.numericUnit() != null || operand.currencyCode() != null || operand.currencyVariableCode() != null)
                    throw invalid("旧格式操作数不能混入变量元数据");
                validateOperandMatrix(operand);
            }
        }
    }

    private static void validateOperandMatrix(OperandView operand) {
        Objects.requireNonNull(operand.role(), "operandRole");
        Objects.requireNonNull(operand.source(), "operandSource");
        Objects.requireNonNull(operand.valueType(), "operandValueType");
        switch (operand.source()) {
            case CONTEXT -> {
                if (operand.fieldCode() == null || operand.fieldCode().isBlank() || operand.literalValue() != null) {
                    throw invalid("Context Operand 字段矩阵无效");
                }
            }
            case LITERAL -> {
                if (operand.fieldCode() != null || operand.literalValue() == null) {
                    throw invalid("Literal Operand 字段矩阵无效");
                }
                validateLiteral(operand.literalValue());
            }
            case FEATURE, VARIABLE -> throw invalid("旧格式闭包不支持该操作数来源");
        }
    }

    private static void validateLiteral(LiteralValue literal) {
        if (literal instanceof StringLiteral string && string.value() == null) {
            throw invalid("String Literal 无效");
        }
        if (!(literal instanceof StringLiteral)
            && !(literal instanceof BooleanLiteral)
            && literal != NullLiteral.INSTANCE) {
            throw invalid("Literal 类型无效");
        }
    }

    private static void requireContiguousNodeSequence(List<NodeView> siblings) {
        for (int index = 0; index < siblings.size(); index++) {
            if (siblings.get(index).sequenceNo() != index + 1) {
                throw invalid("Sibling Node 序号不连续");
            }
        }
    }

    private static void appendPreorder(
        NodeView node,
        Map<Long, List<NodeView>> children,
        Set<Long> visiting,
        List<NodeView> result
    ) {
        if (!visiting.add(node.nodeId())) {
            throw invalid("Rule 条件树存在环");
        }
        result.add(node);
        for (NodeView child : children.getOrDefault(node.nodeId(), List.of())) {
            appendPreorder(child, children, visiting, result);
        }
        visiting.remove(node.nodeId());
    }

    private static void requireHash(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw invalid(name + " 无效");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw invalid(name + " 无效");
        }
        return value;
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw invalid(name + " 无效");
        }
    }

    private static <T> List<T> immutable(String name, List<T> values) {
        if (values == null || values.stream().anyMatch(Objects::isNull)) {
            throw invalid(name + " 无效");
        }
        return List.copyOf(values);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    public record RuleSetVersionView(
        long ruleSetVersionId,
        long decisionPointId,
        String ruleSetCode,
        String ruleSetName,
        int versionNo,
        RuleSetVersionStatus status,
        String contentHash,
        long createdBy,
        String changeReason,
        Long frozenBy,
        Instant frozenTime,
        Instant createTime
    ) {
    }

    public record DecisionPointView(
        long decisionPointId,
        String pointCode,
        String pointName,
        DecisionBusinessDomain businessDomain,
        DecisionPointStatus status
    ) {
    }

    public record PointVersionView(
        long pointVersionId,
        long decisionPointId,
        int versionNo,
        DecisionPointVersionStatus status,
        String contentHash
    ) {
    }

    public record InputFieldView(
        Long inputFieldId,
        String fieldCode,
        DecisionValueType valueType,
        boolean requiredFlag,
        DataClassification dataClassification,
        boolean ruleReferenceFlag,
        boolean actionParameterFlag
    ) {
    }

    public record RuleView(
        long ruleId,
        String ruleCode,
        String ruleName,
        int priorityNo,
        boolean enabledFlag,
        String reasonCode,
        List<NodeView> conditionNodes,
        RuleActionView action,
        String reasonMessage,
        List<ConfiguredActionView> additionalActions
    ) {
        public RuleView(long ruleId, String ruleCode, String ruleName, int priorityNo, boolean enabledFlag,
            String reasonCode, List<NodeView> conditionNodes, RuleActionView action) {
            this(ruleId, ruleCode, ruleName, priorityNo, enabledFlag, reasonCode, conditionNodes, action, null, List.of());
        }
        public RuleView {
            additionalActions = immutable("additionalActions", additionalActions);
            conditionNodes = immutable("conditionNodes", conditionNodes);
        }
    }

    public record NodeView(
        long nodeId,
        Long parentNodeId,
        int sequenceNo,
        NodeType nodeType,
        GroupOperator groupOperator,
        ComparisonOperator comparisonOperator,
        List<OperandView> operands
    ) {
        public NodeView {
            operands = immutable("operands", operands);
        }
    }

    public record OperandView(
        long operandId,
        OperandRole role,
        int valueSequence,
        OperandSource source,
        String fieldCode,
        DecisionValueType valueType,
        LiteralValue literalValue,
        String variableCode,
        NumericUnit numericUnit,
        String currencyCode,
        String currencyVariableCode
    ) {
        /** 原历史形状保留空的新增字段。 */
        public OperandView(long operandId, OperandRole role, int valueSequence, OperandSource source,
            String fieldCode, DecisionValueType valueType, LiteralValue literalValue) {
            this(operandId, role, valueSequence, source, fieldCode, valueType, literalValue, null, null, null, null);
        }
        /** 转换为共用操作数，变量引用没有当前值。 */
        public VariableOperand variableOperand() {
            return new VariableOperand(role, valueSequence, source, variableCode, fieldCode, valueType,
                literalValue == null ? NullLiteral.INSTANCE : literalValue, numericUnit, currencyCode, currencyVariableCode);
        }
    }

    public record ConfiguredActionView(RuleActionView binding, AdditionalAction configuration) { }

    public record RuleActionView(
        long ruleActionId,
        long actionVersionId,
        int sequenceNo,
        ActionKey action
    ) {
    }
}
