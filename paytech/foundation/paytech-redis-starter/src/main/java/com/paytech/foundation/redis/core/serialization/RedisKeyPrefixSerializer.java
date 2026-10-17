package com.paytech.foundation.redis.core.serialization;

import org.springframework.data.redis.serializer.RedisSerializer;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.error.RedisErrorCode;

/**
 * 为 StringRedisTemplate 的顶层 Key 增加并移除服务级前缀。
 *
 * @author Codex
 */
public final class RedisKeyPrefixSerializer implements RedisSerializer<String> {

    private static final RedisSerializer<String> STRING_SERIALIZER = RedisSerializer.string();

    private final String mappedPrefix;

    public RedisKeyPrefixSerializer(String keyPrefix) {
        this.mappedPrefix = keyPrefix + ":";
    }

    @Override
    public byte[] serialize(String key) {
        if (key == null) {
            return STRING_SERIALIZER.serialize(null);
        }
        return STRING_SERIALIZER.serialize(mappedPrefix + key);
    }

    @Override
    public String deserialize(byte[] source) {
        String key = STRING_SERIALIZER.deserialize(source);
        if (key == null || key.isEmpty()) {
            return key;
        }
        if (!key.startsWith(mappedPrefix)) {
            throw new FailException(RedisErrorCode.KEY_NAMESPACE_MISMATCH, "Redis Key不属于当前服务Namespace");
        }
        return key.substring(mappedPrefix.length());
    }

    @Override
    public Class<?> getTargetType() {
        return String.class;
    }
}
