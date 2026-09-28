package com.paytech.risk.decision.domain.rule;

import static com.paytech.risk.decision.domainservice.policy.RuleSetManagementPolicy.requireConfigurable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.decision.core.dal.dataobject.ActionVersion;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointInputField;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointRuleSet;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointVersion;
import com.paytech.risk.decision.core.dal.dataobject.RiskRule;
import com.paytech.risk.decision.core.dal.dataobject.RuleAction;
import com.paytech.risk.decision.core.dal.dataobject.RuleActionParameter;
import com.paytech.risk.decision.core.dal.dataobject.RuleNode;
import com.paytech.risk.decision.core.dal.dataobject.RuleOperand;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;
import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor.InputFieldDescriptor;
import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.ActionKey;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.PersistedActionVersion;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry;
import com.paytech.risk.decision.domain.authoring.RuleActionConfigurationCatalog;
import com.paytech.risk.decision.domain.enums.ConfigurationStatus;
import com.paytech.risk.decision.domain.enums.DecisionBusinessDomain;
import com.paytech.risk.decision.domain.enums.DecisionPointStatus;
import com.paytech.risk.decision.domain.enums.ExecutionVersionStatus;
import com.paytech.risk.decision.domain.enums.NodeType;
import com.paytech.risk.decision.domain.enums.OperandRole;
import com.paytech.risk.decision.domain.enums.OperandSource;
import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ActionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.BooleanValue;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ConditionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.DecisionPointVersionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.GroupProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.InputFieldProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.LiteralProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.NullValue;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.OperandProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.PredicateProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.RuleProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.RuleSetBindingProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.RuleSetVersionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.StringValue;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer;
import com.paytech.risk.decision.domain.rule.ChangeReasonPolicy;
import com.paytech.risk.decision.domain.rule.CreateRuleSetVersionContext;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.ConfiguredActionView;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.DecisionPointView;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.InputFieldView;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.NodeView;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.OperandView;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.PointVersionView;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.RuleActionView;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.RuleSetVersionView;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.RuleView;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.BooleanLiteral;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.LiteralValue;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NormalizedCondition;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NormalizedGroup;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NormalizedOperand;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NormalizedPredicate;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NormalizedRule;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NormalizedRuleSet;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NullLiteral;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.StringLiteral;
import com.paytech.risk.decision.domain.variable.VariableOperandJson;
import com.paytech.risk.decision.domain.variable.VariableRuleConfiguration;
import com.paytech.risk.decision.domainservice.convert.RuleActionConfigurationConverter;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService.RuleSetSummary;
import lombok.AllArgsConstructor;

/** 规则的持久化投影、历史 Hash 核验和新版本装配；不加载关联或执行写入。 */
@Component
@AllArgsConstructor
public class DecisionRuleSetAssembler {
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private static final DateTimeFormatter MICROSECOND_UTC = DateTimeFormatter
        .ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    private final DecisionActionRegistry actionRegistry;
    private final RiskDecisionCanonicalizer canonicalizer;
    private final DecisionIdentifierGenerator identifiers;
    private final CheckoutPrepareAuthoringDescriptor descriptor;

    /** 在锁定版本与已核验目录上构造一次完整写入上下文，不查询数据库。 */
    public CreateRuleSetVersionContext createContext(NormalizedRuleSet normalized, DecisionPoint point,
        RuleSet management, RuleSetVersion current, DecisionPointVersion companion, List<ActionVersion> catalog,
        long actorAccountId, Instant commandTime) {
        int versionNo = nextRuleSetVersion(normalized, current);
        IdentifierScope scope = new IdentifierScope();
        long id = scope.next();
        MaterializedRules materialized = materializeRules(normalized, id, commandTime, scope,
            indexFields(descriptor.inputFields()), catalog);
        RuleSetVersionProjection projection = ruleSetProjection(normalized, versionNo, catalog);
        String hash = canonicalizer.ruleSetVersion(projection).sha256LowerHex();
        RuleSetVersion version = new RuleSetVersion(id, point.decisionPointId(), normalized.ruleSetCode(),
            normalized.ruleSetName(), versionNo, RuleSetVersionStatus.DRAFT, hash, actorAccountId,
            ChangeReasonPolicy.normalize(normalized.changeReason()), null, null, commandTime, commandTime,
            contentFormat(normalized), descriptor.point().inputContractCode(), descriptor.point().inputContractVersion());
        RuleSet root = management == null
            ? new RuleSet(scope.next(), point.decisionPointId(), normalized.ruleSetCode(), id,
                RuleSetManagementStatus.CONFIGURABLE, 1, null, null, null, null, commandTime, commandTime)
            : management;
        CreateRuleSetVersionContext context = new CreateRuleSetVersionContext(root, version, current, companion,
            materialized.rules(), materialized.nodes(), materialized.operands(), materialized.actions(), materialized.parameters());
        context.verifyIntegrity();
        verifyNewSnapshot(point, context, catalog, projection);
        return context;
    }

