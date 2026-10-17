package com.paytech.risk.decision.domain.hash;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.jsoncanonicalization.api.JsonCanonicalizationException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.rule.CanonicalUtcMicrosecond;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;

/** 构造 Risk 业务投影并委托 Foundation 生成 canonical JSON bytes。 */
public final class RiskDecisionCanonicalizerImpl implements RiskDecisionCanonicalizer {

    private static final Comparator<String> UTF16 = String::compareTo;
    private static final Comparator<String> NUMERIC_ID = (left, right) -> numericId(left).compareTo(numericId(right));

    @Override
    public CanonicalHash ruleSetVersion(RuleSetVersionProjection value) {
        return project(() -> canonicalRuleSetVersion(value));
    }

    @Override
    public CanonicalHash decisionPointVersion(DecisionPointVersionProjection value) {
        return project(() -> canonicalDecisionPointVersion(value));
    }

    @Override
    public CanonicalHash decisionRelease(DecisionReleaseProjection value) {
        return project(() -> canonicalDecisionRelease(value));
    }

    @Override
    public CanonicalHash createRuleSetVersionCommand(CreateRuleSetVersionCommandProjection value) {
        return project(() -> canonicalCreateRuleSetVersionCommand(value));
    }

    @Override
    public CanonicalHash freezeRuleSetVersionCommand(FreezeRuleSetVersionCommandProjection value) {
        return project(() -> canonicalFreezeRuleSetVersionCommand(value));
    }

    /** 冻结规则集整体身份与管理 CAS 版本的规范删除命令。 */
    @Override
    public CanonicalHash deleteRuleSetCommand(DeleteRuleSetCommandProjection value) {
        return project(() -> hash("RISK_DELETE_RULE_SET_COMMAND_V1", obj(
            "schema", str("RISK_DELETE_RULE_SET_COMMAND_V1"),
            "ruleSetId", str(value.ruleSetId()), "expectedVersion", str(value.expectedVersion()))));
    }

    @Override
    public CanonicalHash createTestReleaseCommand(CreateTestReleaseCommandProjection value) {
        return project(() -> canonicalCreateTestReleaseCommand(value));
    }

    @Override
    public CanonicalHash validateTestReleaseCommand(ValidateTestReleaseCommandProjection value) {
        return project(() -> canonicalValidateTestReleaseCommand(value));
    }

    private CanonicalHash canonicalRuleSetVersion(RuleSetVersionProjection value) {
        List<RuleProjection> rules = sorted(value.rules(), Comparator.comparingInt(RuleProjection::priorityNo)
            .thenComparing(RuleProjection::ruleCode, UTF16));
        boolean configuration = configuration(value.contentFormat());
        boolean variable = variableConfiguration(value.contentFormat());
        String schema = variable ? "RISK_RULE_SET_VARIABLE_CONFIGURATION"
            : configuration ? "RISK_RULE_SET_CONFIGURATION_V1" : "RISK_RULE_SET_VERSION_V1";
        return hash(schema, obj(
            "schema", str(schema),
            "pointCode", str(value.pointCode()),
            "ruleSetCode", str(value.ruleSetCode()),
            "ruleSetName", str(trim(value.ruleSetName())),
            "versionNo", number(value.versionNo()),
            "rules", array(rules.stream().map(rule -> configuration ? configuredRule(rule, variable) : rule(rule)).toList())
        ));
    }

