package com.paytech.platform.support.dataquery.template;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.paytech.platform.support.dataquery.core.enums.QueryParameterType;

import tools.jackson.databind.JsonNode;

/**
 * 把调用方 JSON 值按声明转换为 Java 值并检查约束，错误记入字段路径，不做隐式转换。
 *
 * <p>转换结果：STRING、ENUM 为 String，INTEGER 为 Long，DECIMAL 为 BigDecimal，BOOLEAN 为 Boolean，DATE 为 LocalDate，
 * DATETIME 为 LocalDateTime，FRAGMENT 为 {@link SelectedFragment}，LIST 为不可变 List，OBJECT 为保持字段顺序的 Map。</p>
 */
final class ParameterValueConverter {

    private static final Pattern INTEGER = Pattern.compile("-?\\d{1,19}");

    private static final Pattern DECIMAL = Pattern.compile("-?\\d+(\\.\\d+)?");

    private final int maxListSize;

    /**
     * 以系统集合上限构造。
     *
     * @param maxListSize 集合元素数硬上限
     */
    ParameterValueConverter(int maxListSize) {
        this.maxListSize = maxListSize;
    }

    /**
     * 转换一个非 null 的 JSON 值。
     *
     * @param declaration 声明
     * @param node JSON 值，不能是 null 节点
     * @param field 字段路径
     * @param errors 错误收集
     * @return 转换后的值；出错时为 null
     */
    Object convert(QueryParameterDeclaration declaration, JsonNode node, String field, List<ParameterError> errors) {
        return switch (declaration.getType()) {
            case LIST -> convertList(declaration, node, field, errors);
            case OBJECT -> convertObject(declaration, node, field, errors);
            case FRAGMENT -> convertFragment(declaration, node, field, errors);
            default -> convertScalar(declaration, node, field, errors);
        };
    }

    /**
     * 解析标量 JSON 值，不检查约束。
     *
     * @param type 标量类型
     * @param node JSON 值
     * @return Java 值；形态不符时为 null
     */
    static Object parseScalar(QueryParameterType type, JsonNode node) {
        return switch (type) {
            case BOOLEAN -> node.isBoolean() ? node.booleanValue() : null;
            case INTEGER -> node.isIntegralNumber() && node.canConvertToLong() ? Long.valueOf(node.longValue())
                : node.isString() ? parseText(type, node.asString()) : null;
            default -> node.isString() ? parseText(type, node.asString()) : null;
        };
    }

    /**
     * 解析标量文本；也用于解析声明中的 min、max。
     *
     * @param type 标量类型
     * @param text 文本
     * @return Java 值；格式不符时为 null
     */
    static Object parseText(QueryParameterType type, String text) {
        try {
            return switch (type) {
                case STRING, ENUM -> text;
                case INTEGER -> INTEGER.matcher(text).matches() ? Long.valueOf(text) : null;
                case DECIMAL -> DECIMAL.matcher(text).matches() ? new BigDecimal(text) : null;
                case DATE -> LocalDate.parse(text);
                case DATETIME -> LocalDateTime.parse(text);
                default -> null;
            };
        } catch (NumberFormatException | DateTimeParseException exception) {
            return null;
        }
    }

    /**
     * 转换标量并检查约束。
     *
     * @param declaration 声明
     * @param node JSON 值
     * @param field 字段路径
     * @param errors 错误收集
     * @return Java 值；出错时为 null
     */
    private static Object convertScalar(QueryParameterDeclaration declaration, JsonNode node, String field,
        List<ParameterError> errors) {
        Object value = parseScalar(declaration.getType(), node);
        String violation = value == null ? typeMessage(declaration.getType()) : constraintViolation(declaration, value);
        if (violation != null) {
            errors.add(new ParameterError(field, violation));
            return null;
        }
        return value;
    }

    /**
     * 检查标量值的约束。
     *
     * @param declaration 声明
     * @param value 已转换的值
     * @return 违反时的原因；满足时为 null
     */
    private static String constraintViolation(QueryParameterDeclaration declaration, Object value) {
        List<String> allowed = declaration.getAllowedValues();
        if (allowed != null && !allowed.contains(value)) {
            return "取值不在允许范围内";
        }
        if (value instanceof String text && declaration.getMaxLength() != null
            && text.length() > declaration.getMaxLength()) {
            return "长度不能超过 " + declaration.getMaxLength();
        }
        if (compareBound(declaration.getType(), value, declaration.getMin()) < 0) {
            return "不能小于 " + declaration.getMin();
        }
        if (compareBound(declaration.getType(), value, declaration.getMax()) > 0) {
            return "不能大于 " + declaration.getMax();
        }
        return null;
    }