    /** 核验管理根和准确头版本，组装列表事实。 */
    public RuleSetSummary toSummary(DecisionRuleSetRepository.RuleSetSummary row, boolean inUse) {
        RuleSetVersion root = row.ruleSetVersion();
        RuleSet management = row.managementRoot();
        if (management == null) throw corruption("规则集管理根损坏");
        management.verifyManagementHead(root);
        requireConfigurable(management);
        DecisionPoint point = row.decisionPoint();
        DecisionPointRuleSet binding = row.companionBinding();
        verifyPoint(point);
        descriptor.inputFields(point.pointCode(), root.inputContractCode(), root.inputContractVersion());
        if (root.ruleSetVersionId() <= 0 || root.decisionPointId() != point.decisionPointId()
            || (binding != null && (binding.bindingId() <= 0 || binding.ruleSetVersionId() != root.ruleSetVersionId()
            || binding.pointVersionId() <= 0 || binding.sequenceNo() != 1)) || root.versionNo() <= 0 || root.createdBy() <= 0
            || root.contentHash() == null || !HASH.matcher(root.contentHash()).matches()) {
            throw corruption("Rule Set 分页闭包损坏");
        }
        root.verifyStatusEvidence();
        return new RuleSetSummary(root.ruleSetVersionId(), binding == null ? null : binding.pointVersionId(), point.pointCode(),
            root.ruleSetCode(), root.ruleSetName(), root.versionNo(), root.status(), root.contentHash(),
            root.createdBy(), root.changeReason(), root.createTime(), root.frozenBy(), root.frozenTime(),
            row.managementRoot().getRuleSetId(), row.managementRoot().getVersion(), inUse);
    }

    /** 按原版本内容验证规范 Hash 并装配领域读取结果。 */
    public DecisionRuleSetAggregate toAggregate(DecisionRuleSetRepository.DecisionRuleSetAggregate raw) {
        return toAggregate(raw, false);
    }

    /** 按原版本内容验证规范 Hash 并装配领域读取结果。 */
    public DecisionRuleSetAggregate toAggregate(DecisionRuleSetRepository.DecisionRuleSetAggregate raw, boolean legacy) {
        try {
            verifyPoint(raw.decisionPoint());
            List<InputFieldDescriptor> fields = descriptor.inputFields(raw.decisionPoint().pointCode(),
                raw.ruleSetVersion().inputContractCode(), raw.ruleSetVersion().inputContractVersion());
            List<PersistedActionVersion> actions = raw.actionVersions().stream()
                .map(this::toHistoricalReferencedRow).sorted(Comparator.comparing(PersistedActionVersion::actionCode)
                    .thenComparingInt(PersistedActionVersion::versionNo)).toList();
            verifyReferencedActions(raw.actionVersions());
            Map<Long, ActionVersion> actionById = indexActions(raw.actionVersions());
            List<RuleView> rules = toRuleViews(raw, indexFields(fields), actionById);
            RuleSetVersionProjection ruleProjection = projection(raw, rules, actionById);
            DecisionPointVersionProjection pointProjection = null;
            if (legacy) {
                requireSupportedClosure(raw);
                verifyPointVersion(raw.pointVersion());
                var historicalFields = validateFields(raw.inputFields(), raw.pointVersion().pointVersionId(),
                    raw.actionVersions().stream().anyMatch(action -> !action.terminalFlag()));
                pointProjection = projection(raw, historicalFields);
                requireStoredHash(raw.pointVersion().contentHash(), canonicalizer.decisionPointVersion(pointProjection)
                    .sha256LowerHex(), "Point Version");
            }
            requireStoredHash(raw.ruleSetVersion().contentHash(), canonicalizer.ruleSetVersion(ruleProjection)
                .sha256LowerHex(), "Rule Set");
            RuleSetVersion root = raw.ruleSetVersion();
            root.verifyStatusEvidence();
            return new DecisionRuleSetAggregate(
                new RuleSetVersionView(root.ruleSetVersionId(), root.decisionPointId(), root.ruleSetCode(),
                    root.ruleSetName(), root.versionNo(), root.status(), root.contentHash(), root.createdBy(),
                    root.changeReason(), root.frozenBy(), root.frozenTime(), root.createTime()),
                new DecisionPointView(raw.decisionPoint().decisionPointId(), raw.decisionPoint().pointCode(),
                    raw.decisionPoint().pointName(), raw.decisionPoint().businessDomain(), raw.decisionPoint().status()),
                raw.pointVersion() == null ? null : new PointVersionView(raw.pointVersion().pointVersionId(), raw.pointVersion().decisionPointId(),
                    raw.pointVersion().versionNo(), raw.pointVersion().status(), raw.pointVersion().contentHash()),
                fields.stream().map(value -> new InputFieldView(null, value.fieldCode(),
                    value.valueType(), value.requiredFlag(), value.dataClassification(), value.ruleReferenceFlag(),
                    value.actionParameterFlag())).toList(), rules, ruleProjection, pointProjection, actions);
        } catch (ErrorException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw corruption("Rule Set 持久化闭包无法形成可信领域对象", exception);
        }
    }

    /** 拒绝当前读取合同不支持的执行组成。 */
    private void requireSupportedClosure(DecisionRuleSetRepository.DecisionRuleSetAggregate raw) {
        Objects.requireNonNull(raw, "raw");
        if (!raw.pointFeatures().isEmpty() || !raw.pointObligations().isEmpty()
            || !raw.featureVersions().isEmpty() || !raw.featureDependencies().isEmpty()
            || raw.pointRuleSets().size() != 1 || raw.pointRuleSets().getFirst().sequenceNo() != 1
            || raw.pointRuleSets().getFirst().ruleSetVersionId() != raw.ruleSetVersion().ruleSetVersionId()
            || raw.pointRuleSets().getFirst().pointVersionId() != raw.pointVersion().pointVersionId()) {
            throw corruption("首期 Rule Set 闭包包含未支持组成或 Binding 损坏");
        }
    }