    private CanonicalHash canonicalDecisionPointVersion(DecisionPointVersionProjection value) {
        requireEmpty(value.features(), value.obligations(), value.limitBindings());
        return hash("RISK_DECISION_POINT_VERSION_V1", obj(
            "schema", str("RISK_DECISION_POINT_VERSION_V1"),
            "pointCode", str(value.pointCode()),
            "businessDomain", str(value.businessDomain().getCode()),
            "pointVersionNo", number(value.pointVersionNo()),
            "inputContractCode", str(value.inputContractCode()),
            "inputContractVersion", number(value.inputContractVersion()),
            "outputContractCode", str(value.outputContractCode()),
            "defaultDecision", str(value.defaultDecision().getCode()),
            "evaluationStrategy", str(value.evaluationStrategy().getCode()),
            "failureStrategy", str(value.failureStrategy().getCode()),
            "timeoutMillis", number(value.timeoutMillis()),
            "inputFields", array(sorted(value.inputFields(), Comparator.comparing(InputFieldProjection::fieldCode, UTF16))
                .stream().map(this::inputField).toList()),
            "ruleSets", array(sorted(value.ruleSets(), Comparator.comparingInt(RuleSetBindingProjection::sequenceNo)
                .thenComparing(RuleSetBindingProjection::ruleSetCode, UTF16)).stream().map(this::binding).toList()),
            "features", array(List.of()), "obligations", array(List.of()), "limitBindings", array(List.of())
        ));
    }

    private CanonicalHash canonicalDecisionRelease(DecisionReleaseProjection value) {
        if (value.ruleSetVersions().stream().anyMatch(ruleSet -> variableConfiguration(ruleSet.contentFormat())))
            throw new InvalidParameterException("变量配置尚不支持执行发布");
        requireEmpty(value.features(), value.featureDependencies(), value.actionParameterDefinitions(),
            value.ruleActionParameters(), value.obligations(), value.limitBindings());
        ReleaseDecisionPointProjection point = value.decisionPoint();
        ReleasePointVersionProjection version = value.pointVersion();
        boolean configured = value.ruleSetVersions().stream().anyMatch(ruleSet -> configuration(ruleSet.contentFormat()));
        String schema = configured ? "RISK_DECISION_RELEASE_CONFIGURATION_V1" : "RISK_DECISION_RELEASE_V1";
        return hash(schema, obj(
            "schema", str(schema), "releaseId", str(value.releaseId()),
            "releaseNo", number(value.releaseNo()), "decisionPointId", str(value.decisionPointId()),
            "pointVersionId", str(value.pointVersionId()), "environmentCode", str(value.environmentCode().getCode()),
            "decisionPoint", obj("decisionPointId", str(point.decisionPointId()), "pointCode", str(point.pointCode()),
                "businessDomain", str(point.businessDomain().getCode())),
            "pointVersion", obj("decisionPointId", str(version.decisionPointId()), "pointCode", str(version.pointCode()),
                "pointVersionId", str(version.pointVersionId()), "pointVersionNo", number(version.pointVersionNo()),
                "inputContractCode", str(version.inputContractCode()),
                "inputContractVersion", number(version.inputContractVersion()),
                "outputContractCode", str(version.outputContractCode()),
                "defaultDecision", str(version.defaultDecision().getCode()),
                "evaluationStrategy", str(version.evaluationStrategy().getCode()),
                "failureStrategy", str(version.failureStrategy().getCode()),
                "timeoutMillis", number(version.timeoutMillis()), "contentHash", str(version.contentHash())),
            "inputFields", array(sortId(value.inputFields(), ReleaseInputFieldProjection::inputFieldId, this::releaseInputField).stream()
                .map(this::releaseInputField).toList()),
            "ruleSetBindings", array(sortId(value.ruleSetBindings(), ReleaseRuleSetBindingProjection::bindingId, this::releaseBinding)
                .stream().map(this::releaseBinding).toList()),
            "ruleSetVersions", array(sortId(value.ruleSetVersions(), ReleaseRuleSetVersionProjection::ruleSetVersionId, this::releaseRuleSet)
                .stream().map(ruleSet -> configured ? configuredReleaseRuleSet(ruleSet) : releaseRuleSet(ruleSet)).toList()),
            "rules", array(sortId(value.rules(), ReleaseRuleProjection::ruleId, this::releaseRule).stream().map(rule -> configured ? configuredReleaseRule(rule) : releaseRule(rule)).toList()),
            "nodes", array(sortId(value.nodes(), ReleaseNodeProjection::nodeId, this::releaseNode).stream().map(this::releaseNode).toList()),
            "operands", array(sortId(value.operands(), ReleaseOperandProjection::operandId, this::releaseOperand).stream()
                .map(this::releaseOperand).toList()),
            "actionVersions", array(sortId(value.actionVersions(), ReleaseActionVersionProjection::actionVersionId, this::releaseAction)
                .stream().map(this::releaseAction).toList()),
            "ruleActions", array(sortId(value.ruleActions(), ReleaseRuleActionProjection::ruleActionId, this::releaseRuleAction).stream()
                .map(this::releaseRuleAction).toList()),
            "features", array(List.of()), "featureDependencies", array(List.of()),
            "actionParameterDefinitions", array(List.of()), "ruleActionParameters", array(List.of()),
            "obligations", array(List.of()), "limitBindings", array(List.of())
        ));
    }

