package com.paytech.foundation.redis.core.cache;

import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.api.RedisCacheDefinition;
import com.paytech.foundation.redis.core.error.RedisErrorCode;
import com.paytech.foundation.redis.core.serialization.RedisJsonObjectMapperFactory;

import tools.jackson.databind.ObjectMapper;

/**
 * 根据宿主服务声明的缓存名称、值类型和 TTL 创建 RedisCacheManager。
 *
 * @author Codex
 */
public final class RedisCacheManagerFactory {

    private static final int MAXIMUM_CACHE_NAME_LENGTH = 128;
    private static final Duration MINIMUM_TTL = Duration.ofMillis(1);
    private static final Pattern CACHE_NAME_PATTERN = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");

    private final ObjectMapper objectMapper = RedisJsonObjectMapperFactory.create();

    /**
     * 创建只允许访问已注册缓存名称的默认 Redis CacheManager。
     *
     * @param connectionFactory
     *            Redis 连接工厂
     * @param keyPrefix
     *            当前服务的 Redis Key 前缀
     * @param definitions
     *            宿主服务声明的 Redis 缓存
     * @return Redis CacheManager
     */
    public RedisCacheManager create(RedisConnectionFactory connectionFactory, String keyPrefix,
        Collection<RedisCacheDefinition<?>> definitions) {
        Map<String, RedisCacheConfiguration> cacheConfigurations = buildCacheConfigurations(keyPrefix, definitions);
        RedisCacheConfiguration defaultConfiguration = baseConfiguration(keyPrefix)
            .serializeValuesWith(SerializationPair.fromSerializer(RedisSerializer.byteArray()));
        RedisCacheWriter cacheWriter =
            RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory, BatchStrategies.scan(1_000));

        return RedisCacheManager.builder(cacheWriter).cacheDefaults(defaultConfiguration)
            .withInitialCacheConfigurations(cacheConfigurations).disableCreateOnMissingCache().transactionAware()
            .enableStatistics().build();
    }

    private Map<String, RedisCacheConfiguration> buildCacheConfigurations(String keyPrefix,
        Collection<RedisCacheDefinition<?>> definitions) {
        Map<String, RedisCacheConfiguration> cacheConfigurations = new LinkedHashMap<>();
        for (RedisCacheDefinition<?> definition : definitions) {
            validateDefinition(definition);
            RedisCacheConfiguration configuration = baseConfiguration(keyPrefix).entryTtl(definition.ttl())
                .serializeValuesWith(SerializationPair.fromSerializer(
                    new TypedRedisJsonSerializer(definition.cacheName(), definition.valueType(), objectMapper)));
            if (cacheConfigurations.putIfAbsent(definition.cacheName(), configuration) != null) {
                throw configurationError("缓存名称重复: " + definition.cacheName());
            }
        }
        return cacheConfigurations;
    }

    private RedisCacheConfiguration baseConfiguration(String keyPrefix) {
        return RedisCacheConfiguration.defaultCacheConfig().disableCachingNullValues()
            .prefixCacheNameWith(keyPrefix + ":cache:")
            .serializeKeysWith(SerializationPair.fromSerializer(RedisSerializer.string()));
    }

    private void validateDefinition(RedisCacheDefinition<?> definition) {
        if (definition == null) {
            throw configurationError("缓存定义不能为空");
        }
        validateCacheName(definition.cacheName());
        validateValueType(definition.valueType());
        if (definition.ttl() == null || definition.ttl().compareTo(MINIMUM_TTL) < 0) {
            throw configurationError("缓存[" + definition.cacheName() + "] TTL必须至少为1毫秒");
        }
    }

    private void validateCacheName(String cacheName) {
        if (cacheName == null || cacheName.isBlank()) {
            throw configurationError("缓存名称不能为空");
        }
        if (cacheName.length() > MAXIMUM_CACHE_NAME_LENGTH || !CACHE_NAME_PATTERN.matcher(cacheName).matches()) {
            throw configurationError("缓存名称必须使用小写kebab-case且不能超过128个字符: " + cacheName);
        }
    }

    private void validateValueType(Class<?> valueType) {
        if (valueType == null || valueType == Object.class || valueType == Void.class || valueType == Void.TYPE
            || valueType.isPrimitive() || valueType.isArray() || valueType.isInterface()
            || Collection.class.isAssignableFrom(valueType) || Map.class.isAssignableFrom(valueType)
            || Optional.class.isAssignableFrom(valueType) || valueType.getTypeParameters().length > 0
            || (valueType.isMemberClass() && !Modifier.isStatic(valueType.getModifiers()))
            || Modifier.isAbstract(valueType.getModifiers())) {
            throw configurationError("缓存值必须声明可实例化的具体类型");
        }
    }

    private FailException configurationError(String message) {
        return new FailException(RedisErrorCode.CONFIGURATION_INVALID, "redis.cache." + message);
    }
}