    /** 核验规则、节点、操作数和动作之间的准确引用。 */
    private List<RuleView> toRuleViews(
        DecisionRuleSetRepository.DecisionRuleSetAggregate raw,
        Map<String, InputFieldDescriptor> fields,
        Map<Long, ActionVersion> actions
    ) {
        List<RuleActionConfigurationCatalog.ConfigurationAction> definitions = raw.actionVersions().stream()
            .filter(action -> !action.terminalFlag())
            .map(action -> RuleActionConfigurationCatalog.require(new ActionKey(action.actionCode(), action.versionNo()))).toList();
        RuleActionConfigurationConverter.verifyDefinitions(raw.actionParameterDefinitions(), definitions);
        Map<Long, List<RuleNode>> nodesByRule = groupNodes(raw.nodes());
        Map<Long, List<RuleOperand>> operandsByNode = groupOperands(raw.operands());
        Map<Long, List<RuleAction>> actionsByRule = groupRuleActions(raw.ruleActions());
        return raw.rules().stream().sorted(Comparator.comparingInt(RiskRule::priorityNo)
            .thenComparing(RiskRule::ruleCode)).map(rule -> {
                List<RuleAction> bindings = actionsByRule.getOrDefault(rule.ruleId(), List.of()).stream()
                    .sorted(Comparator.comparingInt(RuleAction::sequenceNo)).toList();
                List<ConfiguredActionView> additional = new ArrayList<>();
                RuleActionView terminal = null;
                for (RuleAction binding : bindings) {
                    ActionVersion action = actions.get(binding.actionVersionId());
                    if (action == null) throw corruption("Rule Action 引用缺失");
                    RuleActionView view = new RuleActionView(binding.ruleActionId(), binding.actionVersionId(),
                        binding.sequenceNo(), new ActionKey(action.actionCode(), action.versionNo()));
                    if (action.terminalFlag()) {
                        if (terminal != null || binding.targetBindingCode() != null
                            || raw.ruleActionParameters().stream().anyMatch(value -> value.ruleActionId() == binding.ruleActionId()))
                            throw corruption("终态动作矩阵损坏");
                        terminal = view;
                    } else {
                        additional.add(new ConfiguredActionView(view, RuleActionConfigurationConverter.restore(binding,
                            action, raw.ruleActionParameters(), fields, additional.size() + 1,
                                VariableRuleConfiguration.CONTENT_FORMAT.equals(raw.ruleSetVersion().contentFormat()))));
                    }
                }
                List<RuleNode> ordered = preorder(nodesByRule.getOrDefault(rule.ruleId(), List.of()));
                List<NodeView> nodeViews = ordered.stream().map(node -> new NodeView(node.nodeId(),
                    node.parentNodeId(), node.sequenceNo(), node.nodeType(), node.groupOperator(),
                    node.comparisonOperator(), operandsByNode.getOrDefault(node.nodeId(), List.of()).stream()
                        .sorted(Comparator.comparingInt((RuleOperand value) ->
                            value.operandRole() == OperandRole.LEFT ? 0 : 1)
                            .thenComparingInt(RuleOperand::valueSequence).thenComparingLong(RuleOperand::operandId))
                        .map(value -> toOperandView(value, fields, VariableRuleConfiguration.CONTENT_FORMAT.equals(raw.ruleSetVersion().contentFormat()))).toList())).toList();
                return new RuleView(rule.ruleId(), rule.ruleCode(), rule.ruleName(), rule.priorityNo(),
                    rule.enabledFlag(), rule.reasonCode(), nodeViews, terminal, rule.reasonMessage(), additional);
            }).toList();
    }

    /** 按原合同构造参与内容 Hash 的稳定投影。 */
    private RuleSetVersionProjection projection(
        DecisionRuleSetRepository.DecisionRuleSetAggregate raw,
        List<RuleView> rules,
        Map<Long, ActionVersion> actions
    ) {
        return new RuleSetVersionProjection(raw.decisionPoint().pointCode(), raw.ruleSetVersion().ruleSetCode(),
            raw.ruleSetVersion().ruleSetName(), raw.ruleSetVersion().versionNo(), rules.stream().map(rule -> {
                ActionVersion action = rule.action() == null ? null : actions.get(rule.action().actionVersionId());
                return new RuleProjection(rule.ruleCode(), rule.ruleName(), rule.priorityNo(), rule.enabledFlag(),
                    rule.reasonCode(), toCondition(rule.conditionNodes()), action == null ? null : toActionProjection(action),
                    rule.reasonMessage(), RuleActionConfigurationConverter.projections(rule.additionalActions().stream()
                        .map(ConfiguredActionView::configuration).toList()));
            }).toList(), raw.ruleSetVersion().contentFormat());
    }

    /** 按原合同构造参与内容 Hash 的稳定投影。 */
    private DecisionPointVersionProjection projection(
        DecisionRuleSetRepository.DecisionRuleSetAggregate raw,
        List<DecisionPointInputField> fields
    ) {
        DecisionPointVersion point = raw.pointVersion();
        DecisionPointRuleSet binding = raw.pointRuleSets().getFirst();
        return new DecisionPointVersionProjection(raw.decisionPoint().pointCode(),
            raw.decisionPoint().businessDomain(), point.versionNo(), point.inputContractCode(),
            point.inputContractVersion(), point.outputContractCode(), point.defaultDecision(),
            point.evaluationStrategy(), point.failureStrategy(), point.timeoutMillis(),
            fields.stream().map(this::toInputProjection).toList(),
            List.of(new RuleSetBindingProjection(binding.sequenceNo(), raw.ruleSetVersion().ruleSetCode(),
                raw.ruleSetVersion().versionNo(), raw.ruleSetVersion().contentHash())),
            List.of(), List.of(), List.of());
    }

    /** 按原节点顺序重建条件 Hash 投影。 */
    private ConditionProjection toCondition(List<NodeView> nodes) {
        Map<Long, NodeView> byId = new HashMap<>();
        Map<Long, List<NodeView>> children = new HashMap<>();
        NodeView root = null;
        for (NodeView node : nodes) {
            byId.put(node.nodeId(), node);
            if (node.parentNodeId() == null) {
                if (root != null) {
                    throw corruption("Rule AST Root 重复");
                }
                root = node;
            } else {
                children.computeIfAbsent(node.parentNodeId(), ignored -> new ArrayList<>()).add(node);
            }
        }
        if (root == null) {
            throw corruption("Rule AST Root 缺失");
        }
        children.values().forEach(values -> values.sort(Comparator.comparingInt(NodeView::sequenceNo)
            .thenComparingLong(NodeView::nodeId)));
        return toCondition(root, children, new HashSet<>());
    }