    private CanonicalHash canonicalCreateRuleSetVersionCommand(CreateRuleSetVersionCommandProjection value) {
        List<CommandRuleProjection> rules = sorted(value.rules(), Comparator.comparingInt(CommandRuleProjection::priorityNo)
            .thenComparing(CommandRuleProjection::ruleCode, UTF16));
        boolean configuration = configuration(value.contentFormat());
        boolean variable = variableConfiguration(value.contentFormat());
        String schema = variable ? "RISK_CREATE_RULE_SET_VARIABLE_CONFIGURATION_COMMAND"
            : configuration ? "RISK_CREATE_RULE_SET_CONFIGURATION_COMMAND_V1"
            : "RISK_CREATE_RULE_SET_VERSION_COMMAND_V1";
        return hash(schema, obj("schema", str(schema),
            "pointCode", str(value.pointCode()), "ruleSetCode", str(value.ruleSetCode()),
            "ruleSetName", str(trim(value.ruleSetName())), "baseRuleSetVersionId", nullable(value.baseRuleSetVersionId()),
            "baseContentHash", nullable(value.baseContentHash()), "changeReason", str(trim(value.changeReason())),
            "rules", array(rules.stream().map(rule -> configuration ? configuredCommandRule(rule, variable) : commandRule(rule)).toList())));
    }

    private CanonicalHash canonicalFreezeRuleSetVersionCommand(FreezeRuleSetVersionCommandProjection value) {
        return hash("RISK_FREEZE_RULE_SET_VERSION_COMMAND_V1", obj("schema", str("RISK_FREEZE_RULE_SET_VERSION_COMMAND_V1"),
            "ruleSetVersionId", str(value.ruleSetVersionId()), "expectedContentHash", str(value.expectedContentHash())));
    }

    private CanonicalHash canonicalCreateTestReleaseCommand(CreateTestReleaseCommandProjection value) {
        return hash("RISK_CREATE_TEST_RELEASE_COMMAND_V1", obj("schema", str("RISK_CREATE_TEST_RELEASE_COMMAND_V1"),
            "ruleSetVersionId", str(value.ruleSetVersionId()),
            "expectedRuleSetContentHash", str(value.expectedRuleSetContentHash())));
    }

    private CanonicalHash canonicalValidateTestReleaseCommand(ValidateTestReleaseCommandProjection value) {
        return hash("RISK_VALIDATE_TEST_RELEASE_COMMAND_V1", obj("schema", str("RISK_VALIDATE_TEST_RELEASE_COMMAND_V1"),
            "releaseId", str(value.releaseId()), "expectedVersion", number(value.expectedVersion())));
    }

    static CanonicalHash canonicalizeTestObject(List<TestProperty> properties) {
        List<Property> values = properties.stream().map(p -> new Property(p.key(), str(p.value()))).toList();
        String schema = properties.stream().filter(value -> value.key().equals("schema"))
            .map(TestProperty::value).findFirst().orElse("TEST_OBJECT_V1");
        return hash(schema, new ObjectNode(values));
    }

    record TestProperty(String key, String value) { }

