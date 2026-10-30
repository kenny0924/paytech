package com.paytech.platform.support.dataquery.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.platform.support.dataquery.core.enums.ColumnMaskType;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyRole;
import com.paytech.platform.support.dataquery.core.enums.QueryPagingMode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 模板与数据源 JSON 列的固定结构：parameters_json、columns_json、constraints_json、joint_json、connection_params_json。
 *
 * <p>写入时由本类生成，读取时数据库内容不合规视为系统不变量被破坏，按 500 失败关闭。</p>
 */
public final class QueryTemplateJson {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private QueryTemplateJson() {
    }

    /**
     * 参数声明原样序列化；null 为空数组。
     *
     * @param parameters 参数声明 JSON
     * @return JSON 文本
     */
    public static String writeParameters(JsonNode parameters) {
        return parameters == null || parameters.isNull() ? "[]" : JSON.writeValueAsString(parameters);
    }

    /**
     * 读取参数声明 JSON。
     *
     * @param json JSON 文本
     * @return JSON 数组节点
     */
    public static JsonNode readParameters(String json) {
        return read(json == null ? "[]" : json, "parameters_json");
    }

    /**
     * 列声明序列化；没有列时为 null。
     *
     * @param columns 列声明
     * @return JSON 文本或 null
     */
    public static String writeColumns(List<QueryColumnRule> columns) {
        if (columns == null || columns.isEmpty()) {
            return null;
        }
        ArrayNode array = JSON.createArrayNode();
        for (QueryColumnRule column : columns) {
            ObjectNode node = array.addObject();
            node.put("name", column.getName());
            if (column.getLabel() != null) {
                node.put("label", column.getLabel());
            }
            node.put("mask", column.getMask().getCode());
            node.put("keepStart", column.getKeepStart());
            node.put("keepEnd", column.getKeepEnd());
        }
        return JSON.writeValueAsString(array);
    }

    /**
     * 读取列声明。
     *
     * @param json JSON 文本，可为 null
     * @return 列声明；null 为空列表
     */
    public static List<QueryColumnRule> readColumns(String json) {
        if (json == null) {
            return List.of();
        }
        List<QueryColumnRule> columns = new ArrayList<>();
        for (JsonNode node : read(json, "columns_json")) {
            columns.add(new QueryColumnRule(node.path("name").asString(), text(node, "label"),
                code(ColumnMaskType.class, node.path("mask").asString(), "columns_json.mask"),
                node.path("keepStart").asInt(), node.path("keepEnd").asInt()));
        }
        return List.copyOf(columns);
    }

    /**
     * 约束序列化。
     *
     * @param constraints 约束
     * @return JSON 文本
     */
    public static String writeConstraints(QueryConstraints constraints) {
        ObjectNode node = JSON.createObjectNode();
        node.put("maxRows", constraints.getMaxRows());
        node.put("timeoutSeconds", constraints.getTimeoutSeconds());
        node.put("pagingMode", constraints.getPagingMode().getCode());
        if (constraints.getMaxPageSize() != null) {
            node.put("maxPageSize", constraints.getMaxPageSize());
        }
        node.put("countAllowed", constraints.isCountAllowed());
        ArrayNode cursor = node.putArray("cursorColumns");
        constraints.getCursorColumns().forEach(cursor::add);
        return JSON.writeValueAsString(node);
    }

    /**
     * 读取约束。
     *
     * @param json JSON 文本
     * @return 约束
     */
    public static QueryConstraints readConstraints(String json) {
        JsonNode node = read(json, "constraints_json");
        List<String> cursor = new ArrayList<>();
        node.path("cursorColumns").forEach(column -> cursor.add(column.asString()));
        JsonNode pageSize = node.get("maxPageSize");
        return new QueryConstraints(node.path("maxRows").asInt(), node.path("timeoutSeconds").asInt(),
            code(QueryPagingMode.class, node.path("pagingMode").asString(), "constraints_json.pagingMode"),
            pageSize == null || pageSize.isNull() ? null : pageSize.asInt(), node.path("countAllowed").asBoolean(),
            List.copyOf(cursor));
    }