    /** 按原节点顺序重建条件 Hash 投影。 */
    private ConditionProjection toCondition(
        NodeView node,
        Map<Long, List<NodeView>> children,
        Set<Long> visiting
    ) {
        if (!visiting.add(node.nodeId())) {
            throw corruption("Rule AST 存在环");
        }
        ConditionProjection projection;
        if (node.nodeType() == NodeType.GROUP) {
            projection = new GroupProjection(node.groupOperator(), children.getOrDefault(node.nodeId(), List.of())
                .stream().map(child -> toCondition(child, children, visiting)).toList());
        } else {
            projection = new PredicateProjection(node.comparisonOperator(), node.operands().stream()
                .map(this::toOperandProjection).toList());
        }
        visiting.remove(node.nodeId());
        return projection;
    }

    /** 读取持久化类型化操作数并核验字段用途。 */
    private OperandView toOperandView(RuleOperand value, Map<String, InputFieldDescriptor> fields, boolean variableFormat) {
        if (variableFormat) {
            if (value.featureVersionId() != null || value.fieldCode() != null
                || value.sourceType() == OperandSource.VARIABLE && hasLiteral(value)) throw corruption("变量操作数持久化形状损坏");
            var view = new OperandView(value.operandId(), value.operandRole(), value.valueSequence(), value.sourceType(), null,
                value.valueType(), value.sourceType() == OperandSource.VARIABLE ? null : literal(value), value.variableCode(),
                value.numericUnit(), value.currencyCode(), value.currencyVariableCode());
            VariableOperandJson.validateShape(view.variableOperand());
            return view;
        }
        if (value.variableCode() != null || value.numericUnit() != null || value.currencyCode() != null || value.currencyVariableCode() != null)
            throw corruption("旧格式包含新增变量元数据");
        if (value.sourceType() == OperandSource.FEATURE || value.featureVersionId() != null) {
            throw corruption("首期 Rule Operand 不支持 Feature");
        }
        if (value.sourceType() == OperandSource.CONTEXT) {
            InputFieldDescriptor field = value.fieldCode() == null ? null : fields.get(value.fieldCode());
            if (field == null || !field.ruleReferenceFlag() || hasLiteral(value)) {
                throw corruption("Context Operand 字段矩阵损坏");
            }
            if (field.valueType() != value.valueType()) {
                throw corruption("Context Operand 类型与 Input Field 不一致");
            }
            return new OperandView(value.operandId(), value.operandRole(), value.valueSequence(), value.sourceType(),
                field.fieldCode(), value.valueType(), null);
        }
        if (value.sourceType() != OperandSource.LITERAL || value.fieldCode() != null) {
            throw corruption("Literal Operand 字段矩阵损坏");
        }
        return new OperandView(value.operandId(), value.operandRole(), value.valueSequence(), value.sourceType(),
            null, value.valueType(), literal(value));
    }

    /** 严格恢复持久化 Literal，不推断缺失的类型值。 */
    private LiteralValue literal(RuleOperand value) {
        int present = (value.stringValue() == null ? 0 : 1) + (value.numberValue() == null ? 0 : 1)
            + (value.booleanValue() == null ? 0 : 1) + (value.datetimeValue() == null ? 0 : 1)
            + (value.durationValue() == null ? 0 : 1);
        if (present != 1) {
            throw corruption("Literal Operand 必须恰有一个类型化值");
        }
        return switch (value.valueType()) {
            case STRING -> {
                if (value.stringValue() == null) throw corruption("STRING Literal 字段矩阵损坏");
                yield new StringLiteral(value.stringValue());
            }
            case NUMBER -> {
                if (value.numberValue() == null) throw corruption("NUMBER Literal 字段矩阵损坏");
                yield new StringLiteral(value.numberValue().toPlainString());
            }
            case BOOLEAN -> {
                if (value.booleanValue() == null) throw corruption("BOOLEAN Literal 字段矩阵损坏");
                yield new BooleanLiteral(value.booleanValue());
            }
            case DATETIME -> {
                if (value.datetimeValue() == null) throw corruption("DATETIME Literal 字段矩阵损坏");
                yield new StringLiteral(formatTime(value.datetimeValue()));
            }
            case DURATION -> {
                if (value.durationValue() == null) throw corruption("DURATION Literal 字段矩阵损坏");
                yield new StringLiteral(Long.toString(value.durationValue()));
            }
        };
    }

    /** 构造不含当前变量展示值的操作数投影。 */
    private OperandProjection toOperandProjection(OperandView value) {
        return new OperandProjection(value.role(), value.valueSequence(), value.source(), value.fieldCode(),
            value.valueType(), toLiteralProjection(value.literalValue()), value.variableCode(), value.numericUnit(), value.currencyCode(), value.currencyVariableCode());
    }

    /** 将领域 Literal 转为规范 Hash 支持的类型。 */
    private static RiskDecisionCanonicalizer.LiteralProjection toLiteralProjection(LiteralValue value) {
        if (value == null || value == NullLiteral.INSTANCE) {
            return NullValue.INSTANCE;
        }
        if (value instanceof StringLiteral string) {
            return new StringValue(string.value());
        }
        if (value instanceof BooleanLiteral bool) {
            return new BooleanValue(bool.value());
        }
        throw corruption("Literal 投影类型无效");
    }

