package com.paytech.platform.support.integration.domain.execution;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 执行环境值与 JSON 之间的转换。环境只使用 Map、List、String、Boolean、Long/BigInteger 与 BigDecimal；
 * 输出时 BigDecimal 按项目第一方 JSON 规则写成十进制字符串，不使用浮点。
 */
public final class JsonValues {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private JsonValues() {
    }

    /** 无字段结构约束的 JSON 转为环境值：整数为 Long（超出时 BigInteger），小数为 BigDecimal。 */
    public static Object toJava(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            var result = new LinkedHashMap<String, Object>();
            node.properties().forEach(entry -> result.put(entry.getKey(), toJava(entry.getValue())));
            return result;
        }
        if (node.isArray()) {
            var result = new ArrayList<Object>(node.size());
            node.forEach(item -> result.add(toJava(item)));
            return result;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            return node.canConvertToLong() ? (Object) node.longValue() : node.bigIntegerValue();
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        return node.asString();
    }

    /**
     * 环境值转为 JSON。
     *
     * @throws ExecutionErrorException 值不是可输出的 JSON 类型（例如受控句柄）
     */
    public static JsonNode toJson(Object value) {
        return toJson(value, false);
    }

    /**
     * 环境值转为 JSON；渠道报文要求时 BigDecimal 写成 JSON 数字（原样保留精度，不经浮点）。
     *
     * @param value 环境值
     * @param decimalAsNumber BigDecimal 是否写成 JSON 数字
     * @throws ExecutionErrorException 值不是可输出的 JSON 类型（例如受控句柄）
     */
    public static JsonNode toJson(Object value, boolean decimalAsNumber) {
        if (value == null) {
            return NODES.nullNode();
        }
        if (value instanceof Map<?, ?> map) {
            ObjectNode result = NODES.objectNode();
            map.forEach((key, item) -> result.set(String.valueOf(key), toJson(item, decimalAsNumber)));
            return result;
        }
        if (value instanceof List<?> list) {
            ArrayNode result = NODES.arrayNode();
            list.forEach(item -> result.add(toJson(item, decimalAsNumber)));
            return result;
        }
        if (value instanceof String text) {
            return NODES.stringNode(text);
        }
        if (value instanceof Boolean flag) {
            return NODES.booleanNode(flag);
        }
        if (value instanceof Long || value instanceof Integer) {
            return NODES.numberNode(((Number) value).longValue());
        }
        if (value instanceof BigInteger integer) {
            return NODES.numberNode(integer);
        }
        if (value instanceof BigDecimal decimal) {
            return decimalAsNumber ? NODES.numberNode(decimal) : NODES.stringNode(decimal.toPlainString());
        }
        throw new ExecutionErrorException("值不是可输出的 JSON 类型");
    }
}
