package com.paytech.operations.console.bff.configuration;

import com.paytech.risk.api.decision.DecisionRuleSetManagementModels;
import com.paytech.risk.api.decision.DecisionReleaseManagementModels;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import tools.jackson.databind.JsonNode;

import com.paytech.foundation.web.paging.PagingResponseBody;

/** 在 Owner 绑定前保留缺键、Null 和原始 token 的区别；不解释规则或状态语义。 */
final class RiskOwnerResponseShapeValidator {
    private static final Shape TEXT = JsonNode::isString;
    private static final Shape BOOLEAN = JsonNode::isBoolean;
    private static final Shape INTEGER = node -> node.isIntegralNumber() && node.canConvertToInt();
    private static final Shape TOTAL = node -> node.isIntegralNumber() && node.canConvertToLong() && node.longValue() >= 0;
    private static final Shape NULLABLE_TEXT = nullable(TEXT);
    private static final Shape LITERAL = node -> node.isNull() || node.isString() || node.isBoolean();
    private static final Shape EMPTY_ARRAY = node -> node.isArray() && node.isEmpty();

    private static final Shape OPERAND = object(
        fields(TEXT, "operandRole valueType sourceType"), fields(INTEGER, "valueSequence"),
        fields(NULLABLE_TEXT, "fieldCode variableCode numericUnit currencyCode currencyVariableCode"), fields(LITERAL, "literalValue"));
    private static final Shape NODE = object(
        fields(TEXT, "nodeKey nodeType"), fields(INTEGER, "sequenceNo"),
        fields(NULLABLE_TEXT, "parentNodeKey groupOperator comparisonOperator"), fields(array(OPERAND), "operands"));
    private static final Shape RULE_ACTION = object(fields(TEXT, "actionCode"), fields(INTEGER, "actionVersionNo"));
    private static final Shape PARAMETER = object(fields(TEXT, "parameterCode sourceType valueType"),
        fields(NULLABLE_TEXT, "fieldCode"), fields(INTEGER, "valueSequence"), fields(LITERAL, "literalValue"));
    private static final Shape ADDITIONAL_ACTION = object(fields(TEXT, "actionCode targetBindingCode"),
        fields(INTEGER, "sequenceNo actionVersionNo"), fields(array(PARAMETER), "parameters"), fields(NULLABLE_TEXT, "subjectVariableCode"));
    private static final Shape RULE = object(
        fields(TEXT, "ruleCode ruleName"), fields(INTEGER, "priorityNo"), fields(BOOLEAN, "enabledFlag"),
        fields(NULLABLE_TEXT, "reasonCode reasonMessage"), fields(array(NODE), "conditionNodes"),
        fields(nullable(RULE_ACTION), "action"), fields(array(ADDITIONAL_ACTION), "additionalActions"));
    private static final Shape POINT = object(
        fields(TEXT, "pointCode pointName businessDomain inputContractCode outputContractCode defaultDecision "
            + "evaluationStrategy failureStrategy status"), fields(INTEGER, "inputContractVersion timeoutMillis"));
    private static final Shape FIELD = object(
        fields(TEXT, "fieldCode valueType dataClassification variableCode"), fields(BOOLEAN, "requiredFlag ruleReferenceFlag actionParameterFlag"));
    private static final Shape AUTHORING_ACTION = object(
        fields(TEXT, "actionCode actionName actionCategory mergePolicy status"),
        fields(NULLABLE_TEXT, "handlerCode implementationVersion implementationHash decisionCode"), fields(INTEGER, "versionNo"), fields(BOOLEAN, "terminalFlag shadowSupportedFlag"));
    private static final Shape TARGET_FIELD = object(fields(TEXT, "role fieldCode"));
    private static final Shape TARGET_BINDING = object(fields(TEXT, "bindingCode displayName subjectType"),
        fields(array(TARGET_FIELD), "requiredFields"), fields(NULLABLE_TEXT, "subjectSemanticRole"));
    private static final Shape PARAMETER_DESCRIPTOR = object(
        fields(TEXT, "actionCode parameterCode valueType allowedSourceType"), fields(INTEGER, "actionVersionNo maxValues"),
        fields(BOOLEAN, "requiredFlag targetParameterFlag"), fields(NULLABLE_TEXT, "minimumValue maximumValue valuePattern"));
    private static final Shape AUTHORING = object(
        fields(TEXT, "descriptorCode"), fields(POINT, "point"), fields(array(FIELD), "fields"),
        fields(array(TEXT), "operators"), fields(array(AUTHORING_ACTION), "actions"),
        fields(EMPTY_ARRAY, "features obligations limitBindings"), fields(array(PARAMETER_DESCRIPTOR), "actionParameters"),
        fields(array(TARGET_BINDING), "targetBindings"), fields(TEXT, "operandFormat"),
        fields(RiskVariableResponseShape::queryContext, "variableQueryContext"));
    private static final Map<String, Shape> RULE_SET_FIELDS = combine(
        fields(TEXT, "ruleSetVersionId pointCode ruleSetCode ruleSetName status contentHash "
            + "createdBy changeReason createTime"), fields(INTEGER, "versionNo"), fields(NULLABLE_TEXT, "pointVersionId"), fields(NULLABLE_TEXT, "frozenBy frozenTime"));
    private static final Shape RULE_SET_ITEM = object(RULE_SET_FIELDS, fields(TEXT, "ruleSetId managementVersion"), fields(BOOLEAN, "inUse"));
    private static final Shape RULE_SET_DETAIL = object(RULE_SET_FIELDS, fields(array(RULE), "rules"),
        fields(NULLABLE_TEXT, "operandFormat valuesAsOf"), fields(array(RiskVariableResponseShape::summary), "currentVariables"),
        fields(array(RiskVariableResponseShape::issue), "variableIssues"));
    private static final Shape CREATE_RULE_SET = object(
        fields(TEXT, "ruleSetVersionId ruleSetCode status contentHash createdBy createTime"), fields(INTEGER, "versionNo"), fields(NULLABLE_TEXT, "pointVersionId"));
    private static final Shape FREEZE_RULE_SET = object(
        fields(TEXT, "ruleSetVersionId ruleSetCode status contentHash frozenBy frozenTime"), fields(INTEGER, "versionNo"), fields(NULLABLE_TEXT, "pointVersionId"));