    /** 历史读取只核验实际引用动作，不套用当前可用性。 */
    private void verifyReferencedActions(List<ActionVersion> values) {
        for (ActionVersion value : values) {
            if (!value.terminalFlag()) {
                RuleActionConfigurationConverter.verifyAction(value, false);
                continue;
            }
            try {
                var expected = actionRegistry.require(new ActionKey(value.actionCode(), value.versionNo()));
                PersistedActionVersion actual = toHistoricalReferencedRow(value);
                long expectedId = 240001L + descriptor.actions().indexOf(expected);
                PersistedActionVersion expectedRow = new PersistedActionVersion(expectedId,
                    expected.actionCode(), expected.versionNo(), expected.handlerCode(),
                    expected.implementationVersion(), expected.implementationHash(), expected.actionCategory(),
                    expected.terminalFlag(), expected.decisionCode(), expected.mergePolicy(),
                    expected.shadowSupportedFlag(), expected.status());
                if (!actual.equals(expectedRow) || !expected.actionName().equals(value.actionName())) {
                    throw corruption("引用动作版本与工程注册表不一致");
                }
            } catch (InvalidParameterException exception) {
                throw corruption("引用动作版本不在工程注册表", exception);
            }
        }
    }

    /** 允许已退休的准确动作版本参与历史核验。 */
    private PersistedActionVersion toHistoricalReferencedRow(ActionVersion value) {
        if (!value.terminalFlag()) {
            RuleActionConfigurationConverter.verifyAction(value, false);
            return toStableActionRow(value);
        }
        if (value.actionVersionId() <= 0
            || (value.status() != ExecutionVersionStatus.ACTIVE
                && value.status() != ExecutionVersionStatus.RETIRED)) {
            throw corruption("Action Version 历史引用状态或标识损坏");
        }
        return toStableActionRow(value);
    }

    /** 提取已冻结动作实现和执行能力。 */
    private PersistedActionVersion toStableActionRow(ActionVersion value) {
        return new PersistedActionVersion(value.actionVersionId(), value.actionCode(), value.versionNo(),
            value.handlerCode(), value.implementationVersion(), value.implementationHash(), value.actionCategory(),
            value.terminalFlag(), value.decisionCode(), value.mergePolicy(), value.shadowSupportedFlag(),
            value.terminalFlag() ? ConfigurationStatus.ACTIVE : ConfigurationStatus.CONFIGURATION_ONLY);
    }

    /** 基准版本和内容一致才分配下一业务版本号。 */
    public int nextRuleSetVersion(NormalizedRuleSet normalized, RuleSetVersion current) {
        if (current == null) {
            if (normalized.baseRuleSetVersionId() != null || normalized.baseContentHash() != null) {
                throw concurrent("Rule Set 基础版本不存在");
            }
            return 1;
        }
        if (current.status() == RuleSetVersionStatus.RETIRED) {
            throw corruption("最新 Rule Set Version 不得为 RETIRED");
        }
        if (normalized.baseRuleSetVersionId() == null || normalized.baseContentHash() == null
            || normalized.baseRuleSetVersionId() != current.ruleSetVersionId()
            || !normalized.baseContentHash().equals(current.contentHash())) {
            throw concurrent("Rule Set 基础版本已变化");
        }
        try {
            return Math.addExact(current.versionNo(), 1);
        } catch (ArithmeticException exception) {
            throw corruption("Rule Set Version No 已溢出", exception);
        }
    }

    /** 为规范化规则生成只追加的各表事实。 */
    private MaterializedRules materializeRules(
        NormalizedRuleSet normalized,
        long ruleSetVersionId,
        Instant commandTime,
        IdentifierScope scope,
        Map<String, InputFieldDescriptor> fields,
        List<ActionVersion> catalog
    ) {
        Map<ActionKey, ActionVersion> actionsByKey = new HashMap<>();
        catalog.forEach(action -> actionsByKey.put(new ActionKey(action.actionCode(), action.versionNo()), action));
        List<RiskRule> rules = new ArrayList<>();
        List<RuleNode> nodes = new ArrayList<>();
        List<RuleOperand> operands = new ArrayList<>();
        List<RuleAction> actions = new ArrayList<>();
        List<RuleActionParameter> parameters = new ArrayList<>();
        for (NormalizedRule rule : normalized.rules()) {
            long ruleId = scope.next();
            rules.add(new RiskRule(ruleId, ruleSetVersionId, rule.ruleCode(), rule.ruleName(), rule.priorityNo(),
                rule.enabled(), rule.reasonCode(), commandTime, commandTime, rule.reasonMessage()));
            materializeCondition(rule.condition(), null, 1, ruleId, commandTime, scope, fields, nodes, operands);
            int sequence = 1;
            if (rule.additionalActions() != null) for (var extra : rule.additionalActions()) {
                ActionVersion action = actionsByKey.get(extra.action());
                if (action == null) throw corruption("配置动作目录缺失");
                long bindingId = scope.next();
                actions.add(new RuleAction(bindingId, ruleId, action.actionVersionId(), sequence++, commandTime,
                    extra.targetBindingCode()));
                parameters.addAll(RuleActionConfigurationConverter.materialize(extra, bindingId, fields, scope::next, commandTime));
            }
            if (rule.action() != null) {
                ActionVersion action = actionsByKey.get(rule.action());
                if (action == null) throw corruption("终态动作目录缺失");
                actions.add(new RuleAction(scope.next(), ruleId, action.actionVersionId(), sequence, commandTime));
            }
        }
        return new MaterializedRules(List.copyOf(rules), List.copyOf(nodes), List.copyOf(operands),
            List.copyOf(actions), List.copyOf(parameters));
    }