    private static boolean configuration(String format) {
        if (!"LEGACY".equals(format) && !"CONFIGURATION".equals(format) && !variableConfiguration(format))
            throw new InvalidParameterException("规则内容格式不支持");
        return !"LEGACY".equals(format);
    }

    private static boolean variableConfiguration(String format) {
        return "RISK_RULE_SET_VARIABLE_CONFIGURATION".equals(format);
    }

    private JNode configuredRule(RuleProjection value, boolean variable) {
        return obj("ruleCode", str(value.ruleCode()), "ruleName", str(trim(value.ruleName())),
            "priorityNo", number(value.priorityNo()), "enabledFlag", bool(value.enabledFlag()),
            "reasonCode", nullable(value.reasonCode()), "reasonMessage", nullable(value.reasonMessage()),
            "condition", condition(value.condition(), variable), "action", value.action() == null ? NullNode.INSTANCE : action(value.action()),
            "additionalActions", additionalActions(value.additionalActions(), variable));
    }

    private JNode configuredCommandRule(CommandRuleProjection value, boolean variable) {
        return obj("ruleCode", str(value.ruleCode()), "ruleName", str(trim(value.ruleName())),
            "priorityNo", number(value.priorityNo()), "enabledFlag", bool(value.enabledFlag()),
            "reasonCode", nullable(value.reasonCode()), "reasonMessage", nullable(value.reasonMessage()),
            "condition", condition(value.condition(), variable), "action", value.action() == null ? NullNode.INSTANCE
                : obj("actionCode", str(value.action().actionCode()), "actionVersionNo", number(value.action().actionVersionNo())),
            "additionalActions", additionalActions(value.additionalActions(), variable));
    }

    private JNode additionalActions(List<AdditionalActionProjection> values, boolean variable) {
        if (variable) return array(values.stream().<JNode>map(value -> obj("sequenceNo", number(value.sequenceNo()),
            "actionCode", str(value.actionCode()), "actionVersionNo", number(value.actionVersionNo()),
            "targetBindingCode", str(value.targetBindingCode()), "subjectVariableCode", str(value.subjectVariableCode()),
            "parameters", array(value.parameters().stream().<JNode>map(parameter -> obj(
                "parameterCode", str(parameter.parameterCode()), "valueSequence", number(parameter.valueSequence()),
                "sourceType", str(parameter.sourceType()), "fieldCode", nullable(parameter.fieldCode()),
                "variableCode", nullable(parameter.variableCode()), "valueType", str(parameter.valueType().getCode()),
                "literalValue", literal(parameter.literalValue(), parameter.valueType()))).toList()))).toList());
        return array(values.stream().<JNode>map(value -> obj("sequenceNo", number(value.sequenceNo()),
            "actionCode", str(value.actionCode()), "actionVersionNo", number(value.actionVersionNo()),
            "targetBindingCode", str(value.targetBindingCode()), "parameters", array(value.parameters().stream()
                .<JNode>map(parameter -> obj("parameterCode", str(parameter.parameterCode()),
                    "valueSequence", number(parameter.valueSequence()), "sourceType", str(parameter.sourceType()),
                    "fieldCode", nullable(parameter.fieldCode()), "valueType", str(parameter.valueType().getCode()),
                    "literalValue", literal(parameter.literalValue(), parameter.valueType()))).toList()))).toList());
    }

    private JNode rule(RuleProjection value) {
        if (value.reasonMessage() != null || !value.additionalActions().isEmpty())
            throw new InvalidParameterException("旧 Hash 格式不接受新增配置");
        return obj("ruleCode", str(value.ruleCode()), "ruleName", str(trim(value.ruleName())),
            "priorityNo", number(value.priorityNo()), "enabledFlag", bool(value.enabledFlag()),
            "reasonCode", nullable(value.reasonCode()), "condition", condition(value.condition()),
            "action", action(value.action()));
    }

