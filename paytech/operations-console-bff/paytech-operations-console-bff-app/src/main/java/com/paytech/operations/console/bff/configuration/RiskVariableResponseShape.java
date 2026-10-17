package com.paytech.operations.console.bff.configuration;

import com.paytech.risk.api.decision.RiskVariableModels;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.ResolvableType;
import tools.jackson.databind.JsonNode;

/** 变量轻量合同的严格 token Reader；Owner 合同类的实例字段拥有字段集合，代码不注册业务变量。 */
final class RiskVariableResponseShape {
    private static final Set<Class<?>> ROOTS = Set.of(com.paytech.risk.api.decision.response.VariableManagementContextResponseBody.class,
        com.paytech.risk.api.decision.response.VariableListResponseBody.class, com.paytech.risk.api.decision.response.VariableDetailResponseBody.class,
        com.paytech.risk.api.decision.response.VariableMembersResponseBody.class, com.paytech.risk.api.decision.response.ReplaceMembersResponseBody.class,
        com.paytech.risk.api.decision.response.DeleteVariableResponseBody.class, com.paytech.risk.api.decision.response.VariableReferencesResponseBody.class);
    private static final Set<String> NULLABLE = Set.of("pointCode", "numericUnit", "currencyCode", "currencyVariableCode",
        "allowedValuesVariableCode", "currentValue", "memberCount", "description", "sourceContractCode",
        "sourceContractVersion", "sourceField", "allowedValues", "displayName", "versionId", "versionNo",
        "nextCursor", "nodeType", "currencyFieldKey", "semanticRole");

    private RiskVariableResponseShape() { }

    static boolean supports(ResolvableType type) { return ROOTS.contains(type.resolve()); }
    static boolean summary(JsonNode node) { return matches(node, RiskVariableModels.VariableSummary.class); }
    static boolean issue(JsonNode node) { return matches(node, RiskVariableModels.VariableIssue.class); }
    static boolean queryContext(JsonNode node) { return matches(node, RiskVariableModels.VariableQueryContext.class); }

    private static boolean matches(JsonNode node, Class<?> type) {
        try { validate(node, type); return true; }
        catch (IllegalArgumentException failure) { return false; }
    }

    static void validate(JsonNode node, Class<?> type) {
        require(node != null && node.isObject() && ownerContract(type));
        var components = components(type);
        var keys = components.stream().map(c -> c.getName()).collect(Collectors.toSet());
        if (type == com.paytech.risk.api.decision.response.VariableListResponseBody.class) {
            if (node.has("missingCodes")) keys.removeAll(Set.of("page", "size", "total"));
            else keys.remove("missingCodes");
        }
        require(node.propertyNames().equals(keys));
        for (var c : components) {
            if (!keys.contains(c.getName())) continue;
            var value = node.get(c.getName());
            if (value.isNull()) {
                require(NULLABLE.contains(c.getName()) && !(c.getName().equals("pointCode")
                    && (type == RiskVariableModels.VariableQueryContext.class || type == RiskVariableModels.SupportedPoint.class)));
                continue;
            }
            scalar(value, c.getGenericType(), c.getName());
        }
        if (type == RiskVariableModels.VariableSummary.class || type == com.paytech.risk.api.decision.response.VariableDetailResponseBody.class) {
            require(node.get("previewMembers").size() <= 100);
            String source = node.get("sourceType").stringValue();
            if (source.equals("CONSTANT")) {
                require(!node.get("currentValue").isNull() && node.get("memberCount").isNull());
                require(node.get("valueType").asString().equals("BOOLEAN") == node.get("currentValue").isBoolean());
            } else require(node.get("currentValue").isNull());
            if (source.equals("COLLECTION")) require(node.get("memberCount").isIntegralNumber());
            else require(node.get("memberCount").isNull() && node.get("previewMembers").isEmpty());
        }
    }

    private static void scalar(JsonNode value, Type type, String name) {
        if (type == String.class) {
            require(value.isString());
            String text = value.stringValue();
            if (Set.of("version", "expectedVersion", "rootId", "versionId", "createBy", "updateBy").contains(name)) {
                require(text.matches("[1-9][0-9]{0,18}") && new BigInteger(text).bitLength() <= 63);
            }
            if (Set.of("variableCode", "currencyVariableCode", "allowedValuesVariableCode").contains(name))
                require(text.matches("[A-Z][A-Z0-9_]{0,63}"));
            if (Set.of("valuesAsOf", "createTime", "updateTime").contains(name)) {
                require(text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{6}Z"));
                try {
                    var parsed = java.time.Instant.parse(text);
                    require(new java.time.format.DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(parsed).equals(text)
                        && text.compareTo("1000-") >= 0);
                } catch (java.time.format.DateTimeParseException failure) {
                    throw new IllegalArgumentException("变量响应日期无效");
                }
            }
            if (name.equals("sourceType")) require(Set.of("INPUT", "CONSTANT", "COLLECTION", "NODE_OUTPUT", "VELOCITY", "LIST").contains(text));
            if (name.equals("valueType")) require(Set.of("STRING", "NUMBER", "BOOLEAN", "DATETIME", "DURATION").contains(text));
            if (name.equals("status")) require(Set.of("ACTIVE", "DISABLED", "DELETED").contains(text));
            if (name.equals("numericUnit")) require(Set.of("SCALAR", "MONEY").contains(text));
            if (name.equals("usages") || name.equals("allowedUsages") || name.equals("usage"))
                require(Set.of("RULE_CONDITION", "FLOW_CONDITION", "ACTION_PARAMETER").contains(text));
        } else if (type == Object.class) require(value.isString() || value.isBoolean());
        else if (type == boolean.class || type == Boolean.class) require(value.isBoolean());
        else if (type == int.class || type == Integer.class) {
            require(value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= 0);
            if (Set.of("page", "size", "sortOrder", "contractVersion", "inputContractVersion", "sourceContractVersion", "versionNo").contains(name))
                require(value.intValue() > 0);
        }
        else if (type == long.class || type == Long.class)
            require(value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0);
        else if (type instanceof ParameterizedType p && p.getRawType() == List.class) {
            require(value.isArray() && value.size() <= 1000);
            for (var child : value) scalar(child, p.getActualTypeArguments()[0], name);
        } else if (type instanceof Class<?> contract && ownerContract(contract)) validate(value, contract);
        else throw new IllegalArgumentException("变量响应出现未声明的类型");
    }

    /** Risk 公开合同类型；字段集合来自其实例字段。 */
    private static boolean ownerContract(Class<?> type) {
        return type.getPackageName().startsWith("com.paytech.risk.api.decision") && !type.isEnum();
    }

    /** 合同类按声明顺序的实例字段，与 JSON 属性一一对应。 */
    private static List<java.lang.reflect.Field> components(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers())).toList();
    }

    private static void require(boolean valid) {
        if (!valid) throw new IllegalArgumentException("变量响应不符合严格合同");
    }
}