    /** 按条件树顺序分配节点及操作数身份。 */
    private void materializeCondition(
        NormalizedCondition condition,
        Long parentNodeId,
        int sequenceNo,
        long ruleId,
        Instant commandTime,
        IdentifierScope scope,
        Map<String, InputFieldDescriptor> fields,
        List<RuleNode> nodes,
        List<RuleOperand> operands
    ) {
        long nodeId = scope.next();
        if (condition instanceof NormalizedGroup group) {
            nodes.add(new RuleNode(nodeId, ruleId, parentNodeId, NodeType.GROUP, group.groupOperator(), null,
                sequenceNo, commandTime, commandTime));
            for (int index = 0; index < group.children().size(); index++) {
                materializeCondition(group.children().get(index), nodeId, index + 1, ruleId, commandTime, scope,
                    fields, nodes, operands);
            }
            return;
        }
        if (!(condition instanceof NormalizedPredicate predicate)) {
            throw corruption("规范化 AST 包含未知节点类型");
        }
        nodes.add(new RuleNode(nodeId, ruleId, parentNodeId, NodeType.PREDICATE, null,
            predicate.comparisonOperator(), sequenceNo, commandTime, commandTime));
        for (NormalizedOperand operand : predicate.operands()) {
            operands.add(materializeOperand(operand, nodeId, commandTime, scope, fields));
        }
    }

    /** 将类型化 Literal 和变量引用写入准确持久化列。 */
    private RuleOperand materializeOperand(
        NormalizedOperand operand,
        long nodeId,
        Instant commandTime,
        IdentifierScope scope,
        Map<String, InputFieldDescriptor> fields
    ) {
        String fieldCode = null;
        String stringValue = null;
        BigDecimal numberValue = null;
        Boolean booleanValue = null;
        Instant datetimeValue = null;
        Long durationValue = null;
        if (operand.sourceType() == OperandSource.CONTEXT) {
            InputFieldDescriptor field = fields.get(operand.fieldCode());
            if (field == null) {
                throw corruption("规范化 Context Operand 引用了未知字段");
            }
            if (!field.ruleReferenceFlag() || field.valueType() != operand.valueType()) throw corruption("Context 字段用途或类型不一致");
            fieldCode = field.fieldCode();
        } else if (operand.sourceType() == OperandSource.LITERAL) {
            if (operand.literalValue() instanceof BooleanLiteral bool) {
                booleanValue = bool.value();
            } else if (operand.literalValue() instanceof StringLiteral text) {
                switch (operand.valueType()) {
                    case STRING -> stringValue = text.value();
                    case NUMBER -> numberValue = new BigDecimal(text.value());
                    case DATETIME -> datetimeValue = Instant.parse(text.value());
                    case DURATION -> durationValue = Long.valueOf(text.value());
                    case BOOLEAN -> throw corruption("BOOLEAN Literal 的规范类型损坏");
                }
            } else {
                throw corruption("Literal Operand 缺少规范值");
            }
        } else if (operand.sourceType() != OperandSource.VARIABLE) {
            throw corruption("当前配置不支持该操作数来源");
        }
        return new RuleOperand(scope.next(), nodeId, operand.operandRole(), operand.valueSequence(),
            operand.sourceType(), null, fieldCode, operand.valueType(), stringValue, numberValue, booleanValue,
            datetimeValue, durationValue, commandTime, commandTime, operand.variableCode(), operand.numericUnit(),
            operand.currencyCode(), operand.currencyVariableCode());
    }

    /** 保留原规则内容格式、顺序及 Hash 字段集合。 */
    private RuleSetVersionProjection ruleSetProjection(
        NormalizedRuleSet normalized,
        int versionNo,
        List<ActionVersion> catalog
    ) {
        Map<ActionKey, ActionVersion> byKey = new HashMap<>();
        catalog.forEach(value -> byKey.put(new ActionKey(value.actionCode(), value.versionNo()), value));
        return new RuleSetVersionProjection(normalized.pointCode(), normalized.ruleSetCode(),
            normalized.ruleSetName(), versionNo, normalized.rules().stream().map(rule -> {
                ActionVersion action = byKey.get(rule.action());
                if (action == null && rule.action() != null) throw corruption("规范 Action 与持久化目录不一致");
                return new RuleProjection(rule.ruleCode(), rule.ruleName(), rule.priorityNo(), rule.enabled(),
                    rule.reasonCode(), toConditionProjection(rule.condition()), action == null ? null : toActionProjection(action),
                    rule.reasonMessage(), RuleActionConfigurationConverter.projections(rule.additionalActions()));
            }).toList(), contentFormat(normalized));
    }

    /** 将已规范化条件转换为固定内容投影。 */
    private ConditionProjection toConditionProjection(NormalizedCondition condition) {
        if (condition instanceof NormalizedGroup group) {
            return new GroupProjection(group.groupOperator(), group.children().stream()
                .map(this::toConditionProjection).toList());
        }
        if (condition instanceof NormalizedPredicate predicate) {
            return new PredicateProjection(predicate.comparisonOperator(), predicate.operands().stream()
                .map(operand -> new OperandProjection(operand.operandRole(), operand.valueSequence(),
                    operand.sourceType(), operand.fieldCode(), operand.valueType(),
                    toLiteralProjection(operand.literalValue()), operand.variableCode(), operand.numericUnit(),
                    operand.currencyCode(), operand.currencyVariableCode())).toList());
        }
        throw corruption("规范化 AST 类型无效");
    }

    /** 提取动作的稳定实现投影。 */
    private ActionProjection toActionProjection(ActionVersion action) {
        return new ActionProjection(action.actionCode(), action.versionNo(), action.handlerCode(),
            action.implementationVersion(), action.implementationHash(), action.actionCategory(),
            action.terminalFlag(), action.decisionCode(), action.mergePolicy(), action.shadowSupportedFlag());
    }