    /**
     * 联合查询配置序列化；不参与时为 null。
     *
     * @param joint 配置，可为 null
     * @return JSON 文本或 null
     */
    public static String writeJoint(QueryJointConfig joint) {
        if (joint == null) {
            return null;
        }
        ObjectNode node = JSON.createObjectNode();
        node.put("subjectKeyType", joint.getSubjectKeyType());
        ObjectNode key = node.putObject("keyParameter");
        key.put("name", joint.getKeyParameter().getName());
        key.put("keyType", joint.getKeyParameter().getKeyType());
        ArrayNode columns = node.putArray("columns");
        for (QueryJointColumn column : joint.getColumns()) {
            ObjectNode item = columns.addObject();
            item.put("name", column.getName());
            item.put("keyType", column.getKeyType());
            item.put("keyRole", column.getKeyRole().getCode());
        }
        return JSON.writeValueAsString(node);
    }

    /**
     * 读取联合查询配置。
     *
     * @param json JSON 文本，可为 null
     * @return 配置；null 表示该版本不参与
     */
    public static QueryJointConfig readJoint(String json) {
        if (json == null) {
            return null;
        }
        JsonNode node = read(json, "joint_json");
        JsonNode key = node.path("keyParameter");
        List<QueryJointColumn> columns = new ArrayList<>();
        for (JsonNode column : node.path("columns")) {
            columns.add(new QueryJointColumn(required(column, "name"), required(column, "keyType"),
                code(QueryKeyRole.class, column.path("keyRole").asString(), "joint_json.columns.keyRole")));
        }
        return new QueryJointConfig(required(node, "subjectKeyType"),
            new QueryJointKeyParameter(required(key, "name"), required(key, "keyType")), List.copyOf(columns));
    }

    /**
     * 驱动参数序列化；没有参数时为 null。
     *
     * @param params 驱动参数
     * @return JSON 文本或 null
     */
    public static String writeConnectionParams(Map<String, String> params) {
        return params == null || params.isEmpty() ? null : JSON.writeValueAsString(params);
    }

    /**
     * 读取驱动参数。
     *
     * @param json JSON 文本，可为 null
     * @return 驱动参数；null 为空
     */
    public static Map<String, String> readConnectionParams(String json) {
        if (json == null) {
            return Map.of();
        }
        Map<String, String> params = new LinkedHashMap<>();
        JsonNode node = read(json, "connection_params_json");
        for (String key : node.propertyNames()) {
            params.put(key, node.get(key).asString());
        }
        return params;
    }

    /**
     * 解析 JSON 文本。
     *
     * @param json 文本
     * @param column 列名，用于诊断
     * @return 节点
     */
    static JsonNode read(String json, String column) {
        try {
            return JSON.readTree(json);
        } catch (RuntimeException failure) {
            throw new ErrorException(column + " 内容不是合法 JSON", failure);
        }
    }

    /**
     * 可选文本属性。
     *
     * @param node 对象
     * @param name 属性名
     * @return 文本；缺失或 null 为 null
     */
    static String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value.asString();
    }

    /** joint_json 中的必填文本属性；缺失视为数据不一致。 */
    static String required(JsonNode node, String name) {
        String value = text(node, name);
        if (value == null) {
            throw new ErrorException("joint_json 缺少属性: " + name);
        }
        return value;
    }

    /**
     * 解析枚举 Code，未知值失败关闭。
     *
     * @param type 枚举类型
     * @param code Code
     * @param column 列与属性，用于诊断
     * @return 枚举
     */
    static <E extends CodeEnum> E code(Class<E> type, String code, String column) {
        E value = EnumUtils.getEnumByCode(type, code);
        if (value == null) {
            throw new ErrorException(column + " 取值未知: " + code);
        }
        return value;
    }
}
