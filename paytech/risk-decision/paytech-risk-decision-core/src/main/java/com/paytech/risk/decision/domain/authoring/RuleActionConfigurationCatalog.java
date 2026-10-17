package com.paytech.risk.decision.domain.authoring;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor.DecisionActionDescriptor;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor.InputFieldDescriptor;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.ActionKey;
import com.paytech.risk.decision.domain.enums.ActionCategory;
import com.paytech.risk.decision.domain.enums.ConfigurationStatus;
import com.paytech.risk.decision.domain.enums.DataClassification;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.MergePolicy;
import com.paytech.risk.decision.domain.enums.OperandSource;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.ActionParameter;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.AdditionalAction;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.StringLiteral;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/** 工程拥有的配置定义。没有运行实现指纹，不能作为执行注册表使用。 */
public final class RuleActionConfigurationCatalog {

    public static final int MAX_ACTIONS = 16;
    public static final int MAX_TAGS = 32;
    public static final long MAX_DURATION_MILLIS = 31_536_000_000L;
    public static final String CODE_PATTERN = "[A-Z][A-Z0-9_]{0,63}";

    private static final List<TargetBinding> TARGETS = List.of(new TargetBinding("CURRENT_CLIENT_V1",
        "当前 Client", "CLIENT", List.of(new TargetField("SUBJECT_ID", "client_id"))));
    private static final List<ConfigurationAction> ACTIONS = List.of(
        new ConfigurationAction(240004L, new DecisionActionDescriptor("APPLY_COOLDOWN", "冷却", 1,
            null, null, null, ActionCategory.RESTRICTION, false, null, MergePolicy.MAX, false,
            ConfigurationStatus.CONFIGURATION_ONLY), "DURATION", DecisionValueType.DURATION, 1),
        new ConfigurationAction(240005L, new DecisionActionDescriptor("ADD_RISK_TAG", "添加主体标签", 1,
            null, null, null, ActionCategory.TAG, false, null, MergePolicy.UNION, false,
            ConfigurationStatus.CONFIGURATION_ONLY), "TAG", DecisionValueType.STRING, MAX_TAGS));

    private RuleActionConfigurationCatalog() { }

    public static List<TargetBinding> targetBindings() { return TARGETS; }

    public static List<ConfigurationAction> actions() { return ACTIONS; }

    public static List<InputFieldDescriptor> inputFields() {
        return DecisionPointAuthoringDescriptor.legacyCheckoutPrepare().inputFields().stream()
            .filter(InputFieldDescriptor::actionParameterFlag).toList();
    }

    public static ConfigurationAction require(ActionKey key) {
        return ACTIONS.stream().filter(value -> value.descriptor().actionCode().equals(key.actionCode())
            && value.descriptor().versionNo() == key.versionNo()).findFirst()
            .orElseThrow(() -> invalid("附加动作版本不支持"));
    }

    public static TargetBinding requireTarget(String code) {
        return TARGETS.stream().filter(value -> value.bindingCode().equals(code)).findFirst()
            .orElseThrow(() -> invalid("当前决策点不支持该主体绑定"));
    }

    /** 验证连续顺序和参数矩阵；标签按代码去重排序，时长保持毫秒字符串。 */
    public static List<AdditionalAction> normalize(List<AdditionalAction> values) {
        if (values == null) return null;
        if (values.size() > MAX_ACTIONS) throw invalid("附加动作数量超限");
        List<AdditionalAction> sorted = values.stream().sorted(Comparator.comparingInt(AdditionalAction::sequenceNo))
            .toList();
        List<AdditionalAction> result = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            AdditionalAction value = sorted.get(index);
            if (value.sequenceNo() != index + 1 || value.action() == null) throw invalid("附加动作顺序无效");
            ConfigurationAction definition = require(value.action());
            requireTarget(value.targetBindingCode());
            result.add(new AdditionalAction(value.sequenceNo(), value.action(), value.targetBindingCode(),
                normalizeParameters(definition, value.parameters()), value.subjectVariableCode()));
        }
        return List.copyOf(result);
    }

    private static List<ActionParameter> normalizeParameters(ConfigurationAction definition,
                                                             List<ActionParameter> parameters) {
        if (parameters == null || parameters.isEmpty() || parameters.size() > definition.maxValues())
            throw invalid("附加动作参数数量无效");
        List<ActionParameter> sorted = parameters.stream().sorted(Comparator.comparingInt(ActionParameter::valueSequence))
            .toList();
        TreeSet<String> values = new TreeSet<>();
        for (int index = 0; index < sorted.size(); index++) {
            ActionParameter parameter = sorted.get(index);
            if (!definition.parameterCode().equals(parameter.parameterCode()) || parameter.valueSequence() != index + 1
                || parameter.sourceType() != OperandSource.LITERAL || parameter.fieldCode() != null || parameter.variableCode() != null
                || parameter.valueType() != definition.valueType()
                || !(parameter.literalValue() instanceof StringLiteral text)) throw invalid("附加动作参数矩阵无效");
            String literal = text.value();
            if (definition.valueType() == DecisionValueType.DURATION) validateDuration(literal);
            else if (literal == null || !literal.matches(CODE_PATTERN)) throw invalid("标签代码格式无效");
            values.add(literal);
        }
        List<ActionParameter> result = new ArrayList<>();
        for (String value : values) result.add(new ActionParameter(definition.parameterCode(), result.size() + 1,
            OperandSource.LITERAL, null, definition.valueType(), new StringLiteral(value)));
        return List.copyOf(result);
    }

    private static void validateDuration(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,10}") || Long.parseLong(value) > MAX_DURATION_MILLIS)
            throw invalid("冷却时长必须为正毫秒数且不超过 365 天");
    }

    private static BizCheckFailException invalid(String message) { return new BizCheckFailException(message); }

    public record TargetField(String role, String fieldCode) { }

    public record TargetBinding(String bindingCode, String displayName, String subjectType,
                                List<TargetField> requiredFields) {
        public TargetBinding { requiredFields = List.copyOf(requiredFields); }
    }

    public record ConfigurationAction(long actionVersionId, DecisionActionDescriptor descriptor,
                                      String parameterCode, DecisionValueType valueType, int maxValues) {
        public long targetDefinitionId() { return actionVersionId * 10 + 1; }
        public long valueDefinitionId() { return actionVersionId * 10 + 2; }
    }
}
