package com.paytech.foundation.redis.api;

import java.time.Duration;
import java.util.Objects;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.error.RedisErrorCode;
import com.paytech.foundation.redis.core.serialization.RedisJsonObjectMapperFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 使用普通 UTF-8 JSON 读写 Redis 对象缓存的稳定入口。
 *
 * <p>
 * 该模板要求读取方显式传入目标类型，因此不需要把 Java 类名写入 Redis。所有写入都必须显式给出 TTL； Key 的服务级前缀由底层 {@link StringRedisTemplate} 统一处理。
 *
 * @author Codex
 */
public final class RedisJsonTemplate {

    private static final Duration MINIMUM_TTL = Duration.ofMillis(1);

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public RedisJsonTemplate(StringRedisTemplate redisTemplate) {
        this(RedisJsonObjectMapperFactory.create(), redisTemplate);
    }

    private RedisJsonTemplate(ObjectMapper objectMapper, StringRedisTemplate redisTemplate) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
    }

    /**
     * 写入带过期时间的 JSON 对象。
     *
     * @param key
     *            逻辑 Key，不包含服务名前缀
     * @param value
     *            待缓存对象
     * @param ttl
     *            过期时间，不能小于 1 毫秒
     */
    public void set(String key, Object value, Duration ttl) {
        validateWrite(key, value, ttl);
        redisTemplate.opsForValue().set(key, serialize(value), ttl);
    }

    /**
     * Key 不存在时写入带过期时间的 JSON 对象。
     *
     * @param key
     *            逻辑 Key，不包含服务名前缀
     * @param value
     *            待缓存对象
     * @param ttl
     *            过期时间，不能小于 1 毫秒
     * @return 是否完成写入
     */
    public boolean setIfAbsent(String key, Object value, Duration ttl) {
        validateWrite(key, value, ttl);
        Boolean stored = redisTemplate.opsForValue().setIfAbsent(key, serialize(value), ttl);
        return Boolean.TRUE.equals(stored);
    }

    /**
     * 按明确类型读取 JSON 对象。
     *
     * @param key
     *            逻辑 Key，不包含服务名前缀
     * @param valueType
     *            目标类型
     * @param <T>
     *            目标类型
     * @return 缓存对象；Key 不存在时返回 {@code null}
     */
    public <T> T get(String key, Class<T> valueType) {
        validateKey(key);
        if (valueType == null) {
            throw invalidArgument("Redis JSON缓存目标类型不能为空");
        }
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, valueType);
        } catch (JacksonException exception) {
            throw new FailException(RedisErrorCode.JSON_DESERIALIZATION_FAILED, null, exception);
        }
    }

    /**
     * 按明确泛型读取 JSON 对象。
     *
     * @param key
     *            逻辑 Key，不包含服务名前缀
     * @param valueType
     *            目标泛型
     * @param <T>
     *            目标类型
     * @return 缓存对象；Key 不存在时返回 {@code null}
     */
    public <T> T get(String key, TypeReference<T> valueType) {
        validateKey(key);
        if (valueType == null) {
            throw invalidArgument("Redis JSON缓存目标泛型不能为空");
        }
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, valueType);
        } catch (JacksonException exception) {
            throw new FailException(RedisErrorCode.JSON_DESERIALIZATION_FAILED, null, exception);
        }
    }

    /**
     * 删除 JSON 缓存。
     *
     * @param key
     *            逻辑 Key，不包含服务名前缀
     * @return Key 是否存在并被删除
     */
    public boolean delete(String key) {
        validateKey(key);
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new FailException(RedisErrorCode.JSON_SERIALIZATION_FAILED, null, exception);
        }
    }

    private void validateWrite(String key, Object value, Duration ttl) {
        validateKey(key);
        if (value == null) {
            throw invalidArgument("Redis JSON缓存值不能为空");
        }
        if (ttl == null || ttl.compareTo(MINIMUM_TTL) < 0) {
            throw invalidArgument("Redis JSON缓存TTL不能小于1毫秒");
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw invalidArgument("Redis JSON缓存Key不能为空");
        }
    }

    private FailException invalidArgument(String message) {
        return new FailException(RedisErrorCode.OPERATION_ARGUMENT_INVALID, message);
    }
}
