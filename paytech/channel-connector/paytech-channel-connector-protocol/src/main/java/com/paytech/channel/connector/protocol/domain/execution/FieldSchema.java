package com.paytech.channel.connector.protocol.domain.execution;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.paytech.channel.connector.api.enums.ApiFieldDataType;
import com.paytech.channel.connector.api.enums.ApiFieldLocation;
import com.paytech.channel.connector.protocol.domain.FieldDefinition;

import tools.jackson.databind.JsonNode;

/**
 * 由 BODY 字段定义构建的结构树，用于校验并转换标准 API 输入、校验组装出的输出。
 *
 * <p>声明了子字段的对象只接受已声明的属性；没有声明子字段的 OBJECT、没有声明元素的 ARRAY 视为不透明，
 * 内容不再按字段校验。没有 nullable 定义，null 一律不接受（取值阶段 null 等同缺失）。
 * DECIMAL 在 Wire 上是十进制字符串（不接受指数），环境中为 BigDecimal；INTEGER 是 JSON 整数，环境中为 Long。
 */
public final class FieldSchema {

    /** 标准 API 响应字段可选的统一前缀：输出对象即 outputs 之下的结构。 */
    public static final String OUTPUTS_ROOT = "outputs";

    private static final Pattern DECIMAL = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?");

    private final ApiFieldDataType dataType;
    private final boolean required;
    private final boolean root;
    private final Map<String, FieldSchema> children = new LinkedHashMap<>();
    private FieldSchema element;

    private FieldSchema(ApiFieldDataType dataType, boolean required, boolean root) {
        this.dataType = dataType;
        this.required = required;
        this.root = root;
    }

    /** 以全部 BODY 字段构建根对象；其他位置的字段忽略。 */
    public static FieldSchema of(List<FieldDefinition> fields) {
        var schema = new FieldSchema(ApiFieldDataType.OBJECT, true, true);
        fields.stream().filter(field -> field.getLocation() == ApiFieldLocation.BODY)
            .sorted(Comparator.<FieldDefinition>comparingInt(field -> field.getPath().split("\\.").length)
                .thenComparingInt(field -> field.getPath().length()))
            .forEach(field -> schema.add(field.getPath(), field.getDataType(), field.isRequired()));
        return schema;
    }

    /** 标准 API 输出结构：响应字段若以 outputs 为根，则取其下的结构。 */
    public static FieldSchema outputs(List<FieldDefinition> responseFields) {
        String prefix = OUTPUTS_ROOT + ".";
        return of(responseFields.stream().filter(field -> !field.getPath().equals(OUTPUTS_ROOT))
            .map(field -> field.getPath().startsWith(prefix) ? new FieldDefinition(field.getLocation(),
                field.getPath().substring(prefix.length()), field.getName(), field.getDataType(), field.isRequired(),
                field.getDescription()) : field)
            .toList());
    }

    public ApiFieldDataType getDataType() {
        return dataType;
    }

    public boolean isRequired() {
        return required;
    }

