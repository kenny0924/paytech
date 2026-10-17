package com.paytech.operations.console.bff.configuration;

import java.util.HashMap;
import java.util.Map;

import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.CreateRuleSetVersionRequestBody;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.FreezeRuleSetVersionRequestBody;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.DeleteRuleSetRequestBody;
import com.paytech.operations.console.bff.service.model.RiskTestCandidateModels.CreateTestCandidateRequestBody;
import com.paytech.operations.console.bff.service.model.RiskTestCandidateModels.ValidateTestCandidateRequestBody;
import org.springframework.core.ResolvableType;
import tools.jackson.databind.JsonNode;

/** 校验 Risk 命令的完整属性与 JSON 类型；显式 null 留给 Jakarta，业务语义留给 Risk。 */
final class RiskRequestShapeValidator {
    private static final Shape TEXT = nullable(JsonNode::isString);
    private static final Shape INTEGER = nullable(node -> node.isIntegralNumber() && node.canConvertToInt());
    private static final Shape BOOLEAN = nullable(JsonNode::isBoolean);
    private static final Shape LITERAL = nullable(node -> node.isString() || node.isBoolean());
    private static final Shape OPERAND = object(fields(TEXT, "operandRole sourceType fieldCode valueType"),
        fields(INTEGER, "valueSequence"), fields(LITERAL, "literalValue"));
    private static final Shape NODE = object(fields(TEXT, "nodeKey parentNodeKey nodeType groupOperator comparisonOperator"),
        fields(INTEGER, "sequenceNo"), fields(array(OPERAND), "operands"));
    private static final Shape ACTION = object(fields(TEXT, "actionCode"), fields(INTEGER, "actionVersionNo"));
    private static final Shape PARAMETER = object(fields(TEXT, "parameterCode sourceType fieldCode valueType"),
        fields(INTEGER, "valueSequence"), fields(LITERAL, "literalValue"));
    private static final Shape ADDITIONAL_ACTION = object(fields(TEXT, "actionCode targetBindingCode"),
        fields(INTEGER, "sequenceNo actionVersionNo"), fields(array(PARAMETER), "parameters"));
    private static final Shape LEGACY_RULE = object(fields(TEXT, "ruleCode ruleName reasonCode"),
        fields(INTEGER, "priorityNo"), fields(BOOLEAN, "enabledFlag"),
        fields(array(NODE), "conditionNodes"), fields(ACTION, "action"));
    private static final Shape CONFIGURED_RULE = object(fields(TEXT, "ruleCode ruleName reasonCode reasonMessage"),
        fields(INTEGER, "priorityNo"), fields(BOOLEAN, "enabledFlag"), fields(array(NODE), "conditionNodes"),
        fields(ACTION, "action"), fields(array(ADDITIONAL_ACTION), "additionalActions"));
    private static final Shape RULE = node -> (CONFIGURED_RULE.valid(node) && node.path("additionalActions").isArray()) || LEGACY_RULE.valid(node);
    private static final Shape VARIABLE_OPERAND = object(fields(TEXT, "operandRole sourceType fieldCode valueType variableCode numericUnit currencyCode currencyVariableCode"),
        fields(INTEGER, "valueSequence"), fields(LITERAL, "literalValue"));
    private static final Shape VARIABLE_NODE = object(fields(TEXT, "nodeKey parentNodeKey nodeType groupOperator comparisonOperator"),
        fields(INTEGER, "sequenceNo"), fields(array(VARIABLE_OPERAND), "operands"));
    private static final Shape VARIABLE_ACTION = object(fields(TEXT, "actionCode targetBindingCode subjectVariableCode"),
        fields(INTEGER, "sequenceNo actionVersionNo"), fields(array(PARAMETER), "parameters"));
    private static final Shape VARIABLE_RULE = object(fields(TEXT, "ruleCode ruleName reasonCode reasonMessage"),
        fields(INTEGER, "priorityNo"), fields(BOOLEAN, "enabledFlag"), fields(array(VARIABLE_NODE), "conditionNodes"),
        fields(ACTION, "action"), fields(array(VARIABLE_ACTION), "additionalActions"));
    private static final Shape VARIABLE_CREATE = object(
        fields(TEXT, "pointCode ruleSetCode ruleSetName baseRuleSetVersionId baseContentHash changeReason operandFormat"),
        fields(array(VARIABLE_RULE), "rules"));

