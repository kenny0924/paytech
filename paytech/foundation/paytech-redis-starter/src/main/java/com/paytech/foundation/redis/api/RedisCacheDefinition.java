package com.paytech.foundation.redis.api;

import java.time.Duration;

/**
 * 声明一个可供 Spring Cache 使用的 Redis 缓存。
 *
 * <p>
 * 一个缓存名称只能保存一种明确的值类型。Starter 使用该类型完成不带 Java 类型元数据的 JSON 反序列化，并使用 {@code ttl} 设置缓存条目的过期时间。
 *
 * @param cacheName
 *            缓存名称
 * @param valueType
 *            缓存值的具体类型
 * @param ttl
 *            缓存条目过期时间
 * @param <T>
 *            缓存值类型
 * @author Codex
 */
public record RedisCacheDefinition<T>(String cacheName, Class<T> valueType, Duration ttl) {

    /**
     * 创建 Redis 缓存定义。
     *
     * @param cacheName
     *            缓存名称
     * @param valueType
     *            缓存值的具体类型
     * @param ttl
     *            缓存条目过期时间
     * @param <T>
     *            缓存值类型
     * @return Redis 缓存定义
     */
    public static <T> RedisCacheDefinition<T> of(String cacheName, Class<T> valueType, Duration ttl) {
        return new RedisCacheDefinition<>(cacheName, valueType, ttl);
    }
}
