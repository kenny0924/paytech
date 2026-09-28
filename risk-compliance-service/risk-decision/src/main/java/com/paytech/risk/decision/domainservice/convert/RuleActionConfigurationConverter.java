package com.paytech.risk.decision.domainservice.convert;

import com.paytech.risk.decision.core.dal.dataobject.ActionParameterDefinition;
import com.paytech.risk.decision.core.dal.dataobject.ActionVersion;
import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor.InputFieldDescriptor;
import com.paytech.risk.decision.core.dal.dataobject.RuleAction;
import com.paytech.risk.decision.core.dal.dataobject.RuleActionParameter;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.ActionKey;
import com.paytech.risk.decision.domain.authoring.RuleActionConfigurationCatalog;
import com.paytech.risk.decision.domain.authoring.RuleActionConfigurationCatalog.ConfigurationAction;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.ExecutionVersionStatus;
import com.paytech.risk.decision.domain.enums.OperandSource;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.AdditionalActionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ParameterProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.StringValue;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.ActionParameter;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.AdditionalAction;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.StringLiteral;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/** 在已读出的结构化参数与配置模型之间转换；不访问数据库或执行动作。 */
public final class RuleActionConfigurationConverter {
    private RuleActionConfigurationConverter() { }

    public static void verifyAction(ActionVersion value, boolean current) {
        var definition = RuleActionConfigurationCatalog.require(new ActionKey(value.actionCode(), value.versionNo()));
        var expected = definition.descriptor();
        if (value.actionVersionId() != definition.actionVersionId() || !expected.actionName().equals(value.actionName())
            || value.actionCategory() != expected.actionCategory() || value.terminalFlag() || value.decisionCode() != null
            || value.handlerCode() != null || value.implementationVersion() != null || value.implementationHash() != null
            || value.mergePolicy() != expected.mergePolicy() || value.shadowSupportedFlag()
            || (value.status() != ExecutionVersionStatus.CONFIGURATION_ONLY
                && (current || value.status() != ExecutionVersionStatus.RETIRED))) throw corrupt();
    }

    public static List<ActionParameterDefinition> definitions(ConfigurationAction action, Instant time) {
        return List.of(new ActionParameterDefinition(action.targetDefinitionId(), action.actionVersionId(),
            "SUBJECT_ID", DecisionValueType.STRING, true, OperandSource.CONTEXT, true, time),
            new ActionParameterDefinition(action.valueDefinitionId(), action.actionVersionId(), action.parameterCode(),
                action.valueType(), true, OperandSource.LITERAL, false, time));
    }

    public static void verifyDefinitions(List<ActionParameterDefinition> actual, List<ConfigurationAction> actions) {
        var expected = actions.stream().flatMap(action -> definitions(action, Instant.EPOCH).stream()).toList();
        if (actual.size() != expected.size()) throw corrupt();
        for (var value : expected) {
            long matches = actual.stream().filter(row -> row.parameterDefinitionId() == value.parameterDefinitionId()
                && row.actionVersionId() == value.actionVersionId() && row.parameterCode().equals(value.parameterCode())
                && row.valueType() == value.valueType() && row.requiredFlag() == value.requiredFlag()
                && row.allowedSourceType() == value.allowedSourceType() && row.targetParameterFlag() == value.targetParameterFlag()).count();
            if (matches != 1) throw corrupt();
        }
    }

    public static List<RuleActionParameter> materialize(AdditionalAction value, long bindingId,
        Map<String, InputFieldDescriptor> fields, LongSupplier identifiers, Instant time) {
        var definition = RuleActionConfigurationCatalog.require(value.action());
        var target = RuleActionConfigurationCatalog.requireTarget(value.targetBindingCode());
        List<RuleActionParameter> result = new ArrayList<>();
        if (value.subjectVariableCode() != null) {
            result.add(new RuleActionParameter(identifiers.getAsLong(), bindingId, definition.targetDefinitionId(), 1,
                OperandSource.VARIABLE, null, null, null, null, null, null, null, time, value.subjectVariableCode()));
        } else {
            var field = fields.get(target.requiredFields().getFirst().fieldCode());
            if (field == null || !field.actionParameterFlag()) throw corrupt();
            result.add(new RuleActionParameter(identifiers.getAsLong(), bindingId, definition.targetDefinitionId(), 1,
                OperandSource.CONTEXT, null, field.fieldCode(), null, null, null, null, null, time));
        }
        for (var parameter : value.parameters()) {
            String literal = ((StringLiteral) parameter.literalValue()).value();
            boolean duration = parameter.valueType() == DecisionValueType.DURATION;
            result.add(new RuleActionParameter(identifiers.getAsLong(), bindingId, definition.valueDefinitionId(),
                parameter.valueSequence(), OperandSource.LITERAL, null, null, duration ? null : literal,
                null, null, null, duration ? Long.valueOf(literal) : null, time));
        }
        return List.copyOf(result);
    }