    private JNode commandRule(CommandRuleProjection value) {
        if (value.reasonMessage() != null || !value.additionalActions().isEmpty())
            throw new InvalidParameterException("旧命令 Hash 格式不接受新增配置");
        return obj("ruleCode", str(value.ruleCode()), "ruleName", str(trim(value.ruleName())),
            "priorityNo", number(value.priorityNo()), "enabledFlag", bool(value.enabledFlag()),
            "reasonCode", nullable(value.reasonCode()), "condition", condition(value.condition()),
            "action", obj("actionCode", str(value.action().actionCode()),
                "actionVersionNo", number(value.action().actionVersionNo())));
    }

    private JNode condition(ConditionProjection value) { return condition(value, false); }

    private JNode condition(ConditionProjection value, boolean variable) {
        if (value instanceof GroupProjection group) {
            return obj("nodeType", str("GROUP"), "groupOperator", str(group.groupOperator().getCode()),
                "children", array(group.children().stream().map(child -> condition(child, variable)).toList()));
        }
        PredicateProjection predicate = (PredicateProjection) value;
        List<OperandProjection> operands = sorted(predicate.operands(),
            Comparator.comparing((OperandProjection o) -> o.operandRole().ordinal())
                .thenComparingInt(OperandProjection::valueSequence));
        return obj("nodeType", str("PREDICATE"),
            "comparisonOperator", str(predicate.comparisonOperator().getCode()),
            "operands", array(operands.stream().map(operand -> operand(operand, variable)).toList()));
    }

    private JNode operand(OperandProjection value, boolean variable) {
        if (variable) return obj("operandRole", str(value.operandRole().getCode()), "valueSequence", number(value.valueSequence()),
            "sourceType", str(value.sourceType().getCode()), "fieldCode", nullable(value.fieldCode()),
            "variableCode", nullable(value.variableCode()), "valueType", str(value.valueType().getCode()),
            "literalValue", literal(value.literalValue(), value.valueType()),
            "numericUnit", value.numericUnit() == null ? NullNode.INSTANCE : str(value.numericUnit().getCode()),
            "currencyCode", nullable(value.currencyCode()), "currencyVariableCode", nullable(value.currencyVariableCode()));
        if (value.variableCode() != null || value.numericUnit() != null || value.currencyCode() != null
            || value.currencyVariableCode() != null || value.sourceType() == com.paytech.risk.decision.domain.enums.OperandSource.VARIABLE)
            throw new InvalidParameterException("旧 Hash 不接受变量操作数");
        return obj("operandRole", str(value.operandRole().getCode()), "valueSequence", number(value.valueSequence()),
            "sourceType", str(value.sourceType().getCode()), "fieldCode", nullable(value.fieldCode()),
            "valueType", str(value.valueType().getCode()), "literalValue", literal(value.literalValue(), value.valueType()));
    }

    private JNode literal(LiteralProjection value, DecisionValueType type) {
        if (value == null || value == NullValue.INSTANCE) return NullNode.INSTANCE;
        if (value instanceof BooleanValue booleanValue) return bool(booleanValue.value());
        String raw = ((StringValue) value).value();
        if (type == DecisionValueType.NUMBER) return str(decimal(raw));
        if (type == DecisionValueType.DATETIME) return str(datetime(raw));
        if (type == DecisionValueType.DURATION) return str(duration(raw));
        return str(raw);
    }

    private JNode action(ActionProjection v) {
        return obj("actionCode", str(v.actionCode()), "actionVersionNo", number(v.actionVersionNo()),
            "handlerCode", str(v.handlerCode()), "implementationVersion", str(v.implementationVersion()),
            "implementationHash", str(v.implementationHash()), "actionCategory", str(v.actionCategory().getCode()),
            "terminalFlag", bool(v.terminalFlag()), "decisionCode", str(v.decisionCode().getCode()),
            "mergePolicy", str(v.mergePolicy().getCode()), "shadowSupportedFlag", bool(v.shadowSupportedFlag()));
    }

