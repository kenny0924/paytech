package com.paytech.platform.support.dataquery.domain;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 游标：Base64URL（无填充）编码的 JSON {"v":模板版本号,"c":{游标列:值}}；值为结果中的字符串或 null。
 * <li>版本号不一致时拒绝，调用方从第一页重新查询（Q10）。</li>
 */
public final class QueryCursorCodec {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private QueryCursorCodec() {
    }

    /**
     * 编码。
     *
     * @param versionNo 模板版本号；试运行为 0
     * @param values 游标列值，保持声明顺序
     * @return 游标
     */
    public static String encode(int versionNo, Map<String, Object> values) {
        ObjectNode node = JSON.createObjectNode();
        node.put("v", versionNo);
        ObjectNode columns = node.putObject("c");
        values.forEach((column, value) -> {
            if (value == null) {
                columns.putNull(column);
            } else {
                columns.put(column, String.valueOf(value));
            }
        });
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(JSON.writeValueAsString(node).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解码并核对模板版本。
     *
     * @param cursor 游标
     * @param versionNo 当前模板版本号
     * @return 游标列值，注入为 __cursor
     * @throws InvalidParameterException 格式不合法
     * @throws BizCheckFailException 模板版本已变化
     */
    public static Map<String, Object> decode(String cursor, int versionNo) {
        JsonNode node = parse(cursor);
        if (!node.path("v").isIntegralNumber() || !node.path("c").isObject()) {
            throw new InvalidParameterException("游标格式不合法");
        }
        if (node.path("v").asInt() != versionNo) {
            throw new BizCheckFailException("游标已失效：模板版本已变化，请从第一页重新查询");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        JsonNode columns = node.path("c");
        for (String column : columns.propertyNames()) {
            JsonNode value = columns.get(column);
            values.put(column, value.isNull() ? null : value.asString());
        }
        return values;
    }

    /**
     * 解析 Base64URL JSON。
     *
     * @param cursor 游标
     * @return JSON
     */
    static JsonNode parse(String cursor) {
        try {
            return JSON.readTree(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
        } catch (RuntimeException failure) {
            throw new InvalidParameterException("游标格式不合法");
        }
    }
}