    private static final Map<String, Shape> CANDIDATE_FIELDS = combine(
        fields(TEXT, "releaseId decisionPointId pointCode pointVersionId ruleSetVersionId environmentCode status "
            + "contentHash createdBy createTime"), fields(INTEGER, "releaseNo version"));
    private static final Shape CANDIDATE_ITEM = object(CANDIDATE_FIELDS, fields(nullable(BOOLEAN), "allStaticChecksPassed"));
    private static final Shape COMPOSITION = object(
        fields(TEXT, "decisionPointId pointCode pointVersionId pointVersionContentHash ruleSetVersionId ruleSetCode ruleSetContentHash"),
        fields(INTEGER, "pointVersionNo ruleSetVersionNo"));
    private static final Map<String, Shape> INITIAL_STATE_FIELDS = combine(
        fields(TEXT, "auditEventId eventTime toStatus reasonCode triggerType actorId"),
        fields(NULLABLE_TEXT, "fromStatus"), fields(INTEGER, "aggregateVersion"));
    private static final Shape INITIAL_STATE = object(INITIAL_STATE_FIELDS);
    private static final Shape STATE_CHANGE = object(INITIAL_STATE_FIELDS, fields(TEXT, "triggerId"));
    private static final Map<String, Shape> CHECK_FIELDS = combine(
        fields(TEXT, "checkCode checkStatus severity"), fields(NULLABLE_TEXT, "errorCode errorSummary"));
    private static final Shape VALIDATION_CHECK = object(CHECK_FIELDS);
    private static final Shape STATIC_CHECK = object(CHECK_FIELDS, fields(TEXT, "auditEventId eventTime"));
    private static final Shape CANDIDATE_DETAIL = object(CANDIDATE_FIELDS,
        fields(nullable(BOOLEAN), "allStaticChecksPassed"), fields(BOOLEAN, "waitingForShadow"),
        fields(COMPOSITION, "frozenComposition"), fields(array(STATE_CHANGE), "stateChanges"), fields(array(STATIC_CHECK), "staticChecks"));
    private static final Shape CREATE_CANDIDATE = object(CANDIDATE_FIELDS, fields(INITIAL_STATE, "initialStateChange"));
    private static final Shape VALIDATE_CANDIDATE = object(
        fields(TEXT, "releaseId environmentCode status contentHash"), fields(INTEGER, "releaseNo version"),
        fields(BOOLEAN, "allStaticChecksPassed"), fields(array(VALIDATION_CHECK), "checks"));