    /**
     * 与上下限比较。
     *
     * @param type 类型
     * @param value 值
     * @param bound 上下限文本；没有或无法解析时视为满足
     * @return 值与上下限的比较结果；没有上下限时为 0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareBound(QueryParameterType type, Object value, String bound) {
        Object parsed = bound == null ? null : parseText(type, bound);
        if (!(parsed instanceof Comparable comparable) || type == QueryParameterType.STRING
            || type == QueryParameterType.ENUM) {
            return 0;
        }
        return ((Comparable) value).compareTo(comparable);
    }

    /**
     * 转换集合：元素数不超过 min(声明, 系统上限)，元素不能为 null。
     *
     * @param declaration 声明
     * @param node JSON 值
     * @param field 字段路径
     * @param errors 错误收集
     * @return 不可变列表；出错时为 null
     */
    private Object convertList(QueryParameterDeclaration declaration, JsonNode node, String field,
        List<ParameterError> errors) {
        int limit = declaration.getMaxSize() == null ? maxListSize : Math.min(declaration.getMaxSize(), maxListSize);
        if (!node.isArray()) {
            errors.add(new ParameterError(field, "必须是数组"));
            return null;
        }
        if (node.size() > limit) {
            errors.add(new ParameterError(field, "元素数不能超过 " + limit));
            return null;
        }
        QueryParameterDeclaration element = declaration.elementDeclaration();
        List<Object> values = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode item = node.get(index);
            String itemField = field + "[" + index + "]";
            if (item.isNull()) {
                errors.add(new ParameterError(itemField, "元素不能为 null"));
            } else {
                values.add(convert(element, item, itemField, errors));
            }
        }
        return Collections.unmodifiableList(values);
    }

    /**
     * 转换对象：拒绝未声明字段，按字段声明逐个转换。
     *
     * @param declaration 对象声明
     * @param node JSON 值
     * @param field 字段路径
     * @param errors 错误收集
     * @return 保持字段顺序的不可变 Map；出错时为 null
     */
    private Object convertObject(QueryParameterDeclaration declaration, JsonNode node, String field,
        List<ParameterError> errors) {
        if (!node.isObject()) {
            errors.add(new ParameterError(field, "必须是 JSON 对象"));
            return null;
        }
        for (String name : node.propertyNames()) {
            if (declaration.field(name) == null) {
                errors.add(new ParameterError(field + "." + name, "未声明的字段"));
            }
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (QueryParameterDeclaration fieldDeclaration : declaration.getFields()) {
            String fieldPath = field + "." + fieldDeclaration.getName();
            JsonNode value = node.get(fieldDeclaration.getName());
            if (value == null || value.isNull()) {
                addMissing(fieldDeclaration, fieldPath, errors);
                values.put(fieldDeclaration.getName(), null);
            } else {
                values.put(fieldDeclaration.getName(), convert(fieldDeclaration, value, fieldPath, errors));
            }
        }
        return Collections.unmodifiableMap(values);
    }

    /**
     * 必填字段缺失时记错。
     *
     * @param declaration 字段声明
     * @param field 字段路径
     * @param errors 错误收集
     */
    static void addMissing(QueryParameterDeclaration declaration, String field, List<ParameterError> errors) {
        if (declaration.isRequired()) {
            errors.add(new ParameterError(field, "必填"));
        }
    }

    /**
     * 转换片段参数：必须是 choices 的键。
     *
     * @param declaration 声明
     * @param node JSON 值
     * @param field 字段路径
     * @param errors 错误收集
     * @return 选中的片段；出错时为 null
     */
    private static Object convertFragment(QueryParameterDeclaration declaration, JsonNode node, String field,
        List<ParameterError> errors) {
        Map<String, String> choices = declaration.getChoices();
        if (!node.isString() || choices == null || !choices.containsKey(node.asString())) {
            errors.add(new ParameterError(field, "取值不在可选范围内"));
            return null;
        }
        return new SelectedFragment(node.asString(), choices.get(node.asString()));
    }

    /**
     * 类型不符时的原因。
     *
     * @param type 类型
     * @return 中文原因
     */
    private static String typeMessage(QueryParameterType type) {
        return switch (type) {
            case INTEGER -> "必须是 64 位整数（字符串或整数）";
            case DECIMAL -> "必须是十进制字符串";
            case BOOLEAN -> "必须是布尔值";
            case DATE -> "必须是 YYYY-MM-DD 格式的日期";
            case DATETIME -> "必须是不带时区的 ISO-8601 日期时间，如 2026-10-22T08:30:00";
            default -> "必须是字符串";
        };
    }
}
