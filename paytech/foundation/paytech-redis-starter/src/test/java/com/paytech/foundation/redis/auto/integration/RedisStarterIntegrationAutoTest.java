package com.paytech.foundation.redis.auto.integration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.paytech.foundation.caffeine.api.CaffeineCacheConstants;
import com.paytech.foundation.caffeine.core.autoconfigure.CaffeineCacheAutoConfiguration;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.redis.api.RedisCacheDefinition;
import com.paytech.foundation.redis.api.RedisJsonTemplate;
import com.paytech.foundation.redis.core.autoconfigure.RedisAutoConfiguration;
import com.paytech.foundation.redis.core.autoconfigure.RedisCacheAutoConfiguration;
import com.paytech.foundation.redis.core.autoconfigure.RedisKeyNamespaceAutoConfiguration;
import com.paytech.foundation.redis.test.infrastructure.RedisLocalEnvironmentPrecondition;
import com.paytech.foundation.redis.test.infrastructure.RedisTestNamespace;

import tools.jackson.core.type.TypeReference;

class RedisStarterIntegrationAutoTest {

    private static final RedisTestNamespace NAMESPACE = RedisTestNamespace.unique("core");
    private static String password;

    @BeforeAll
    static void verifyLocalRedis() {
        password = RedisLocalEnvironmentPrecondition.requirePasswordAndVerify();
    }

    @Test
    void keepsLettuceForValuesAndUsesNamespacedRedissonLocks() throws Exception {
        AtomicReference<RedissonClient> redissonClientReference = new AtomicReference<>();

        contextRunner(NAMESPACE).run(context -> {
            RedisConnectionFactory connectionFactory = context.getBean(RedisConnectionFactory.class);
            StringRedisTemplate redisTemplate = context.getBean(StringRedisTemplate.class);
            RedisJsonTemplate redisJsonTemplate = context.getBean(RedisJsonTemplate.class);
            StringRedisTemplate rawRedisTemplate = new StringRedisTemplate(connectionFactory);
            RedissonClient redissonClient = context.getBean(RedissonClient.class);
            redissonClientReference.set(redissonClient);

            try {
                assertThat(connectionFactory).isInstanceOf(LettuceConnectionFactory.class);
                assertThat(context.getBean(CacheManager.class)).isInstanceOf(RedisCacheManager.class);
                assertThat(context.getBean(CaffeineCacheConstants.CACHE_MANAGER_BEAN_NAME))
                    .isInstanceOf(CaffeineCacheManager.class);
                verifyValueTtl(redisTemplate, rawRedisTemplate);
                verifyJsonValue(redisJsonTemplate, rawRedisTemplate);
                verifyMixedSpringCaches(context.getBean(MixedCacheService.class), rawRedisTemplate);
                verifyMultiKeyAndScan(redisTemplate, rawRedisTemplate);
                verifyHashFieldIsNotPrefixed(redisTemplate, rawRedisTemplate);
                verifyLockNamespaceAndContention(rawRedisTemplate, redissonClient);
                verifyLockLeaseExpiry(redissonClient);
            } finally {
                NAMESPACE.clean(connectionFactory);
            }
        });

        assertThat(redissonClientReference.get()).isNotNull();
        assertThat(redissonClientReference.get().isShutdown()).isTrue();
    }

    @Test
    void appliesExplicitKeyPrefixToLettuceRedissonAndSpringCache() {
        RedisTestNamespace override = RedisTestNamespace.unique("override");
        String overriddenPrefix = override.value();
        contextRunner(NAMESPACE).withPropertyValues("redis.key-prefix=" + overriddenPrefix).run(context -> {
            RedisConnectionFactory connectionFactory = context.getBean(RedisConnectionFactory.class);
            StringRedisTemplate redisTemplate = context.getBean(StringRedisTemplate.class);
            StringRedisTemplate rawRedisTemplate = new StringRedisTemplate(connectionFactory);
            RedissonClient redissonClient = context.getBean(RedissonClient.class);
            MixedCacheService mixedCacheService = context.getBean(MixedCacheService.class);
            String valueKey = "cache:" + UUID.randomUUID();
            String lockName = "lock:" + UUID.randomUUID();
            String paymentId = "payment:" + UUID.randomUUID();
            RLock lock = redissonClient.getLock(lockName);

            try {
                redisTemplate.opsForValue().set(valueKey, "available", Duration.ofSeconds(5));
                assertThat(rawRedisTemplate.hasKey(overriddenPrefix + ":" + valueKey)).isTrue();

                assertThat(lock.tryLock()).isTrue();
                try {
                    assertThat(rawRedisTemplate.hasKey(overriddenPrefix + ":" + lockName)).isTrue();
                    mixedCacheService.loadRedis(paymentId);
                    String expectedCacheKey = overriddenPrefix + ":cache:redis-payment-cache::" + paymentId;
                    await().atMost(Duration.ofSeconds(2))
                        .untilAsserted(() -> assertThat(rawRedisTemplate.hasKey(expectedCacheKey)).isTrue());
                    assertThat(rawRedisTemplate.hasKey(NAMESPACE.physical("cache:redis-payment-cache::" + paymentId)))
                        .isFalse();
                } finally {
                    lock.unlock();
                }
            } finally {
                override.clean(connectionFactory);
            }
        });
    }

