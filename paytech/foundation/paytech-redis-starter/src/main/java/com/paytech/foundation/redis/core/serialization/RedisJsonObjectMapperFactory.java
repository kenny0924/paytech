package com.paytech.foundation.redis.core.serialization;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 创建 Redis 专用的普通 JSON ObjectMapper。
 *
 * @author Codex
 */
public final class RedisJsonObjectMapperFactory {

    private RedisJsonObjectMapperFactory() {}

    /**
     * 创建不启用 Java 类型元数据、兼容新增字段并支持稳定 CodeEnum Code 的 ObjectMapper。
     *
     * @return Redis JSON ObjectMapper
     */
    public static ObjectMapper create() {
        return JsonMapper.builder().findAndAddModules().addModule(new CodeEnumJsonModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    }
}
