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
     * @throws ExecutionErrorException 值不是可输出的 JSON 类型（例如受控句柄、卡值）
     */
    public static JsonNode toJson(Object value, boolean decimalAsNumber) {
        return toJson(value, decimalAsNumber, SecretRendering.REJECT);
    }

    /**
     * 环境值转为 JSON，并指定卡值与受控句柄的写法。
     *
     * @param value 环境值
     * @param decimalAsNumber BigDecimal 是否写成 JSON 数字
     * @param secrets 卡值与受控句柄的写法：发送字节写原值，执行轨迹写遮蔽文本，其余位置拒绝
     * @throws ExecutionErrorException 值不是可输出的 JSON 类型
     */
    public static JsonNode toJson(Object value, boolean decimalAsNumber, SecretRendering secrets) {
        if (value == null) {
            return NODES.nullNode();
        }
        if (value instanceof Map<?, ?> map) {
            ObjectNode result = NODES.objectNode();
            map.forEach((key, item) -> result.set(String.valueOf(key), toJson(item, decimalAsNumber, secrets)));
            return result;
        }
        if (value instanceof List<?> list) {
            ArrayNode result = NODES.arrayNode();
            list.forEach(item -> result.add(toJson(item, decimalAsNumber, secrets)));
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
        if (value instanceof CardValue card && secrets != SecretRendering.REJECT) {
            return NODES.stringNode(secrets == SecretRendering.WIRE ? card.apply(secret -> secret) : card.masked());
        }
        if (value instanceof ProtectedHandle handle && secrets != SecretRendering.REJECT) {
            return NODES.stringNode(secrets == SecretRendering.WIRE ? handle.apply(secret -> secret) : ProtectedHandle.MASK);
        }
        throw new ExecutionErrorException("值不是可输出的 JSON 类型");
    }

    /** 环境值中是否含卡值。 */
    public static boolean containsCard(Object value) {
        return contains(value, CardValue.class);
    }

    /** 环境值中是否含受控句柄。 */
    public static boolean containsHandle(Object value) {
        return contains(value, ProtectedHandle.class);
    }

    /** 递归查找某类值。 */
    static boolean contains(Object value, Class<?> type) {
        return switch (value) {
            case Map<?, ?> map -> map.values().stream().anyMatch(item -> contains(item, type));
            case List<?> list -> list.stream().anyMatch(item -> contains(item, type));
            case null -> false;
            default -> type.isInstance(value);
        };
    }

    /** 卡值与受控句柄在 JSON 中的写法。 */
    public enum SecretRendering {

        /** 不允许出现卡值或受控句柄。 */
        REJECT,

        /** 渠道请求的发送字节：写原值。 */
        WIRE,

        /** 执行轨迹与证据：写遮蔽文本。 */
        MASKED
    }
}