    private static final Map<Type, Shape> ROOTS = Map.of(
        com.paytech.risk.api.decision.response.RuleAuthoringContextResponseBody.class, AUTHORING,
        new ParameterizedTypeReference<PagingResponseBody<com.paytech.risk.api.decision.response.RuleSetListItemResponseBody>>() {}.getType(),
        page(RULE_SET_ITEM),
        com.paytech.risk.api.decision.response.RuleSetDetailResponseBody.class, RULE_SET_DETAIL,
        com.paytech.risk.api.decision.response.CreateRuleSetVersionResponseBody.class, CREATE_RULE_SET,
        com.paytech.risk.api.decision.response.FreezeRuleSetVersionResponseBody.class, FREEZE_RULE_SET,
        com.paytech.risk.api.decision.response.DeleteRuleSetResponseBody.class,
        object(fields(TEXT, "ruleSetId status version deletedBy deletedTime")),
        new ParameterizedTypeReference<PagingResponseBody<com.paytech.risk.api.decision.response.TestReleaseListItemResponseBody>>() {}.getType(),
        page(CANDIDATE_ITEM),
        com.paytech.risk.api.decision.response.TestReleaseDetailResponseBody.class, CANDIDATE_DETAIL,
        com.paytech.risk.api.decision.response.CreateTestReleaseResponseBody.class, CREATE_CANDIDATE,
        com.paytech.risk.api.decision.response.ValidateTestReleaseResponseBody.class, VALIDATE_CANDIDATE);

    private static final Map<Type, Shape> ADDITIONAL_ROOTS = additionalRoots();
    /** 登记新增查询的精确响应字段集，不放宽已有规则接口的形状检查。 */
    private static Map<Type, Shape> additionalRoots() {
        Map<Type, Shape> roots = new HashMap<>();
        Shape reason = object(fields(TEXT, "reasonCode name defaultMessage status version createdBy updatedBy createTime updateTime"), fields(NULLABLE_TEXT, "description"));
        Shape tag = object(fields(TEXT, "tagCode name status version createdBy updatedBy createTime updateTime"), fields(NULLABLE_TEXT, "description"));
        Shape reference = object(fields(TEXT, "ruleSetId pointCode ruleSetCode ruleSetName ruleSetVersionId ruleCode ruleName referenceKind ruleSetStatus"),
            fields(INTEGER, "versionNo"), fields(nullable(INTEGER), "actionSequenceNo"), fields(NULLABLE_TEXT, "targetBindingCode"),
            fields(BOOLEAN, "isCurrentConfiguration"), fields(array(TEXT), "activeEnvironments"));
        Shape history = object(fields(TEXT, "ruleSetId ruleSetVersionId pointCode ruleSetCode ruleSetName status createdBy changeReason createTime"),
            fields(INTEGER, "versionNo"), fields(BOOLEAN, "isCurrent"));
        Shape release = object(fields(TEXT, "releaseId releaseNo environmentCode status pointCode pointVersionId ruleSetVersionId"),
            fields(INTEGER, "pointVersionNo versionNo"), fields(NULLABLE_TEXT, "activatedTime supersededTime"));
        roots.put(com.paytech.risk.api.decision.response.RejectionReasonResponseBody.class, reason);
        roots.put(com.paytech.risk.api.decision.response.SubjectTagResponseBody.class, tag);
        roots.put(new ParameterizedTypeReference<PagingResponseBody<com.paytech.risk.api.decision.response.RejectionReasonResponseBody>>() {}.getType(), page(reason));
        roots.put(new ParameterizedTypeReference<PagingResponseBody<com.paytech.risk.api.decision.response.SubjectTagResponseBody>>() {}.getType(), page(tag));
        roots.put(new ParameterizedTypeReference<PagingResponseBody<com.paytech.risk.api.decision.response.SubjectTagReferenceResponseBody>>() {}.getType(), page(reference));
        roots.put(com.paytech.risk.api.decision.response.DictionaryWriteResponseBody.class,
            object(fields(TEXT, "dictionaryType code version status changedBy changedTime")));
        roots.put(new ParameterizedTypeReference<PagingResponseBody<com.paytech.risk.api.decision.response.RuleSetHistoryItemResponseBody>>() {}.getType(), page(history));
        roots.put(com.paytech.risk.api.decision.response.RuleSetReleaseReferencesResponseBody.class,
            object(fields(TEXT, "ruleSetId"), fields(BOOLEAN, "inUse"), fields(page(release), "references")));
        Shape reasonReference = object(fields(TEXT, "pointCode referenceKind sourceType"),
            fields(NULLABLE_TEXT, "ruleSetId ruleSetCode ruleSetName ruleSetVersionId ruleCode ruleName ruleSetStatus targetBindingCode flowId flowCode flowName flowVersionId nodeKey nodeName flowStatus"),
            fields(nullable(INTEGER), "versionNo actionSequenceNo flowVersionNo"), fields(BOOLEAN, "isCurrentConfiguration"), fields(array(TEXT), "activeEnvironments"));
        roots.put(new ParameterizedTypeReference<PagingResponseBody<com.paytech.risk.api.decision.response.RejectionReasonReferenceResponseBody>>() {}.getType(), page(reasonReference));
        addFlowRoots(roots);
        return Map.copyOf(roots);
    }