    private JNode inputField(InputFieldProjection v) {
        return obj("fieldCode", str(v.fieldCode()), "valueType", str(v.valueType().getCode()),
            "requiredFlag", bool(v.requiredFlag()), "dataClassification", str(v.dataClassification().getCode()),
            "ruleReferenceFlag", bool(v.ruleReferenceFlag()), "actionParameterFlag", bool(v.actionParameterFlag()));
    }

    private JNode binding(RuleSetBindingProjection v) {
        return obj("sequenceNo", number(v.sequenceNo()), "ruleSetCode", str(v.ruleSetCode()),
            "ruleSetVersionNo", number(v.ruleSetVersionNo()), "ruleSetContentHash", str(v.ruleSetContentHash()));
    }

    private JNode releaseInputField(ReleaseInputFieldProjection v) {
        return obj("inputFieldId", str(v.inputFieldId()), "pointVersionId", str(v.pointVersionId()),
            "fieldCode", str(v.fieldCode()), "valueType", str(v.valueType().getCode()),
            "requiredFlag", bool(v.requiredFlag()), "dataClassification", str(v.dataClassification().getCode()),
            "ruleReferenceFlag", bool(v.ruleReferenceFlag()), "actionParameterFlag", bool(v.actionParameterFlag()));
    }

    private JNode releaseBinding(ReleaseRuleSetBindingProjection v) {
        return obj("bindingId", str(v.bindingId()), "pointVersionId", str(v.pointVersionId()),
            "ruleSetVersionId", str(v.ruleSetVersionId()), "sequenceNo", number(v.sequenceNo()));
    }

    private JNode configuredReleaseRuleSet(ReleaseRuleSetVersionProjection v) {
        return obj("ruleSetVersionId", str(v.ruleSetVersionId()), "decisionPointId", str(v.decisionPointId()),
            "ruleSetCode", str(v.ruleSetCode()), "versionNo", number(v.versionNo()), "contentHash", str(v.contentHash()),
            "contentFormat", str(v.contentFormat()));
    }

    private JNode configuredReleaseRule(ReleaseRuleProjection v) {
        return obj("ruleId", str(v.ruleId()), "ruleSetVersionId", str(v.ruleSetVersionId()),
            "ruleCode", str(v.ruleCode()), "priorityNo", number(v.priorityNo()), "enabledFlag", bool(v.enabledFlag()),
            "reasonCode", nullable(v.reasonCode()), "reasonMessage", nullable(v.reasonMessage()));
    }

    private JNode releaseRuleSet(ReleaseRuleSetVersionProjection v) {
        return obj("ruleSetVersionId", str(v.ruleSetVersionId()), "decisionPointId", str(v.decisionPointId()),
            "ruleSetCode", str(v.ruleSetCode()), "versionNo", number(v.versionNo()), "contentHash", str(v.contentHash()));
    }

    private JNode releaseRule(ReleaseRuleProjection v) {
        return obj("ruleId", str(v.ruleId()), "ruleSetVersionId", str(v.ruleSetVersionId()),
            "ruleCode", str(v.ruleCode()), "priorityNo", number(v.priorityNo()),
            "enabledFlag", bool(v.enabledFlag()), "reasonCode", nullable(v.reasonCode()));
    }

    private JNode releaseNode(ReleaseNodeProjection v) {
        return obj("nodeId", str(v.nodeId()), "ruleId", str(v.ruleId()), "parentNodeId", nullable(v.parentNodeId()),
            "nodeType", str(v.nodeType().getCode()),
            "groupOperator", v.groupOperator() == null ? NullNode.INSTANCE : str(v.groupOperator().getCode()),
            "comparisonOperator", v.comparisonOperator() == null ? NullNode.INSTANCE : str(v.comparisonOperator().getCode()),
            "sequenceNo", number(v.sequenceNo()));
    }

