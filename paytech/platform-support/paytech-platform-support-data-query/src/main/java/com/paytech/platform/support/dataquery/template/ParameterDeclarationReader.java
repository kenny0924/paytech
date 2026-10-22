package com.paytech.platform.support.dataquery.template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.platform.support.dataquery.core.enums.QueryParameterType;

import tools.jackson.databind.JsonNode;

/**
 * 读取 parameters_json：只做结构解析（属性名、JSON 类型、类型编码），错误带 JSON 路径；
 * 类型与约束是否搭配、取值是否合法由保存校验以问题列表报告。
 */
public final class ParameterDeclarationReader {

    private static final String ROOT = "parameters";

    private static final Set<String> PROPERTIES = Set.of("name", "type", "elementType", "required", "default",
        "maxLength", "min", "max", "allowedValues", "maxSize", "choices", "fields");

    /**
     * 读取参数声明数组。
     *
     * @param root JSON 数组；null 视为空数组
     * @return 声明，保持顺序
     * @throws InvalidParameterException 结构不合法
     */
    public List<QueryParameterDeclaration> read(JsonNode root) {
        return readArray(root, ROOT);
    }

    /**
     * 读取声明数组（顶层参数或对象字段）。
     *
     * @param array JSON 数组；null 视为空
     * @param path JSON 路径
     * @return 声明
     */
    private List<QueryParameterDeclaration> readArray(JsonNode array, String path) {
        if (array == null || array.isNull()) {
            return List.of();
        }
        if (!array.isArray()) {
            throw new InvalidParameterException(path + " 必须是数组");
        }
        List<QueryParameterDeclaration> declarations = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            declarations.add(readOne(array.get(index), path + "[" + index + "]"));
        }
        return Collections.unmodifiableList(declarations);
    }

    /**
     * 读取一项声明。
     *
     * @param node JSON 对象
     * @param path JSON 路径
     * @return 声明
     */
    private QueryParameterDeclaration readOne(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw new InvalidParameterException(path + " 必须是 JSON 对象");
        }
        for (String property : node.propertyNames()) {
            if (!PROPERTIES.contains(property)) {
                throw new InvalidParameterException(path + " 含有不支持的属性：" + property);
            }
        }
        JsonNode defaultValue = node.get("default");
        return QueryParameterDeclaration.builder().name(requiredText(node, "name", path))
            .type(type(node, "type", path, true)).elementType(type(node, "elementType", path, false))
            .required(bool(node, path)).defaultValue(defaultValue == null || defaultValue.isNull() ? null : defaultValue)
            .maxLength(integer(node, "maxLength", path)).min(bound(node, "min", path)).max(bound(node, "max", path))
            .allowedValues(texts(node.get("allowedValues"), path + ".allowedValues"))
            .maxSize(integer(node, "maxSize", path)).choices(choices(node.get("choices"), path + ".choices"))
            .fields(node.has("fields") ? readArray(node.get("fields"), path + ".fields") : null).build();
    }

    /**
     * 读取必填文本属性。
     *
     * @param node 对象
     * @param name 属性名
     * @param path 对象路径
     * @return 文本
     */
    private static String requiredText(JsonNode node, String name, String path) {
        JsonNode value = node.get(name);
        if (value == null || !value.isString()) {
            throw new InvalidParameterException(path + "." + name + " 必须是字符串");
        }
        return value.asString();
    }

    /**
     * 读取类型编码。
     *
     * @param node 对象
     * @param name 属性名
     * @param path 对象路径
     * @param required 是否必填
     * @return 类型；可选且缺失时为 null
     */
    private static QueryParameterType type(JsonNode node, String name, String path, boolean required) {
        JsonNode value = node.get(name);
        if (!required && (value == null || value.isNull())) {
            return null;
        }
        QueryParameterType type = EnumUtils.getEnumByCode(QueryParameterType.class, requiredText(node, name, path));
        if (type == null) {
            throw new InvalidParameterException(path + "." + name + " 取值不合法：" + value.asString());
        }
        return type;
    }

    /**
     * 读取 required，缺失为 false。
     *
     * @param node 对象
     * @param path 对象路径
     * @return 是否必填
     */
    private static boolean bool(JsonNode node, String path) {
        JsonNode value = node.get("required");
        if (value == null || value.isNull()) {
            return false;
        }
        if (!value.isBoolean()) {
            throw new InvalidParameterException(path + ".required 必须是布尔值");
        }
        return value.booleanValue();
    }

    /**
     * 读取可选正整数属性。
     *
     * @param node 对象
     * @param name 属性名
     * @param path 对象路径
     * @return 整数；缺失时为 null
     */
    private static Integer integer(JsonNode node, String name, String path) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1) {
            throw new InvalidParameterException(path + "." + name + " 必须是正整数");
        }
        return value.intValue();
    }

    /**
     * 读取 min、max：字符串或数字，统一保存为文本。
     *
     * @param node 对象
     * @param name 属性名
     * @param path 对象路径
     * @return 文本；缺失时为 null
     */
    private static String bound(JsonNode node, String name, String path) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue().toPlainString();
        }
        if (!value.isString()) {
            throw new InvalidParameterException(path + "." + name + " 必须是字符串或数字");
        }
        return value.asString();
    }

    /**
     * 读取字符串数组。
     *
     * @param value JSON 值；缺失时为 null
     * @param path 路径
     * @return 不可变列表；缺失时为 null
     */
    private static List<String> texts(JsonNode value, String path) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isArray()) {
            throw new InvalidParameterException(path + " 必须是字符串数组");
        }
        List<String> texts = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isString()) {
                throw new InvalidParameterException(path + " 必须是字符串数组");
            }
            texts.add(item.asString());
        }
        return List.copyOf(texts);
    }

    /**
     * 读取片段 choices：键为取值，值为片段原文。
     *
     * @param value JSON 对象；缺失时为 null
     * @param path 路径
     * @return 保持顺序的不可变 Map；缺失时为 null
     */
    private static Map<String, String> choices(JsonNode value, String path) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isObject()) {
            throw new InvalidParameterException(path + " 必须是 JSON 对象");
        }
        Map<String, String> choices = new LinkedHashMap<>();
        for (String key : value.propertyNames()) {
            JsonNode text = value.get(key);
            if (!text.isString()) {
                throw new InvalidParameterException(path + "." + key + " 必须是字符串");
            }
            choices.put(key, text.asString());
        }
        return Collections.unmodifiableMap(choices);
    }
}
