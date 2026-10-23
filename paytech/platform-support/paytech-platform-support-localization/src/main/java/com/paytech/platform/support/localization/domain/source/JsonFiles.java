package com.paytech.platform.support.localization.domain.source;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 导入文件的 JSON 解析：拒绝重复键，避免同名 key 静默覆盖。 */
final class JsonFiles {

    private static final JsonMapper JSON = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    private JsonFiles() {
    }

    /**
     * 解析为 JSON 树。
     *
     * @param content UTF-8 字节
     * @return 根节点；不是合法 JSON 时为 null
     */
    static JsonNode read(byte[] content) {
        try {
            return JSON.readTree(content);
        } catch (JacksonException invalid) {
            return null;
        }
    }

    /**
     * 读取字符串属性。
     *
     * @param node 对象节点
     * @param name 属性名
     * @return 字符串值；缺失或不是字符串时为 null
     */
    static String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value != null && value.isString() ? value.asString() : null;
    }
}