    private static void addFlowRoots(Map<Type, Shape> roots) {
        Shape position = object(fields(TEXT, "x y"));
        Shape condition = node -> node.isObject() && node.propertyNames().equals(java.util.Set.of("schemaVersion", "root"))
            && INTEGER.valid(node.get("schemaVersion")) && java.util.Set.of(1, 2).contains(node.get("schemaVersion").intValue())
            && flowExpression(node.get("root"), 1, new int[]{0}, node.get("schemaVersion").intValue());
        Shape node = object(fields(TEXT, "nodeKey type name"), fields(position, "position"),
            fields(NULLABLE_TEXT, "ruleSetVersionId decisionCode reasonCode reasonMessage"));
        Shape edge = object(fields(TEXT, "edgeKey sourceNodeKey targetNodeKey type name"), fields(INTEGER, "priority"), fields(nullable(condition), "condition"));
        Shape graph = object(fields(INTEGER, "schemaVersion"), fields(array(node), "nodes"), fields(array(edge), "edges"));
        Shape reference = object(fields(TEXT, "nodeKey ruleSetId ruleSetCode ruleSetName ruleSetVersionId ruleSetStatus versionStatus"),
            fields(INTEGER, "versionNo ruleCount"));
        Shape item = object(fields(TEXT, "flowId pointCode flowCode flowName currentFlowVersionId version updatedBy updateTime"), fields(INTEGER, "currentVersionNo ruleSetNodeCount"));
        Shape history = object(fields(TEXT, "flowVersionId flowName createdBy createTime changeReason"), fields(INTEGER, "versionNo"));
        Shape detail = object(fields(TEXT, "flowId pointCode flowCode flowStatus currentFlowVersionId version flowVersionId flowName inputContractCode createdBy createTime changeReason"),
            fields(INTEGER, "currentVersionNo versionNo inputContractVersion"), fields(graph, "graph"), fields(array(reference), "ruleSetReferences"), fields(TEXT, "valuesAsOf"),
            fields(array(RiskVariableResponseShape::summary), "currentVariables"), fields(array(RiskVariableResponseShape::issue), "variableIssues"));
        Shape fields = object(fields(TEXT, "fieldCode label valueType literalDomain variableCode"), fields(BOOLEAN, "required"), fields(array(TEXT), "operators"));
        Shape output = object(fields(TEXT, "nodeType fieldCode label valueType variableCode"), fields(BOOLEAN, "required"), fields(array(TEXT), "operators"));
        roots.put(com.paytech.risk.api.decision.response.FlowAuthoringContextResponseBody.class,
            object(fields(array(object(fields(TEXT, "pointCode pointName"))), "supportedPoints"),
                fields(TEXT, "pointCode inputContractCode"), fields(INTEGER, "inputContractVersion graphSchemaVersion"), fields(array(fields), "fields"), fields(array(output), "nodeOutputs"),
                fields(object(fields(INTEGER, "bodyBytes nodes edges conditionBytes conditionNodes conditionDepth")), "limits"),
                fields(TEXT, "descriptorCode"), fields(INTEGER, "conditionSchemaVersion"),
                fields(RiskVariableResponseShape::queryContext, "variableQueryContext")));
        roots.put(new ParameterizedTypeReference<PagingResponseBody<com.paytech.risk.api.decision.response.FlowListItemResponseBody>>() {}.getType(), page(item));
        roots.put(new ParameterizedTypeReference<PagingResponseBody<com.paytech.risk.api.decision.response.FlowHistoryItemResponseBody>>() {}.getType(), page(history));
        roots.put(com.paytech.risk.api.decision.response.FlowDetailResponseBody.class, detail);
        roots.put(com.paytech.risk.api.decision.response.SaveFlowResponseBody.class,
            object(fields(TEXT, "flowId flowVersionId version savedBy savedTime"), fields(INTEGER, "versionNo")));
        roots.put(com.paytech.risk.api.decision.response.DeleteFlowResponseBody.class,
            object(fields(TEXT, "flowId status version deletedBy deletedTime")));
        roots.put(com.paytech.risk.api.decision.response.ValidateFlowResponseBody.class,
            object(fields(BOOLEAN, "valid truncated"), fields(array(object(fields(TEXT, "code message"), fields(NULLABLE_TEXT, "nodeKey edgeKey field"))), "issues")));
        roots.put(com.paytech.risk.api.decision.response.RuleSetReferenceResponseBody.class,
            object(fields(TEXT, "sourceType sourceVersionId nodeKey ruleSetStatus"), fields(RULE_SET_DETAIL, "ruleSet")));
        roots.put(com.paytech.risk.api.decision.response.LegacyPointResponseBody.class,
            object(fields(TEXT, "sourceType pointVersionId pointCode pointName inputContractCode defaultDecision evaluationStrategy failureStrategy"),
                fields(INTEGER, "versionNo inputContractVersion"), fields(array(node), "nodes"), fields(array(edge), "edges"), fields(array(reference), "ruleSetReferences")));
    }