    @Test
    void cleanupDeletesOnlyTheCurrentUnpredictableNamespaceUsingScan() {
        RedisTestNamespace owner = RedisTestNamespace.unique("cleanup-owner");
        RedisTestNamespace neighbor = RedisTestNamespace.unique("cleanup-neighbor");
        contextRunner(NAMESPACE).run(context -> {
            RedisConnectionFactory connectionFactory = context.getBean(RedisConnectionFactory.class);
            StringRedisTemplate raw = new StringRedisTemplate(connectionFactory);
            raw.opsForValue().set(owner.physical("owned"), "1");
            raw.opsForValue().set(neighbor.physical("sentinel"), "1");
            try {
                owner.clean(connectionFactory);
                assertThat(raw.hasKey(owner.physical("owned"))).isFalse();
                assertThat(raw.hasKey(neighbor.physical("sentinel"))).isTrue();
            } finally {
                owner.clean(connectionFactory);
                neighbor.clean(connectionFactory);
                NAMESPACE.clean(connectionFactory);
            }
        });
    }

    private ApplicationContextRunner contextRunner(RedisTestNamespace namespace) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class,
                RedisKeyNamespaceAutoConfiguration.class, RedisAutoConfiguration.class,
                RedisCacheAutoConfiguration.class, CaffeineCacheAutoConfiguration.class, CacheAutoConfiguration.class))
            .withUserConfiguration(MixedCacheTestConfiguration.class)
            .withPropertyValues("spring.data.redis.client-type=lettuce",
                "spring.data.redis.host=" + RedisLocalEnvironmentPrecondition.HOST,
                "spring.data.redis.port=" + RedisLocalEnvironmentPrecondition.PORT,
                "spring.data.redis.password=" + password,
                "spring.data.redis.connect-timeout=2s", "spring.data.redis.timeout=2s",
                "spring.data.redis.client-name=redis-starter-auto-test", "spring.application.name=" + namespace.value(),
                "caffeine.cache-names=local-payment-cache", "redis.coordination.enabled=true",
                "redis.coordination.threads=2", "redis.coordination.netty-threads=2",
                "redis.coordination.connection-minimum-idle-size=1", "redis.coordination.connection-pool-size=4",
                "redis.coordination.subscription-connection-minimum-idle-size=1",
                "redis.coordination.subscription-connection-pool-size=2");
    }

    private void verifyValueTtl(StringRedisTemplate redisTemplate, StringRedisTemplate rawRedisTemplate) {
        String key = "cache:" + UUID.randomUUID();
        redisTemplate.opsForValue().set(key, "available", Duration.ofMillis(500));

        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("available");
        assertThat(rawRedisTemplate.opsForValue().get(NAMESPACE.physical(key))).isEqualTo("available");
        assertThat(redisTemplate.getExpire(key, MILLISECONDS)).isBetween(1L, 500L);
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(redisTemplate.hasKey(key)).isFalse());
    }

    private void verifyJsonValue(RedisJsonTemplate redisJsonTemplate, StringRedisTemplate rawRedisTemplate) {
        String key = "cache:payment:" + UUID.randomUUID();
        CachedPayment cachedPayment = new CachedPayment("payment-1001", PaymentCacheStatus.PROCESSING);
        redisJsonTemplate.set(key, cachedPayment, Duration.ofSeconds(5));

        String storedJson = rawRedisTemplate.opsForValue().get(NAMESPACE.physical(key));
        assertThat(storedJson).contains("\"paymentId\":\"payment-1001\"").contains("\"status\":\"payment_processing\"")
            .doesNotContain("PROCESSING").doesNotContain("@class", "_type", CachedPayment.class.getName());
        assertThat(redisJsonTemplate.get(key, CachedPayment.class)).isEqualTo(cachedPayment);

        rawRedisTemplate.opsForValue().set(NAMESPACE.physical(key),
            "{\"paymentId\":\"payment-1001\",\"status\":\"payment_processing\",\"futureField\":true}",
            Duration.ofSeconds(5));
        assertThat(redisJsonTemplate.get(key, CachedPayment.class)).isEqualTo(cachedPayment);

        String listKey = "cache:payment-list:" + UUID.randomUUID();
        List<CachedPayment> cachedPayments =
            List.of(cachedPayment, new CachedPayment("payment-1002", PaymentCacheStatus.SUCCEEDED));
        redisJsonTemplate.set(listKey, cachedPayments, Duration.ofSeconds(5));
        assertThat(redisJsonTemplate.get(listKey, new TypeReference<List<CachedPayment>>() {}))
            .containsExactlyElementsOf(cachedPayments);

        String mapKey = "cache:payment-statuses:" + UUID.randomUUID();
        Map<PaymentCacheStatus, String> statusDescriptions = Map.of(PaymentCacheStatus.PROCESSING, "处理中");
        redisJsonTemplate.set(mapKey, statusDescriptions, Duration.ofSeconds(5));
        assertThat(rawRedisTemplate.opsForValue().get(NAMESPACE.physical(mapKey)))
            .isEqualTo("{\"payment_processing\":\"处理中\"}");
        assertThat(redisJsonTemplate.get(mapKey, new TypeReference<Map<PaymentCacheStatus, String>>() {}))
            .isEqualTo(statusDescriptions);

        assertThat(redisJsonTemplate.setIfAbsent(key, new CachedPayment("payment-1003", PaymentCacheStatus.FAILED),
            Duration.ofSeconds(5))).isFalse();
        assertThat(redisJsonTemplate.delete(key)).isTrue();
        assertThat(redisJsonTemplate.get(key, CachedPayment.class)).isNull();
        redisJsonTemplate.delete(listKey);
        redisJsonTemplate.delete(mapKey);
    }

    private void verifyMixedSpringCaches(MixedCacheService service, StringRedisTemplate rawRedisTemplate) {
        String redisPaymentId = "redis-payment-" + UUID.randomUUID();
        CachedPayment firstRedisValue = service.loadRedis(redisPaymentId);
        String physicalRedisKey = NAMESPACE.physical("cache:redis-payment-cache::" + redisPaymentId);
        await().atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(rawRedisTemplate.hasKey(physicalRedisKey)).isTrue());

        assertThat(service.loadRedis(redisPaymentId)).isEqualTo(firstRedisValue);
        assertThat(service.redisExecutions()).isOne();
        String storedJson = rawRedisTemplate.opsForValue().get(physicalRedisKey);
        assertThat(storedJson).contains("\"paymentId\":\"" + redisPaymentId + "\"")
            .contains("\"status\":\"payment_processing\"")
            .doesNotContain("@class", "_type", CachedPayment.class.getName(), "PROCESSING");
        assertThat(rawRedisTemplate.getExpire(physicalRedisKey, MILLISECONDS)).isBetween(1L, 5_000L);

        String localPaymentId = "local-payment-" + UUID.randomUUID();
        CachedPayment firstLocalValue = service.loadLocal(localPaymentId);
        assertThat(service.loadLocal(localPaymentId)).isEqualTo(firstLocalValue);
        assertThat(service.localExecutions()).isOne();
        assertThat(rawRedisTemplate.hasKey(NAMESPACE.physical("cache:local-payment-cache::" + localPaymentId))).isFalse();

        rawRedisTemplate.delete(physicalRedisKey);
    }

    private void verifyMultiKeyAndScan(StringRedisTemplate redisTemplate, StringRedisTemplate rawRedisTemplate) {
        String batchId = UUID.randomUUID().toString();
        String firstKey = "batch:" + batchId + ":first";
        String secondKey = "batch:" + batchId + ":second";
        redisTemplate.opsForValue().multiSet(Map.of(firstKey, "1", secondKey, "2"));

        assertThat(rawRedisTemplate.hasKey(NAMESPACE.physical(firstKey))).isTrue();
        assertThat(rawRedisTemplate.hasKey(NAMESPACE.physical(secondKey))).isTrue();

        ScanOptions scanOptions = ScanOptions.scanOptions().match("batch:" + batchId + ":*").count(10).build();
        try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
            assertThat(cursor.stream().toList()).containsExactlyInAnyOrder(firstKey, secondKey);
        }

        assertThat(redisTemplate.delete(List.of(firstKey, secondKey))).isEqualTo(2L);
    }

    private void verifyHashFieldIsNotPrefixed(StringRedisTemplate redisTemplate, StringRedisTemplate rawRedisTemplate) {
        String key = "profile:" + UUID.randomUUID();
        redisTemplate.opsForHash().put(key, "status", "ACTIVE");

        assertThat(rawRedisTemplate.opsForHash().entries(NAMESPACE.physical(key))).isEqualTo(Map.of("status", "ACTIVE"));
        redisTemplate.delete(key);
    }

    private void verifyLockNamespaceAndContention(StringRedisTemplate rawRedisTemplate, RedissonClient redissonClient)
        throws Exception {
        String lockName = "payment:" + UUID.randomUUID();
        RLock lock = redissonClient.getLock(lockName);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            assertThat(lock.tryLock(0, 2, SECONDS)).isTrue();
            assertThat(rawRedisTemplate.hasKey(NAMESPACE.physical(lockName))).isTrue();

            Future<Boolean> blocked = executor.submit(() -> tryLockAndRelease(redissonClient, lockName, 150));
            assertThat(blocked.get(2, SECONDS)).isFalse();

            lock.unlock();
            Future<Boolean> acquired = executor.submit(() -> tryLockAndRelease(redissonClient, lockName, 500));
            assertThat(acquired.get(2, SECONDS)).isTrue();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            executor.shutdownNow();
        }
    }

    private void verifyLockLeaseExpiry(RedissonClient redissonClient) throws Exception {
        String lockName = "lease:" + UUID.randomUUID();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> leaseOwner =
                executor.submit(() -> redissonClient.getLock(lockName).tryLock(0, 300, MILLISECONDS));
            assertThat(leaseOwner.get(2, SECONDS)).isTrue();

            RLock lockAfterExpiry = redissonClient.getLock(lockName);
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
                assertThat(lockAfterExpiry.tryLock(0, 1, SECONDS)).isTrue();
                lockAfterExpiry.unlock();
            });
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean tryLockAndRelease(RedissonClient redissonClient, String lockName, long waitMillis)
        throws InterruptedException {
        RLock lock = redissonClient.getLock(lockName);
        boolean acquired = lock.tryLock(waitMillis, 1_000, MILLISECONDS);
        if (acquired) {
            lock.unlock();
        }
        return acquired;
    }

    private enum PaymentCacheStatus implements CodeEnum {

        FAILED("payment_failed"), PROCESSING("payment_processing"), SUCCEEDED("payment_succeeded");

        private final String code;

        PaymentCacheStatus(String code) {
            this.code = code;
        }

        @Override
        public String getCode() {
            return code;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MixedCacheTestConfiguration {

        @Bean
        RedisCacheDefinition<CachedPayment> redisPaymentCacheDefinition() {
            return RedisCacheDefinition.of("redis-payment-cache", CachedPayment.class, Duration.ofSeconds(5));
        }

        @Bean
        MixedCacheService mixedCacheService() {
            return new MixedCacheService();
        }
    }

    static class MixedCacheService {

        private final AtomicInteger redisExecutionCount = new AtomicInteger();
        private final AtomicInteger localExecutionCount = new AtomicInteger();

        @Cacheable(cacheNames = "redis-payment-cache", key = "#root.args[0]")
        public CachedPayment loadRedis(String paymentId) {
            redisExecutionCount.incrementAndGet();
            return new CachedPayment(paymentId, PaymentCacheStatus.PROCESSING);
        }

        @Cacheable(cacheNames = "local-payment-cache", key = "#root.args[0]",
            cacheManager = CaffeineCacheConstants.CACHE_MANAGER_BEAN_NAME, sync = true)
        public CachedPayment loadLocal(String paymentId) {
            localExecutionCount.incrementAndGet();
            return new CachedPayment(paymentId, PaymentCacheStatus.PROCESSING);
        }

        int redisExecutions() {
            return redisExecutionCount.get();
        }

        int localExecutions() {
            return localExecutionCount.get();
        }
    }

    private record CachedPayment(String paymentId, PaymentCacheStatus status) {
    }
}
