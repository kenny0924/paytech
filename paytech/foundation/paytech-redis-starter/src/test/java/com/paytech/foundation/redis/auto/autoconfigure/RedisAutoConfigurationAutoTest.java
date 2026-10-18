package com.paytech.foundation.redis.auto.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.query.SortQuery;
import org.springframework.data.redis.core.query.SortQueryBuilder;
import org.springframework.data.redis.serializer.RedisSerializer;

import com.paytech.foundation.caffeine.api.CaffeineCacheConstants;
import com.paytech.foundation.caffeine.core.autoconfigure.CaffeineCacheAutoConfiguration;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.api.RedisCacheDefinition;
import com.paytech.foundation.redis.api.RedisJsonTemplate;
import com.paytech.foundation.redis.core.autoconfigure.RedisAutoConfiguration;
import com.paytech.foundation.redis.core.autoconfigure.RedisCacheAutoConfiguration;
import com.paytech.foundation.redis.core.autoconfigure.RedisKeyNamespaceAutoConfiguration;
import com.paytech.foundation.redis.core.template.NamespacedStringRedisTemplate;

class RedisAutoConfigurationAutoTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(DataRedisAutoConfiguration.class, RedisKeyNamespaceAutoConfiguration.class,
                RedisAutoConfiguration.class, RedisCacheAutoConfiguration.class, CacheAutoConfiguration.class))
        .withPropertyValues("spring.application.name=payment-service");

    @Test
    void keepsLettuceAndDisablesRedissonByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LettuceConnectionFactory.class);
            assertThat(context).hasSingleBean(StringRedisTemplate.class);
            assertThat(context).hasSingleBean(RedisJsonTemplate.class);
            assertThat(context.getBean(StringRedisTemplate.class)).isInstanceOf(NamespacedStringRedisTemplate.class);
            assertThat(context.getBean("redisTemplate")).isSameAs(context.getBean(StringRedisTemplate.class));
            assertThat(context).hasSingleBean(RedisCacheManager.class);
            assertThat(context.getBean("cacheManager")).isSameAs(context.getBean("redisCacheManager"));
            assertThat(context.getBean(CacheManager.class)).isSameAs(context.getBean(RedisCacheManager.class));
            assertThat(context.getBean(RedisCacheManager.class).getCacheNames()).isEmpty();
            assertThat(context.getBean(RedisCacheManager.class).getCache("unknown-cache")).isNull();
            assertThat(context).doesNotHaveBean(RedissonClient.class);
        });
    }

    @Test
    void requiresApplicationNameOrExplicitKeyPrefix() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class,
            RedisKeyNamespaceAutoConfiguration.class, RedisAutoConfiguration.class, RedisCacheAutoConfiguration.class))
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(FailException.class);
            });
    }

    @Test
    void registersOnlyExplicitlyTypedRedisCaches() {
        contextRunner
            .withBean("paymentCacheDefinition", RedisCacheDefinition.class,
                () -> RedisCacheDefinition.of("payment-cache", CachedValue.class, Duration.ofMinutes(5)))
            .run(context -> {
                RedisCacheManager cacheManager = context.getBean(RedisCacheManager.class);

                assertThat(cacheManager.getCacheNames()).containsExactly("payment-cache");
                assertThat(cacheManager.getCache("payment-cache")).isNotNull();
                assertThat(cacheManager.getCache("unknown-cache")).isNull();
            });
    }

    @Test
    void rejectsDuplicateRedisCacheNames() {
        contextRunner
            .withBean("firstPaymentCache", RedisCacheDefinition.class,
                () -> RedisCacheDefinition.of("payment-cache", CachedValue.class, Duration.ofMinutes(5)))
            .withBean("secondPaymentCache", RedisCacheDefinition.class,
                () -> RedisCacheDefinition.of("payment-cache", CachedValue.class, Duration.ofMinutes(10)))
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(FailException.class)
                    .hasRootCauseMessage("redis.cache.缓存名称重复: payment-cache");
            });
    }

    @Test
    void rejectsUntypedRedisCacheValues() {
        contextRunner.withBean("untypedCache", RedisCacheDefinition.class,
            () -> RedisCacheDefinition.of("untyped-cache", Object.class, Duration.ofMinutes(5))).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(FailException.class)
                    .hasRootCauseMessage("redis.cache.缓存值必须声明可实例化的具体类型");
            });
    }

    @Test
    void rejectsPrimitiveRedisCacheValues() {
        contextRunner.withBean("primitiveCache", RedisCacheDefinition.class,
            () -> RedisCacheDefinition.of("primitive-cache", int.class, Duration.ofMinutes(5))).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(FailException.class)
                    .hasRootCauseMessage("redis.cache.缓存值必须声明可实例化的具体类型");
            });
    }

    @Test
    void rejectsGenericRedisCacheValues() {
        contextRunner
            .withBean("genericCache", RedisCacheDefinition.class,
                () -> RedisCacheDefinition.of("generic-cache", GenericCachedValue.class, Duration.ofMinutes(5)))
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(FailException.class)
                    .hasRootCauseMessage("redis.cache.缓存值必须声明可实例化的具体类型");
            });
    }

    @Test
    void unknownRedisCacheFailsBeforeExecutingBusinessMethod() {
        contextRunner.withBean(UnknownCacheService.class).run(context -> {
            UnknownCacheService service = context.getBean(UnknownCacheService.class);

            assertThatThrownBy(() -> service.load("payment-1001")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unregistered-cache");
            assertThat(service.executionCount()).isZero();
        });
    }

    @Test
    void keepsRedisDefaultAndCaffeineExplicitWhenBothStartersArePresent() {
        contextRunner.withConfiguration(AutoConfigurations.of(CaffeineCacheAutoConfiguration.class))
            .withBean("paymentCacheDefinition", RedisCacheDefinition.class,
                () -> RedisCacheDefinition.of("payment-cache", CachedValue.class, Duration.ofMinutes(5)))
            .run(context -> {
                assertThat(context.getBeansOfType(CacheManager.class)).hasSize(2);
                assertThat(context.getBean(CacheManager.class)).isInstanceOf(RedisCacheManager.class);
                assertThat(context.getBean("cacheManager")).isSameAs(context.getBean("redisCacheManager"));
                assertThat(context.getBean(CaffeineCacheConstants.CACHE_MANAGER_BEAN_NAME))
                    .isInstanceOf(CaffeineCacheManager.class);
            });
    }

    @Test
    void requiresExplicitConnectionAndCommandTimeouts() {
        contextRunner.withPropertyValues("redis.coordination.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(FailException.class);
        });
    }

    @Test
    void usesExplicitServiceKeyPrefixOverride() {
        contextRunner.withPropertyValues("redis.key-prefix=payment-service:test").run(context -> {
            StringRedisTemplate redisTemplate = context.getBean(StringRedisTemplate.class);
            byte[] serializedKey = keySerializer(redisTemplate).serialize("cache:sample");

            assertThat(new String(serializedKey, StandardCharsets.UTF_8))
                .isEqualTo("payment-service:test:cache:sample");
        });
    }

    @Test
    void rejectsInvalidJsonWriteArgumentsBeforeAccessingRedis() {
        contextRunner.run(context -> {
            RedisJsonTemplate redisJsonTemplate = context.getBean(RedisJsonTemplate.class);

            assertThatThrownBy(() -> redisJsonTemplate.set("cache:sample", "value", Duration.ZERO))
                .isInstanceOf(FailException.class).hasMessage("Redis JSON缓存TTL不能小于1毫秒");
            assertThatThrownBy(() -> redisJsonTemplate.set("cache:sample", "value", Duration.ofNanos(1)))
                .isInstanceOf(FailException.class).hasMessage("Redis JSON缓存TTL不能小于1毫秒");
            assertThatThrownBy(() -> redisJsonTemplate.set("cache:sample", null, Duration.ofMinutes(1)))
                .isInstanceOf(FailException.class).hasMessage("Redis JSON缓存值不能为空");
        });
    }

    @Test
    void rejectsSortPatternsThatCanBypassKeyNamespace() {
        contextRunner.run(context -> {
            StringRedisTemplate redisTemplate = context.getBean(StringRedisTemplate.class);
            SortQuery<String> sortQuery = SortQueryBuilder.sort("order:list").by("order:weight:*").build();

            assertThatThrownBy(() -> redisTemplate.sort(sortQuery)).isInstanceOf(FailException.class)
                .hasMessage("服务级Key隔离模式不支持SORT BY/GET跨Key模式");
        });
    }

    @Test
    void rejectsUnapprovedClusterModeBeforeConnecting() {
        contextRunner.withPropertyValues("spring.data.redis.cluster.nodes=127.0.0.1:6379",
            "spring.data.redis.connect-timeout=2s", "spring.data.redis.timeout=2s",
            "spring.application.name=payment-service", "redis.coordination.enabled=true").run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(FailException.class)
                    .hasRootCauseMessage("Redisson协调能力当前只支持Redis单机模式");
            });
    }

    @SuppressWarnings("unchecked")
    private RedisSerializer<String> keySerializer(StringRedisTemplate redisTemplate) {
        return (RedisSerializer<String>)redisTemplate.getKeySerializer();
    }

    private record CachedValue(String id) {
    }

    private record GenericCachedValue<T>(T value) {
    }

    static class UnknownCacheService {

        private final AtomicInteger executions = new AtomicInteger();

        @Cacheable(cacheNames = "unregistered-cache")
        public String load(String key) {
            executions.incrementAndGet();
            return key;
        }

        int executionCount() {
            return executions.get();
        }
    }
}