    private JNode releaseOperand(ReleaseOperandProjection v) {
        return obj("operandId", str(v.operandId()), "nodeId", str(v.nodeId()),
            "operandRole", str(v.operandRole().getCode()), "valueSequence", number(v.valueSequence()),
            "sourceType", str(v.sourceType().getCode()), "featureVersionId", nullable(v.featureVersionId()),
            "inputFieldId", nullable(v.inputFieldId()), "valueType", str(v.valueType().getCode()),
            "stringValue", nullable(v.stringValue()), "numberValue", nullableDecimal(v.numberValue()),
            "booleanValue", v.booleanValue() == null ? NullNode.INSTANCE : bool(v.booleanValue()),
            "datetimeValue", nullableDatetime(v.datetimeValue()), "durationValue", nullableDuration(v.durationValue()));
    }

    private JNode releaseAction(ReleaseActionVersionProjection v) {
        return obj("actionVersionId", str(v.actionVersionId()), "actionCode", str(v.actionCode()),
            "actionCategory", str(v.actionCategory().getCode()), "versionNo", number(v.versionNo()),
            "handlerCode", str(v.handlerCode()), "implementationVersion", str(v.implementationVersion()),
            "implementationHash", str(v.implementationHash()), "terminalFlag", bool(v.terminalFlag()),
            "decisionCode", str(v.decisionCode().getCode()), "mergePolicy", str(v.mergePolicy().getCode()),
            "shadowSupportedFlag", bool(v.shadowSupportedFlag()));
    }

    private JNode releaseRuleAction(ReleaseRuleActionProjection v) {
        return obj("ruleActionId", str(v.ruleActionId()), "ruleId", str(v.ruleId()),
            "actionVersionId", str(v.actionVersionId()), "sequenceNo", number(v.sequenceNo()));
    }

    private static JNode nullable(String value) { return value == null ? NullNode.INSTANCE : str(value); }
    private static JNode nullableDecimal(String value) { return value == null ? NullNode.INSTANCE : str(decimal(value)); }
    private static JNode nullableDatetime(String value) { return value == null ? NullNode.INSTANCE : str(datetime(value)); }
    private static JNode nullableDuration(String value) { return value == null ? NullNode.INSTANCE : str(duration(value)); }

    @SafeVarargs
    private static void requireEmpty(List<UnsupportedProjection>... lists) {
        for (List<UnsupportedProjection> list : lists) if (!list.isEmpty()) throw invalid("首期不支持该配置投影");
    }

    private static String decimal(String raw) {
        try {
            if (!raw.matches("-?(?:0|[1-9][0-9]{0,15})(?:\\.[0-9]{1,8})?")) throw invalid("数值格式无效");
            BigDecimal value = new BigDecimal(raw).setScale(8);
            if (value.signum() == 0 && raw.startsWith("-") || value.abs().compareTo(
                new BigDecimal("9999999999999999.99999999")) > 0) throw invalid("数值超出范围");
            return value.toPlainString();
        } catch (ArithmeticException | NumberFormatException ex) { throw invalid("数值格式无效"); }
    }

    private static String datetime(String raw) {
        return CanonicalUtcMicrosecond.normalize(raw);
    }

    private static String duration(String raw) {
        if (!raw.matches("0|[1-9][0-9]*")) throw invalid("时长格式无效");
        try { Long.parseLong(raw); return raw; } catch (NumberFormatException ex) { throw invalid("时长格式无效"); }
    }

