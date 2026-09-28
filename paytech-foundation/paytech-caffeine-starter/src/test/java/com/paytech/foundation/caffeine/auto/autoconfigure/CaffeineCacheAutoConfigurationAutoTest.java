package com.paytech.foundation.caffeine.auto.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.foundation.caffeine.api.CaffeineCacheConstants;
import com.paytech.foundation.caffeine.core.autoconfigure.CaffeineCacheAutoConfiguration;
import com.paytech.foundation.caffeine.core.error.CaffeineErrorCode;
import com.paytech.foundation.common.error.FailException;

class CaffeineCacheAutoConfigurationAutoTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CaffeineCacheAutoConfiguration.class, CacheAutoConfiguration.class));

    @SuppressWarnings("unchecked")
    private static com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache(Cache cache) {
        return (com.github.benmanes.caffeine.cache.Cache<Object, Object>)cache.getNativeCache();
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    @Test
    void test_defaultConfigurationCreatesBoundedCacheManager() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CacheManager.class);
            assertThat(context).hasBean(CaffeineCacheConstants.CACHE_MANAGER_BEAN_NAME);
            assertThat(context).doesNotHaveBean("cacheManager");

            CaffeineCacheManager cacheManager = context.getBean(CaffeineCacheManager.class);
            Cache cache = cacheManager.getCache("order-cache");
            assertThat(cache).isNotNull();
            assertThatThrownBy(() -> cache.put("order-1", null)).isInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    void test_cacheableUsesProductionCacheChainAndRecordsStatistics() {
        contextRunner.withUserConfiguration(CachedServiceConfiguration.class).run(context -> {
            CachedService service = context.getBean(CachedService.class);

            assertThat(service.load("order-1")).isEqualTo("value-1");
            assertThat(service.load("order-1")).isEqualTo("value-1");
            assertThat(service.executionCount()).isOne();

            Cache cache = context.getBean(CacheManager.class).getCache("order-cache");
            com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = nativeCache(cache);
            assertThat(nativeCache.stats().hitCount()).isOne();
            assertThat(nativeCache.stats().missCount()).isOne();
            assertThat(nativeCache.policy().eviction())
                .hasValueSatisfying(eviction -> assertThat(eviction.getMaximum()).isEqualTo(10_000));
        });
    }

    @Test
    void test_expiredEntryIsLoadedAgain() {
        contextRunner.withUserConfiguration(CachedServiceConfiguration.class)
            .withPropertyValues("caffeine.expire-after-write=25ms").run(context -> {
                CachedService service = context.getBean(CachedService.class);
                assertThat(service.load("order-1")).isEqualTo("value-1");

                await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                    assertThat(service.load("order-1")).isEqualTo("value-2");
                    assertThat(service.executionCount()).isEqualTo(2);
                });
            });
    }

    @Test
    void test_nullResultFailsFastByDefault() {
        contextRunner.withUserConfiguration(CachedServiceConfiguration.class).run(context -> {
            CachedService service = context.getBean(CachedService.class);

            assertThatThrownBy(() -> service.loadNullable("missing")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null values");
            assertThat(service.nullableExecutionCount()).isOne();
        });
    }

    @Test
    void test_unlessExpressionSkipsNullableResult() {
        contextRunner.withUserConfiguration(CachedServiceConfiguration.class).run(context -> {
            CachedService service = context.getBean(CachedService.class);

            assertThat(service.loadNullableUnless("missing")).isNull();
            assertThat(service.loadNullableUnless("missing")).isNull();
            assertThat(service.nullableUnlessExecutionCount()).isEqualTo(2);
        });
    }

    @Test
    void test_cacheNamesRestrictDynamicallyCreatedCaches() {
        contextRunner.withPropertyValues("caffeine.cache-names=order-cache,client-cache").run(context -> {
            CacheManager cacheManager = context.getBean(CacheManager.class);
            assertThat(cacheManager.getCacheNames()).containsExactlyInAnyOrder("order-cache", "client-cache");
            assertThat(cacheManager.getCache("unknown-cache")).isNull();
        });
    }

    @Test
    void test_unknownCacheNameFailsBeforeExecutingBusinessMethod() {
        contextRunner.withUserConfiguration(CachedServiceConfiguration.class)
            .withPropertyValues("caffeine.cache-names=order-cache").run(context -> {
                CachedService service = context.getBean(CachedService.class);

                assertThatThrownBy(() -> service.loadUnknown("order-1")).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown-cache");
                assertThat(service.unknownExecutionCount()).isZero();
            });
    }

    @Test
    void test_invalidMaximumSizeFailsWithStableErrorCode() {
        contextRunner.withPropertyValues("caffeine.maximum-size=0").run(context -> {
            assertThat(context).hasFailed();
            Throwable rootCause = rootCause(context.getStartupFailure());
            assertThat(rootCause).isInstanceOf(FailException.class);
            assertThat(((FailException)rootCause).getCode())
                .isEqualTo(CaffeineErrorCode.CONFIGURATION_INVALID.getCode());
        });
    }

    @Test
    void test_subMillisecondExpirationFailsFast() {
        contextRunner.withPropertyValues("caffeine.expire-after-write=1ns").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCause(context.getStartupFailure())).isInstanceOf(FailException.class)
                .hasMessageContaining("至少为1毫秒");
        });
    }

    @Test
    void test_caffeineCacheManagerCoexistsWithHostDefaultCacheManager() {
        ConcurrentMapCacheManager customCacheManager = new ConcurrentMapCacheManager("custom-cache");
        contextRunner.withBean("cacheManager", CacheManager.class, () -> customCacheManager,
            beanDefinition -> beanDefinition.setPrimary(true)).run(context -> {
                assertThat(context.getBeansOfType(CacheManager.class)).hasSize(2);
                assertThat(context.getBean(CacheManager.class)).isSameAs(customCacheManager);
                assertThat(context.getBean(CaffeineCacheManager.class))
                    .isSameAs(context.getBean(CaffeineCacheConstants.CACHE_MANAGER_BEAN_NAME));
            });
    }

    @Test
    void test_namedHostCaffeineCacheManagerMakesAutoConfigurationBackOff() {
        CaffeineCacheManager customCacheManager = new CaffeineCacheManager("custom-cache");
        contextRunner.withBean(CaffeineCacheConstants.CACHE_MANAGER_BEAN_NAME, CaffeineCacheManager.class,
            () -> customCacheManager).run(context -> {
                assertThat(context).hasSingleBean(CacheManager.class);
                assertThat(context.getBean(CaffeineCacheConstants.CACHE_MANAGER_BEAN_NAME))
                    .isSameAs(customCacheManager);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class CachedServiceConfiguration {

        @Bean
        CachedService cachedService() {
            return new CachedService();
        }
    }

    static class CachedService {

        private final AtomicInteger executions = new AtomicInteger();
        private final AtomicInteger unknownExecutions = new AtomicInteger();
        private final AtomicInteger nullableExecutions = new AtomicInteger();
        private final AtomicInteger nullableUnlessExecutions = new AtomicInteger();

        @Cacheable(cacheNames = "order-cache", sync = true)
        public String load(String key) {
            return "value-" + executions.incrementAndGet();
        }

        @Cacheable(cacheNames = "unknown-cache")
        public String loadUnknown(String key) {
            return "value-" + unknownExecutions.incrementAndGet();
        }

        @Cacheable(cacheNames = "nullable-cache")
        public String loadNullable(String key) {
            nullableExecutions.incrementAndGet();
            return null;
        }

        @Cacheable(cacheNames = "nullable-unless-cache", unless = "#result == null")
        public String loadNullableUnless(String key) {
            nullableUnlessExecutions.incrementAndGet();
            return null;
        }

        int executionCount() {
            return executions.get();
        }

        int unknownExecutionCount() {
            return unknownExecutions.get();
        }

        int nullableExecutionCount() {
            return nullableExecutions.get();
        }

        int nullableUnlessExecutionCount() {
            return nullableUnlessExecutions.get();
        }
    }
}