    /** 旧输入字段保留准确协议属性参与历史 Hash。 */
    private InputFieldProjection toInputProjection(DecisionPointInputField field) {
        return new InputFieldProjection(field.fieldCode(), field.valueType(), field.requiredFlag(),
            field.dataClassification(), field.ruleReferenceFlag(), field.actionParameterFlag());
    }

    /** 新准备的各表事实必须重算出同一规范内容。 */
    private void verifyNewSnapshot(
        DecisionPoint point,
        CreateRuleSetVersionContext snapshot,
        List<ActionVersion> catalog,
        RuleSetVersionProjection ruleProjection
    ) {
        List<Long> usedActionIds = snapshot.getRuleActions().stream().map(RuleAction::actionVersionId).distinct().toList();
        Set<Long> used = Set.copyOf(usedActionIds);
        List<ActionVersion> referenced = catalog.stream().filter(value -> used.contains(value.actionVersionId())).toList();
        DecisionRuleSetRepository.DecisionRuleSetAggregate raw = new DecisionRuleSetRepository.DecisionRuleSetAggregate(
            point, null, List.of(), List.of(), List.of(),
            List.of(), snapshot.getRuleSetVersion(), snapshot.getRules(), snapshot.getNodes(), snapshot.getOperands(),
            snapshot.getRuleActions(), referenced, referenced.stream().filter(action -> !action.terminalFlag())
                .flatMap(action -> RuleActionConfigurationConverter.definitions(RuleActionConfigurationCatalog.require(
                    new ActionKey(action.actionCode(), action.versionNo())), action.createTime()).stream()).toList(),
            snapshot.getRuleActionParameters(), List.of(), List.of());
        DecisionRuleSetAggregate aggregate = toAggregate(raw);
        if (!aggregate.ruleSetProjection().equals(ruleProjection)) {
            throw corruption("新 Rule Set 快照与规范投影不一致");
        }
    }

    /** 核对批准的决策事件点与工程目录。 */
    public void verifyPoint(DecisionPoint point) {
        if (point.decisionPointId() <= 0 || !descriptor.point().pointCode().equals(point.pointCode())
            || !descriptor.point().pointName().equals(point.pointName())
            || point.businessDomain() != DecisionBusinessDomain.CHECKOUT
            || point.status() != DecisionPointStatus.ACTIVE) {
            throw corruption("CHECKOUT_PREPARE Decision Point Seed 漂移");
        }
    }

    /** 取得行锁后重证事件点身份未漂移。 */
    public void verifySamePoint(DecisionPoint located, DecisionPoint locked) {
        if (located.decisionPointId() != locked.decisionPointId()
            || !located.pointCode().equals(locked.pointCode())) {
            throw corruption("Decision Point 锁后身份漂移");
        }
    }

    /** 核验旧执行协议与批准合同及内容 Hash。 */
    private void verifyPointVersion(DecisionPointVersion point) {
        if (point.pointVersionId() <= 0 || point.decisionPointId() <= 0 || point.versionNo() <= 0
            || !descriptor.point().inputContractCode().equals(point.inputContractCode())
            || descriptor.point().inputContractVersion() != point.inputContractVersion()
            || !descriptor.point().outputContractCode().equals(point.outputContractCode())
            || descriptor.point().defaultDecision() != point.defaultDecision()
            || descriptor.point().evaluationStrategy() != point.evaluationStrategy()
            || descriptor.point().failureStrategy() != point.failureStrategy()
            || descriptor.point().timeoutMillis() != point.timeoutMillis()
            || !HASH.matcher(point.contentHash()).matches()) {
            throw corruption("Companion Point Version 协议漂移");
        }
    }

    /** 历史字段按原准确协议核验，禁止使用当前变量替代。 */
    private List<DecisionPointInputField> validateFields(List<DecisionPointInputField> values, long pointVersionId, boolean additional) {
        if (values.size() != inputFields(additional).size()) {
            throw corruption("Point Version Input Field 目录不完整");
        }
        Map<String, InputFieldDescriptor> expected = new HashMap<>();
        inputFields(additional).forEach(value -> expected.put(value.fieldCode(), value));
        Set<Long> ids = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (DecisionPointInputField value : values) {
            InputFieldDescriptor field = expected.get(value.fieldCode());
            if (value.inputFieldId() <= 0 || value.pointVersionId() != pointVersionId
                || !ids.add(value.inputFieldId()) || !codes.add(value.fieldCode()) || field == null
                || value.valueType() != field.valueType() || value.requiredFlag() != field.requiredFlag()
                || value.dataClassification() != field.dataClassification()
                || value.ruleReferenceFlag() != field.ruleReferenceFlag()
                || value.actionParameterFlag() != field.actionParameterFlag()) {
                throw corruption("Point Version Input Field 目录漂移");
            }
        }
        return values.stream().sorted(Comparator.comparing(DecisionPointInputField::fieldCode)).toList();
    }

    /** 按字段编码建立已加载目录索引并拒绝重复。 */
    private static Map<String, InputFieldDescriptor> indexFields(List<InputFieldDescriptor> fields) {
        Map<String, InputFieldDescriptor> result = new HashMap<>();
        for (InputFieldDescriptor field : fields) {
            if (result.put(field.fieldCode(), field) != null) {
                throw corruption("新 Input Field 编码重复");
            }
        }
        return Map.copyOf(result);
    }

    /** 按准确动作版本 ID 建立索引并拒绝重复。 */
    private static Map<Long, ActionVersion> indexActions(List<ActionVersion> actions) {
        Map<Long, ActionVersion> result = new HashMap<>();
        for (ActionVersion action : actions) {
            if (result.put(action.actionVersionId(), action) != null) {
                throw corruption("Action Version 标识重复");
            }
        }
        return Map.copyOf(result);
    }

