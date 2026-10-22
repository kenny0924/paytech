package com.paytech.platform.support.workflow.domainservice.convert;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.platform.support.workflow.core.common.WorkflowValueFormats;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 工作流配置 JSON 的严格读取工具：固定结构、拒绝未知属性与类型不符，错误信息带 JSON 路径。
 *
 * <p>只做结构解析；语义问题（空 key、引用不存在、数量越界等）留给发布校验以问题列表报告。</p>
 */
final class StrictJson {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final Pattern ID = Pattern.compile("[1-9][0-9]{0,18}");

    private StrictJson() {
    }

    /**
     * 解析 JSON 文本并要求根为对象。
     *
     * @param json JSON 文本
     * @param path 错误信息中的根路径，如 graph_json
     * @return 根对象
     */
    static JsonNode parseObject(String json, String path) {
        if (json == null || json.isBlank()) {
            throw new WorkflowJsonFieldException(path, "不能为空");
        }
        try {
            return object(JSON.readTree(json), path);
        } catch (JacksonException exception) {
            throw new WorkflowJsonFieldException(path, "不是合法的 JSON", exception);
        }
    }

    /**
     * 要求节点为对象。
     *
     * @param node 节点
     * @param path JSON 路径
     * @return 同一节点
     */
    static JsonNode object(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw new WorkflowJsonFieldException(path, "必须是 JSON 对象");
        }
        return node;
    }

    /**
     * 拒绝不在允许名单中的属性。
     *
     * @param object 对象节点
     * @param path JSON 路径
     * @param allowed 允许的属性名
     */
    static void allowOnly(JsonNode object, String path, Set<String> allowed) {
        for (String name : object.propertyNames()) {
            if (!allowed.contains(name)) {
                throw new WorkflowJsonFieldException(path + "." + name, "是不支持的属性");
            }
        }
    }

    /**
     * 读取可选文本；缺失或 null 返回 null。
     *
     * @param object 对象节点
     * @param name 属性名
     * @param path 对象的 JSON 路径
     * @return 文本或 null
     */
    static String text(JsonNode object, String name, String path) {
        JsonNode value = present(object, name);
        if (value == null) {
            return null;
        }
        if (!value.isString()) {
            throw new WorkflowJsonFieldException(path + "." + name, "必须是字符串");
        }
        return value.asString();
    }

    /**
     * 读取可选的 Snowflake ID；按项目约定以十进制字符串传输。
     *
     * @param object 对象节点
     * @param name 属性名
     * @param path 对象的 JSON 路径
     * @return ID 或 null
     */
    static Long id(JsonNode object, String name, String path) {
        String value = text(object, name, path);
        return value == null ? null : parseId(value, path + "." + name);
    }

    /**
     * 读取 ID 字符串数组；缺失返回空列表。
     *
     * @param object 对象节点
     * @param name 属性名
     * @param path 对象的 JSON 路径
     * @return ID 列表
     */
    static List<Long> ids(JsonNode object, String name, String path) {
        return array(object, name, path, (element, elementPath) -> {
            if (!element.isString()) {
                throw new WorkflowJsonFieldException(elementPath, "必须是字符串形式的 ID");
            }
            return parseId(element.asString(), elementPath);
        });
    }

    /**
     * 读取字符串数组；缺失返回空列表。
     *
     * @param object 对象节点
     * @param name 属性名
     * @param path 对象的 JSON 路径
     * @return 字符串列表
     */
    static List<String> texts(JsonNode object, String name, String path) {
        return array(object, name, path, (element, elementPath) -> {
            if (!element.isString()) {
                throw new WorkflowJsonFieldException(elementPath, "必须是字符串");
            }
            return element.asString();
        });
    }

    /**
     * 读取对象数组并逐个转换；缺失返回空列表。
     *
     * @param object 对象节点
     * @param name 属性名
     * @param path 对象的 JSON 路径
     * @param reader 元素转换，参数为元素节点与元素路径
     * @param <T> 元素类型
     * @return 转换后的列表
     */
    static <T> List<T> array(JsonNode object, String name, String path, BiFunction<JsonNode, String, T> reader) {
        JsonNode value = present(object, name);
        if (value == null) {
            return List.of();
        }
        if (!value.isArray()) {
            throw new WorkflowJsonFieldException(path + "." + name, "必须是数组");
        }
        List<T> result = new ArrayList<>();
        for (int index = 0; index < value.size(); index++) {
            result.add(reader.apply(value.get(index), path + "." + name + "[" + index + "]"));
        }
        return result;
    }

    /**
     * 读取可选的编码枚举；未知编码直接拒绝。
     *
     * @param object 对象节点
     * @param name 属性名
     * @param path 对象的 JSON 路径
     * @param type 枚举类型
     * @param <E> 枚举类型
     * @return 枚举或 null
     */
    static <E extends Enum<E> & CodeEnum> E code(JsonNode object, String name, String path, Class<E> type) {
        String value = text(object, name, path);
        return value == null ? null : parseCode(value, path + "." + name, type);
    }

    /**
     * 把编码文本解析为枚举；未知编码直接拒绝。
     *
     * @param value 编码
     * @param path JSON 路径
     * @param type 枚举类型
     * @param <E> 枚举类型
     * @return 枚举
     */
    static <E extends Enum<E> & CodeEnum> E parseCode(String value, String path, Class<E> type) {
        E result = EnumUtils.getEnumByCode(type, value);
        if (result == null) {
            throw new WorkflowJsonFieldException(path, "取值不合法，可选：" + codes(type));
        }
        return result;
    }

    /**
     * 读取可选布尔；缺失或 null 返回默认值。
     *
     * @param object 对象节点
     * @param name 属性名
     * @param path 对象的 JSON 路径
     * @param defaultValue 缺失时的值
     * @return 布尔值
     */
    static boolean bool(JsonNode object, String name, String path, boolean defaultValue) {
        JsonNode value = present(object, name);
        if (value == null) {
            return defaultValue;
        }
        if (!value.isBoolean()) {
            throw new WorkflowJsonFieldException(path + "." + name, "必须是布尔值");
        }
        return value.booleanValue();
    }

    /**
     * 读取可选整数；缺失或 null 返回 null。
     *
     * @param object 对象节点
     * @param name 属性名
     * @param path 对象的 JSON 路径
     * @return 整数或 null
     */
    static Integer integer(JsonNode object, String name, String path) {
        JsonNode value = present(object, name);
        if (value == null) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new WorkflowJsonFieldException(path + "." + name, "必须是整数");
        }
        return value.intValue();
    }

    /**
     * 读取可选十进制数：接受十进制字符串或 JSON 数字；缺失或 null 返回 null。
     *
     * @param object 对象节点
     * @param name 属性名
     * @param path 对象的 JSON 路径
     * @return 十进制数或 null
     */
    static BigDecimal decimal(JsonNode object, String name, String path) {
        JsonNode value = present(object, name);
        if (value == null) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isString() && WorkflowValueFormats.isDecimal(value.asString())) {
            return new BigDecimal(value.asString());
        }
        throw new WorkflowJsonFieldException(path + "." + name, "必须是十进制数");
    }

    /**
     * 取出存在且不为 null 的属性值。
     *
     * @param object 对象节点
     * @param name 属性名
     * @return 属性值；缺失或 null 时返回 null
     */
    static JsonNode present(JsonNode object, String name) {
        JsonNode value = object.get(name);
        return value == null || value.isNull() ? null : value;
    }

    /**
     * 枚举的全部编码，用于提示可选值。
     *
     * @param type 枚举类型
     * @param <E> 枚举类型
     * @return 以顿号连接的编码
     */
    private static <E extends Enum<E> & CodeEnum> String codes(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(CodeEnum::getCode).collect(Collectors.joining("、"));
    }

    /**
     * 解析十进制字符串 ID。
     *
     * @param value ID 文本
     * @param path JSON 路径
     * @return ID
     */
    private static Long parseId(String value, String path) {
        if (!ID.matcher(value).matches()) {
            throw new WorkflowJsonFieldException(path, "不是合法的 ID");
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new WorkflowJsonFieldException(path, "不是合法的 ID", exception);
        }
    }
}