    private static Long numericId(String value) {
        if (value == null || !value.matches("[1-9][0-9]*")) throw invalid("标识格式无效");
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw invalid("标识格式无效");
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalid("标识格式无效");
        }
    }

    private static <T> List<T> sortId(List<T> values, java.util.function.Function<T, String> id,
                                      java.util.function.Function<T, JNode> projector) {
        return sorted(values, Comparator.comparing(id, NUMERIC_ID)
            .thenComparing(value -> canonicalBytes(projector.apply(value)), RiskDecisionCanonicalizerImpl::compareBytes));
    }

    private static <T> List<T> sorted(List<T> values, Comparator<T> comparator) {
        ArrayList<T> copy = new ArrayList<>(values); copy.sort(comparator); return List.copyOf(copy);
    }

    private static String trim(String value) { return normalize(value).strip(); }

    private static String normalize(String value) {
        if (value == null) throw invalid("字符串投影无效");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw invalid("字符串含无效字符");
            } else if (Character.isLowSurrogate(c)) throw invalid("字符串含无效字符");
        }
        return Normalizer.normalize(value, Normalizer.Form.NFC);
    }

    private static StringNode str(String value) { return new StringNode(normalize(value)); }
    private static NumberNode number(long value) {
        if (value < 0) throw invalid("整数投影无效");
        return new NumberNode(Long.toString(value));
    }
    private static BooleanNode bool(boolean value) { return value ? BooleanNode.TRUE : BooleanNode.FALSE; }
    private static ArrayNode array(List<JNode> values) { return new ArrayNode(List.copyOf(values)); }

    private static ObjectNode obj(Object... alternating) {
        ArrayList<Property> properties = new ArrayList<>();
        for (int i = 0; i < alternating.length; i += 2) {
            properties.add(new Property((String) alternating[i], (JNode) alternating[i + 1]));
        }
        return new ObjectNode(properties);
    }

    private static CanonicalHash hash(String schema, JNode node) {
        byte[] bytes = canonicalBytes(node);
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return new CanonicalHash(schema, bytes, digest);
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static InvalidParameterException invalid(String message) { return new InvalidParameterException(message); }

    private static CanonicalHash project(Supplier<CanonicalHash> operation) {
        try {
            return operation.get();
        } catch (JsonCanonicalizationException exception) {
            throw exception;
        } catch (InvalidParameterException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid("规范投影无效");
        }
    }

    private static byte[] canonicalBytes(JNode node) {
        StringBuilder source = new StringBuilder();
        node.write(source);
        return Rfc8785JsonCanonicalizer.canonicalizeObject(
            source.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static int compareBytes(byte[] left, byte[] right) {
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            int value = Integer.compare(Byte.toUnsignedInt(left[i]), Byte.toUnsignedInt(right[i]));
            if (value != 0) return value;
        }
        return Integer.compare(left.length, right.length);
    }

    private sealed interface JNode permits ObjectNode, ArrayNode, StringNode, NumberNode, BooleanNode, NullNode {
        void write(StringBuilder out);
    }

    private record Property(String name, JNode value) { }

    private record ObjectNode(List<Property> properties) implements JNode {
        private ObjectNode { properties = List.copyOf(properties); }
        @Override public void write(StringBuilder out) {
            out.append('{');
            for (int i = 0; i < properties.size(); i++) {
                if (i > 0) out.append(','); writeString(out, properties.get(i).name()); out.append(':');
                properties.get(i).value().write(out);
            }
            out.append('}');
        }
    }

    private record ArrayNode(List<JNode> values) implements JNode {
        @Override public void write(StringBuilder out) {
            out.append('['); for (int i = 0; i < values.size(); i++) { if (i > 0) out.append(','); values.get(i).write(out); }
            out.append(']');
        }
    }

    private record StringNode(String value) implements JNode { @Override public void write(StringBuilder out) { writeString(out, value); } }
    private record NumberNode(String value) implements JNode { @Override public void write(StringBuilder out) { out.append(value); } }
    private enum BooleanNode implements JNode {
        TRUE,

        FALSE;

        @Override public void write(StringBuilder out) { out.append(this == TRUE); }
    }
    private enum NullNode implements JNode { INSTANCE; @Override public void write(StringBuilder out) { out.append("null"); } }

    private static void writeString(StringBuilder out, String raw) {
        String value = normalize(raw); out.append('"');
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset); offset += Character.charCount(cp);
            switch (cp) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (cp < 0x20) out.append(String.format("\\u%04x", cp)); else out.appendCodePoint(cp); }
            }
        }
        out.append('"');
    }
}