    private static final Map<Class<?>, Shape> ROOTS = Map.of(
        CreateRuleSetVersionRequestBody.class, object(
            fields(TEXT, "pointCode ruleSetCode ruleSetName baseRuleSetVersionId baseContentHash changeReason"),
            fields(array(RULE), "rules")),
        FreezeRuleSetVersionRequestBody.class, object(fields(TEXT, "expectedContentHash")),
        DeleteRuleSetRequestBody.class, object(fields(TEXT, "expectedVersion")),
        CreateTestCandidateRequestBody.class, object(fields(TEXT, "ruleSetVersionId expectedRuleSetContentHash")),
        ValidateTestCandidateRequestBody.class, object(fields(INTEGER, "expectedVersion")),
        com.paytech.operations.console.bff.service.model.risklist.CreateRiskListRequestBody.class,
        object(fields(TEXT, "listCode listName subjectType purpose")),
        com.paytech.operations.console.bff.service.model.risklist.AddRiskListEntryRequestBody.class,
        object(fields(TEXT, "subjectValue effectiveFrom effectiveTo reason")),
        com.paytech.operations.console.bff.service.model.risklist.DeactivateRiskListEntryRequestBody.class,
        object(fields(TEXT, "reason")),
        com.paytech.operations.console.bff.service.model.riskrelease.CreateExecutionReleaseRequestBody.class,
        object(fields(TEXT, "pointCode flowVersionId")),
        com.paytech.operations.console.bff.service.model.riskrelease.StartShadowRunRequestBody.class,
        object(fields(INTEGER, "sampleTarget")));

    boolean supports(ResolvableType type) {
        return ROOTS.containsKey(type.getType());
    }

    void validate(ResolvableType type, JsonNode root) {
        Shape shape = ROOTS.get(type.getType());
        if (type.resolve() == CreateRuleSetVersionRequestBody.class && root != null && root.hasNonNull("operandFormat")) {
            if (!"VARIABLE_REFERENCE".equals(root.path("operandFormat").asString())) throw new IllegalArgumentException("操作数格式无效");
            shape = VARIABLE_CREATE;
        }
        if (type.resolve() == CreateRuleSetVersionRequestBody.class && root != null && !root.hasNonNull("operandFormat")) {
            root = legacyShape(root);
        }
        if (shape == null || root == null || !root.isObject() || !shape.valid(root)) {
            throw new IllegalArgumentException("Risk 请求不符合 JSON 协议");
        }
    }

    /** DTO 新增的空字段不改变旧命令语义；非空变量字段仍要求显式新格式。 */
    private static JsonNode legacyShape(JsonNode root) {
        JsonNode copy = root.deepCopy();
        removeNull(copy, "operandFormat");
        for (var rule : copy.path("rules")) {
            for (var action : rule.path("additionalActions")) removeNull(action, "subjectVariableCode");
            for (var node : rule.path("conditionNodes")) for (var operand : node.path("operands")) {
                for (String field : java.util.List.of("variableCode", "numericUnit", "currencyCode", "currencyVariableCode")) removeNull(operand, field);
            }
        }
        return copy;
    }

    private static void removeNull(JsonNode node, String field) {
        if (node instanceof tools.jackson.databind.node.ObjectNode object && object.has(field) && object.get(field).isNull()) object.remove(field);
    }

    private static Shape nullable(Shape value) {
        return node -> node.isNull() || value.valid(node);
    }

    private static Shape array(Shape item) {
        return nullable(node -> {
            if (!node.isArray()) return false;
            for (JsonNode value : node) {
                if (!item.valid(value)) return false;
            }
            return true;
        });
    }

    @SafeVarargs
    private static Shape object(Map<String, Shape>... groups) {
        Map<String, Shape> properties = new HashMap<>();
        for (var group : groups) properties.putAll(group);
        Map<String, Shape> expected = Map.copyOf(properties);
        return nullable(node -> {
            if (!node.isObject() || !node.propertyNames().equals(expected.keySet())) return false;
            for (var property : expected.entrySet()) {
                if (!property.getValue().valid(node.get(property.getKey()))) return false;
            }
            return true;
        });
    }

    private static Map<String, Shape> fields(Shape shape, String names) {
        Map<String, Shape> properties = new HashMap<>();
        for (String name : names.split(" ")) properties.put(name, shape);
        return Map.copyOf(properties);
    }

    @FunctionalInterface
    private interface Shape {
        boolean valid(JsonNode node);
    }
}