    /** 按点分路径（不含 []）查找子结构，不存在返回 null。 */
    public FieldSchema at(String path) {
        FieldSchema current = this;
        for (String segment : path.split("\\.")) {
            current = current.children.get(segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * 校验 JSON 并转换为环境值。
     *
     * @param node JSON 值
     * @param path 当前位置，根为空串
     * @throws FieldSchemaViolation 不满足结构
     */
    public Object fromJson(JsonNode node, String path) {
        if (node == null || node.isNull()) {
            throw new FieldSchemaViolation(path, "不能为 null");
        }
        return switch (dataType) {
            case STRING -> {
                require(node.isString(), path, "必须是字符串");
                yield node.asString();
            }
            case BOOLEAN -> {
                require(node.isBoolean(), path, "必须是布尔值");
                yield node.booleanValue();
            }
            case INTEGER -> {
                require(node.isIntegralNumber() && node.canConvertToLong(), path, "必须是 64 位整数");
                yield node.longValue();
            }
            case DECIMAL -> {
                require(node.isString() && DECIMAL.matcher(node.asString()).matches(), path, "必须是十进制字符串");
                yield new BigDecimal(node.asString());
            }
            case OBJECT -> objectFromJson(node, path);
            case ARRAY -> arrayFromJson(node, path);
        };
    }

    /**
     * 校验环境值满足结构。
     *
     * @param value 环境值
     * @param path 当前位置，根为空串
     * @throws FieldSchemaViolation 不满足结构
     */
    public void check(Object value, String path) {
        if (value == null) {
            throw new FieldSchemaViolation(path, "不能为 null");
        }
        switch (dataType) {
            case STRING -> require(value instanceof String || value instanceof CardValue || value instanceof ProtectedHandle, path,
                "必须是字符串");
            case BOOLEAN -> require(value instanceof Boolean, path, "必须是布尔值");
            case INTEGER -> require(value instanceof Long || value instanceof Integer, path, "必须是 64 位整数");
            case DECIMAL -> require(value instanceof BigDecimal, path, "必须是十进制数");
            case OBJECT -> checkObject(value, path);
            case ARRAY -> checkArray(value, path);
        }
    }

    /** 声明了子字段的对象只接受已声明属性，并要求必填子字段存在。 */
    Object objectFromJson(JsonNode node, String path) {
        require(node.isObject(), path, "必须是对象");
        if (opaque()) {
            return JsonValues.toJava(node);
        }
        var result = new LinkedHashMap<String, Object>();
        for (var entry : node.properties()) {
            FieldSchema child = children.get(entry.getKey());
            if (child == null) {
                throw new FieldSchemaViolation(child(path, entry.getKey()), "未声明的字段");
            }
            result.put(entry.getKey(), child.fromJson(entry.getValue(), child(path, entry.getKey())));
        }
        requireChildren(result, path);
        return result;
    }

    Object arrayFromJson(JsonNode node, String path) {
        require(node.isArray(), path, "必须是数组");
        if (opaque()) {
            return JsonValues.toJava(node);
        }
        var result = new java.util.ArrayList<Object>(node.size());
        for (int index = 0; index < node.size(); index++) {
            result.add(element.fromJson(node.get(index), path + "[" + index + "]"));
        }
        return result;
    }

    void checkObject(Object value, String path) {
        require(value instanceof Map<?, ?>, path, "必须是对象");
        if (opaque()) {
            return;
        }
        var map = (Map<?, ?>) value;
        for (var entry : map.entrySet()) {
            FieldSchema child = children.get(String.valueOf(entry.getKey()));
            if (child == null) {
                throw new FieldSchemaViolation(child(path, String.valueOf(entry.getKey())), "未声明的字段");
            }
            child.check(entry.getValue(), child(path, String.valueOf(entry.getKey())));
        }
        requireChildren(map, path);
    }

    void checkArray(Object value, String path) {
        require(value instanceof List<?>, path, "必须是数组");
        if (opaque()) {
            return;
        }
        var list = (List<?>) value;
        for (int index = 0; index < list.size(); index++) {
            element.check(list.get(index), path + "[" + index + "]");
        }
    }

    void requireChildren(Map<?, ?> value, String path) {
        children.forEach((name, child) -> {
            if (child.required && !value.containsKey(name)) {
                throw new FieldSchemaViolation(child(path, name), "缺少必填字段");
            }
        });
    }

    /** 没有声明子字段的非根 OBJECT、没有声明元素的 ARRAY。 */
    boolean opaque() {
        return dataType == ApiFieldDataType.OBJECT ? !root && children.isEmpty() : element == null;
    }

    /** 按路径挂入子结构；中间段的 [] 进入数组元素，元素未声明时视为对象。 */
    void add(String path, ApiFieldDataType type, boolean requiredFlag) {
        String[] segments = path.split("\\.");
        FieldSchema current = this;
        for (int index = 0; index < segments.length - 1; index++) {
            current = current.step(segments[index]);
            if (current == null) {
                return;
            }
        }
        String last = segments[segments.length - 1];
        if (last.endsWith("[]")) {
            FieldSchema array = current.children.get(last.substring(0, last.length() - 2));
            if (array != null) {
                array.element = new FieldSchema(type, true, false);
            }
            return;
        }
        current.children.put(last, new FieldSchema(type, requiredFlag, false));
    }

    /** 进入一段：普通段为子对象，[] 段为数组元素。 */
    FieldSchema step(String segment) {
        if (!segment.endsWith("[]")) {
            return children.get(segment);
        }
        FieldSchema array = children.get(segment.substring(0, segment.length() - 2));
        if (array == null) {
            return null;
        }
        if (array.element == null) {
            array.element = new FieldSchema(ApiFieldDataType.OBJECT, true, false);
        }
        return array.element;
    }

    static String child(String path, String name) {
        return path.isEmpty() ? name : path + "." + name;
    }

    static void require(boolean condition, String path, String message) {
        if (!condition) {
            throw new FieldSchemaViolation(path, message);
        }
    }
}