    /** 按所属规则分组已加载节点。 */
    private static Map<Long, List<RuleNode>> groupNodes(List<RuleNode> values) {
        Map<Long, List<RuleNode>> result = new HashMap<>();
        values.forEach(value -> result.computeIfAbsent(value.ruleId(), ignored -> new ArrayList<>()).add(value));
        return result;
    }

    /** 按所属节点分组已加载操作数。 */
    private static Map<Long, List<RuleOperand>> groupOperands(List<RuleOperand> values) {
        Map<Long, List<RuleOperand>> result = new HashMap<>();
        values.forEach(value -> result.computeIfAbsent(value.nodeId(), ignored -> new ArrayList<>()).add(value));
        return result;
    }

    /** 按所属规则分组已加载动作。 */
    private static Map<Long, List<RuleAction>> groupRuleActions(List<RuleAction> values) {
        Map<Long, List<RuleAction>> result = new HashMap<>();
        values.forEach(value -> result.computeIfAbsent(value.ruleId(), ignored -> new ArrayList<>()).add(value));
        return result;
    }

    /** 验证单根条件树并产生稳定先序。 */
    private static List<RuleNode> preorder(List<RuleNode> values) {
        Map<Long, List<RuleNode>> children = new HashMap<>();
        List<RuleNode> roots = new ArrayList<>();
        for (RuleNode value : values) {
            if (value.parentNodeId() == null) {
                roots.add(value);
            } else {
                children.computeIfAbsent(value.parentNodeId(), ignored -> new ArrayList<>()).add(value);
            }
        }
        Comparator<RuleNode> order = Comparator.comparingInt(RuleNode::sequenceNo).thenComparingLong(RuleNode::nodeId);
        roots.sort(order);
        children.values().forEach(list -> list.sort(order));
        List<RuleNode> result = new ArrayList<>();
        Set<Long> visiting = new HashSet<>();
        if (roots.size() == 1) {
            appendPreorder(roots.getFirst(), children, visiting, result);
        }
        if (result.size() != values.size()) {
            throw corruption("Rule AST 不是单 Root 可达树");
        }
        return List.copyOf(result);
    }

    /** 沿已加载父子关系遍历，拒绝环和越界深度。 */
    private static void appendPreorder(
        RuleNode node,
        Map<Long, List<RuleNode>> children,
        Set<Long> visiting,
        List<RuleNode> result
    ) {
        if (!visiting.add(node.nodeId())) {
            throw corruption("Rule AST 存在环");
        }
        result.add(node);
        children.getOrDefault(node.nodeId(), List.of())
            .forEach(child -> appendPreorder(child, children, visiting, result));
        visiting.remove(node.nodeId());
    }

    /** 判断持久化行是否包含任一 Literal 列。 */
    private static boolean hasLiteral(RuleOperand value) {
        return value.stringValue() != null || value.numberValue() != null || value.booleanValue() != null
            || value.datetimeValue() != null || value.durationValue() != null;
    }

    /** 存储 Hash 必须与原内容重算值相同。 */
    private static void requireStoredHash(String stored, String calculated, String label) {
        if (stored == null || !stored.equals(calculated)) {
            throw corruption(label + " Content Hash 与完整闭包不一致");
        }
    }

    /** 只输出 UTC 六位微秒时间，不隐式丢失精度。 */
    private static String formatTime(Instant value) {
        if (value == null || value.getNano() % 1_000 != 0) {
            throw corruption("持久化 DATETIME Literal 精度无效");
        }
        return MICROSECOND_UTC.format(value);
    }

    /** 报告调用方基准已被其他写入替换。 */
    private static ConcurrentRequestException concurrent(String message) {
        return new ConcurrentRequestException(message);
    }

    /** 将持久化不变量损坏报告为内部错误。 */
    private static ErrorException corruption(String message) {
        return new ErrorException(message);
    }

    /** 将持久化不变量损坏报告为内部错误。 */
    private static ErrorException corruption(String message, Throwable cause) {
        return new ErrorException(message, cause);
    }

    private final class IdentifierScope {

        private final Set<Long> allocated = new HashSet<>();

        long next() {
            long value = identifiers.nextIdentifier();
            if (value <= 0 || !allocated.add(value)) {
                throw corruption("决策标识生成器返回非正数或重复值");
            }
            return value;
        }
    }

    /** 按已批准的操作数与动作格式选择历史 Hash 合同。 */
    private static String contentFormat(NormalizedRuleSet normalized) {
        if (VariableRuleConfiguration.OPERAND_FORMAT.equals(normalized.operandFormat())) return VariableRuleConfiguration.CONTENT_FORMAT;
        return normalized.rules().getFirst().additionalActions() == null ? "LEGACY" : "CONFIGURATION";
    }

    /** 判断新配置是否包含附加动作。 */
    private static boolean hasAdditionalActions(NormalizedRuleSet normalized) {
        return normalized.rules().stream().anyMatch(rule -> rule.additionalActions() != null && !rule.additionalActions().isEmpty());
    }

    /** 组合当前格式允许引用的工程输入字段。 */
    private List<InputFieldDescriptor> inputFields(boolean additional) {
        List<InputFieldDescriptor> result = new ArrayList<>(descriptor.fields());
        if (additional) result.addAll(RuleActionConfigurationCatalog.inputFields());
        return List.copyOf(result);
    }

    /** 装配期间的局部各表结果，随后直接进入完整写入 Context。 */
    private record MaterializedRules(
        List<RiskRule> rules,
        List<RuleNode> nodes,
        List<RuleOperand> operands,
        List<RuleAction> actions,
        List<RuleActionParameter> parameters
    ) {
    }
}
