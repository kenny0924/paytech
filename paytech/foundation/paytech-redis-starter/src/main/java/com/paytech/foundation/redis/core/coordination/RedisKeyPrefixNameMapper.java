package com.paytech.foundation.redis.core.coordination;

import org.redisson.config.NameMapper;

/**
 * 为 Redisson 协调对象统一增加服务级 Key 前缀。
 *
 * @author Codex
 */
final class RedisKeyPrefixNameMapper implements NameMapper {

    private final String mappedPrefix;

    RedisKeyPrefixNameMapper(String keyPrefix) {
        this.mappedPrefix = keyPrefix + ":";
    }

    @Override
    public String map(String name) {
        return mappedPrefix + name;
    }

    @Override
    public String unmap(String name) {
        if (!name.startsWith(mappedPrefix)) {
            return name;
        }
        return name.substring(mappedPrefix.length());
    }
}
