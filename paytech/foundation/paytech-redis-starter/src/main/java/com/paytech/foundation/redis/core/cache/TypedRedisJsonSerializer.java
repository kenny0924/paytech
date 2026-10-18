package com.paytech.foundation.redis.core.cache;

import java.util.Objects;

import org.springframework.data.redis.serializer.RedisSerializer;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.error.RedisErrorCode;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 按缓存定义中的明确类型读写普通 JSON，不向 Redis 写入 Java 类型元数据。
 *
 * @author Codex
 */
public final class TypedRedisJsonSerializer implements RedisSerializer<Object> {

    private final String cacheName;
    private final Class<?> valueType;
    private final ObjectMapper objectMapper;

    public TypedRedisJsonSerializer(String cacheName, Class<?> valueType, ObjectMapper objectMapper) {
        this.cacheName = Objects.requireNonNull(cacheName, "cacheName must not be null");
        this.valueType = Objects.requireNonNull(valueType, "valueType must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public byte[] serialize(Object value) {
        if (value == null) {
            throw new FailException(RedisErrorCode.CACHE_VALUE_TYPE_MISMATCH,
                "Redis Cache[" + cacheName + "]不允许写入null");
        }
        if (value.getClass() != valueType) {
            throw new FailException(RedisErrorCode.CACHE_VALUE_TYPE_MISMATCH,
                "Redis Cache[" + cacheName + "]只允许写入" + valueType.getSimpleName());
        }
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JacksonException exception) {
            throw new FailException(RedisErrorCode.JSON_SERIALIZATION_FAILED,
                "Redis Cache[" + cacheName + "] JSON序列化失败", exception);
        }
    }

    @Override
    public Object deserialize(byte[] source) {
        if (source == null || source.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(source, valueType);
        } catch (JacksonException exception) {
            throw new FailException(RedisErrorCode.JSON_DESERIALIZATION_FAILED,
                "Redis Cache[" + cacheName + "] JSON反序列化失败", exception);
        }
    }

    @Override
    public boolean canSerialize(Class<?> type) {
        return type == valueType;
    }

    @Override
    public Class<?> getTargetType() {
        return valueType;
    }
}