    private static boolean flowExpression(JsonNode node, int depth, int[] count, int version) {
        if (node == null || !node.isObject() || depth > 8 || ++count[0] > 32 || !node.has("kind") || !node.get("kind").isString()) return false;
        if (node.get("kind").stringValue().equals("GROUP")) {
            if (!node.propertyNames().equals(java.util.Set.of("kind", "operator", "children")) || !node.get("operator").isString() || !node.get("children").isArray()) return false;
            for (var child : node.get("children")) if (!flowExpression(child, depth + 1, count, version)) return false;
            return true;
        }
        if (version == 2) return node.get("kind").stringValue().equals("PREDICATE")
            && object(fields(TEXT, "kind operator"), fields(array(OPERAND), "operands")).valid(node);
        return node.get("kind").stringValue().equals("PREDICATE") && object(fields(TEXT, "kind operator"),
            fields(object(fields(TEXT, "type fieldCode")), "source"), fields(array(v -> v.isString() || v.isBoolean()), "values")).valid(node);
    }

    boolean supports(ResolvableType type) {
        return RiskVariableResponseShape.supports(type) || ROOTS.containsKey(type.getType()) || ADDITIONAL_ROOTS.containsKey(type.getType());
    }

    void validate(ResolvableType type, JsonNode root) {
        if (RiskVariableResponseShape.supports(type)) {
            RiskVariableResponseShape.validate(root, type.resolve());
            return;
        }
        Shape shape = ROOTS.getOrDefault(type.getType(), ADDITIONAL_ROOTS.get(type.getType()));
        if (shape == null || root == null || !shape.valid(root)) {
            throw new IllegalArgumentException("Risk 成功响应不符合协议");
        }
    }

    private static Shape page(Shape item) {
        return object(fields(array(item), "items"), fields(INTEGER, "page size"), fields(TOTAL, "total"));
    }

    private static Shape nullable(Shape value) {
        return node -> node.isNull() || value.valid(node);
    }

    private static Shape array(Shape item) {
        return node -> {
            if (!node.isArray()) return false;
            for (JsonNode value : node) {
                if (!item.valid(value)) return false;
            }
            return true;
        };
    }

    @SafeVarargs
    private static Shape object(Map<String, Shape>... groups) {
        Map<String, Shape> properties = combine(groups);
        return node -> {
            if (!node.isObject() || !node.propertyNames().equals(properties.keySet())) return false;
            for (var property : properties.entrySet()) {
                if (!property.getValue().valid(node.get(property.getKey()))) return false;
            }
            return true;
        };
    }

    private static Map<String, Shape> fields(Shape shape, String names) {
        Map<String, Shape> properties = new HashMap<>();
        for (String name : names.split(" ")) properties.put(name, shape);
        return Map.copyOf(properties);
    }

    @SafeVarargs
    private static Map<String, Shape> combine(Map<String, Shape>... groups) {
        Map<String, Shape> properties = new HashMap<>();
        for (var group : groups) properties.putAll(group);
        return Map.copyOf(properties);
    }

    @FunctionalInterface
    private interface Shape {
        boolean valid(JsonNode node);
    }
}