    public static AdditionalAction restore(RuleAction binding, ActionVersion action,
        List<RuleActionParameter> parameters, Map<String, InputFieldDescriptor> fields, int sequenceNo, boolean variableFormat) {
        var definition = RuleActionConfigurationCatalog.require(new ActionKey(action.actionCode(), action.versionNo()));
        var target = RuleActionConfigurationCatalog.requireTarget(binding.targetBindingCode());
        var rows = parameters.stream().filter(row -> row.ruleActionId() == binding.ruleActionId())
            .sorted(Comparator.comparingInt(RuleActionParameter::valueSequence)).toList();
        List<ActionParameter> values = new ArrayList<>();
        int targets = 0;
        String subjectVariableCode = null;
        for (var row : rows) {
            if (row.featureVersionId() != null || row.numberValue() != null || row.booleanValue() != null
                || row.datetimeValue() != null) throw corrupt();
            if (row.parameterDefinitionId() == definition.targetDefinitionId()) {
                targets++;
                if (row.valueSequence() != 1 || row.stringValue() != null || row.durationValue() != null) throw corrupt();
                if (variableFormat) {
                    if (row.sourceType() != OperandSource.VARIABLE || row.fieldCode() != null) throw corrupt();
                    com.paytech.risk.decision.domain.variable.VariableDefinitionValidator.code(row.variableCode());
                    subjectVariableCode = row.variableCode();
                } else {
                    var field = fields.get(row.fieldCode());
                    if (row.sourceType() != OperandSource.CONTEXT || row.variableCode() != null || field == null
                        || !field.fieldCode().equals(target.requiredFields().getFirst().fieldCode()) || !field.actionParameterFlag()) throw corrupt();
                }
            } else {
                if (row.parameterDefinitionId() != definition.valueDefinitionId() || row.sourceType() != OperandSource.LITERAL
                    || row.fieldCode() != null || row.variableCode() != null) throw corrupt();
                boolean duration = definition.valueType() == DecisionValueType.DURATION;
                if (duration ? row.stringValue() != null || row.durationValue() == null
                    : row.stringValue() == null || row.durationValue() != null) throw corrupt();
                values.add(new ActionParameter(definition.parameterCode(), row.valueSequence(), OperandSource.LITERAL,
                    null, definition.valueType(), new StringLiteral(duration ? Long.toString(row.durationValue()) : row.stringValue())));
            }
        }
        if (targets != 1) throw corrupt();
        return new AdditionalAction(sequenceNo, new ActionKey(action.actionCode(), action.versionNo()), binding.targetBindingCode(), values, subjectVariableCode);
    }

    public static List<AdditionalActionProjection> projections(List<AdditionalAction> actions) {
        if (actions == null) return List.of();
        return actions.stream().map(action -> new AdditionalActionProjection(action.sequenceNo(), action.action().actionCode(),
            action.action().versionNo(), action.targetBindingCode(), action.parameters().stream().map(parameter ->
                new ParameterProjection(parameter.parameterCode(), parameter.valueSequence(), parameter.sourceType().getCode(),
                    parameter.fieldCode(), parameter.valueType(), new StringValue(((StringLiteral) parameter.literalValue()).value())))
                .toList(), action.subjectVariableCode())).toList();
    }

    private static IllegalStateException corrupt() { return new IllegalStateException("动作配置持久化闭包损坏"); }
}
