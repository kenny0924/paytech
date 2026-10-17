package com.paytech.gateway.test.redis;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Assertions;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.types.Expiration;

import reactor.core.publisher.Mono;

/** 只清理当前运行 Guard 与请求幂等前缀的 Redis 测试工具。 */
public final class RedisTestCleaner {
    private static final int SCAN_PAGE_SIZE = 64;
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(5);
    private static final long SENTINEL_TTL_MILLIS = Duration.ofMinutes(30).toMillis();
    private static final ByteBuffer SENTINEL_VALUE = utf8("owned-sentinel");

    private final ReactiveRedisConnectionFactory connectionFactory;
    private final RedisTestNamespace namespace;

    public RedisTestCleaner(ReactiveRedisConnectionFactory connectionFactory, RedisTestNamespace namespace) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "Redis 连接工厂不可为空");
        this.namespace = Objects.requireNonNull(namespace, "Redis 测试命名空间不可为空");
    }

    private static ByteBuffer utf8(String value) {
        return StandardCharsets.UTF_8.encode(value);
    }

    private static String decode(ByteBuffer value) {
        return StandardCharsets.UTF_8.decode(value.asReadOnlyBuffer()).toString();
    }

    public void cleanGuardKeys() {
        cleanOwned(namespace.guardScanPattern());
        cleanOwned(namespace.idempotencyScanPattern());
    }

    public List<String> idempotencyKeys() {
        return withConnectionResult(connection -> connection.keyCommands()
            .scan(ScanOptions.scanOptions().match(namespace.idempotencyScanPattern()).count(SCAN_PAGE_SIZE).build())
            .map(RedisTestCleaner::decode).doOnNext(this::validateOwnedGuardKey).collectList());
    }

    /** 以非 Hash 值占据当前运行拥有的幂等 Key，制造真实 Redis WRONGTYPE 命令失败。 */
    public void occupyIdempotencyKeyWithString(String key) {
        validateOwnedGuardKey(key);
        Boolean created = withConnectionResult(connection -> connection.stringCommands().set(utf8(key),
            utf8("not-a-hash"), Expiration.milliseconds(SENTINEL_TTL_MILLIS), RedisStringCommands.SetOption.UPSERT));
        Assertions.assertEquals(Boolean.TRUE, created, "Redis 幂等故障 Key 创建失败");
    }

    private void cleanOwned(String pattern) {
        withConnection(connection -> connection.keyCommands()
            .scan(ScanOptions.scanOptions().match(pattern).count(SCAN_PAGE_SIZE).build())
            .buffer(SCAN_PAGE_SIZE).concatMap(keys -> unlinkValidated(connection, keys)).then());
    }

    public List<String> guardKeys() {
        return withConnectionResult(connection -> connection.keyCommands()
            .scan(ScanOptions.scanOptions().match(namespace.guardScanPattern()).count(SCAN_PAGE_SIZE).build())
            .map(RedisTestCleaner::decode).doOnNext(this::validateOwnedGuardKey).collectList());
    }

    public void createSentinel() {
        Boolean created =
            withConnectionResult(connection -> connection.stringCommands().set(utf8(namespace.sentinelKey()),
                SENTINEL_VALUE.asReadOnlyBuffer(), Expiration.milliseconds(SENTINEL_TTL_MILLIS),
                RedisStringCommands.SetOption.UPSERT));
        Assertions.assertEquals(Boolean.TRUE, created, "Redis 测试 Sentinel 创建失败");
    }

    public void assertSentinelPresent() {
        ByteBuffer actual =
            withConnectionResult(connection -> connection.stringCommands().get(utf8(namespace.sentinelKey())));
        Assertions.assertNotNull(actual, "Redis Guard 清理越界删除了 sibling Sentinel");
        Assertions.assertEquals("owned-sentinel", decode(actual), "Redis 测试 Sentinel 值被意外修改");
    }

    public void deleteSentinelExact() {
        withConnection(connection -> connection.keyCommands().unlink(utf8(namespace.sentinelKey())).then());
    }

    private Mono<Long> unlinkValidated(ReactiveRedisConnection connection, List<ByteBuffer> keys) {
        if (keys.isEmpty()) {
            return Mono.just(0L);
        }
        keys.stream().map(RedisTestCleaner::decode).forEach(this::validateOwnedGuardKey);
        return connection.keyCommands().mUnlink(keys);
    }

    private void validateOwnedGuardKey(String key) {
        if (!key.startsWith(namespace.guardPrefix()) && !key.startsWith(namespace.idempotencyPrefix())) {
            throw new AssertionError("拒绝清理当前运行 Guard 前缀之外的 Redis Key");
        }
    }

    private void withConnection(java.util.function.Function<ReactiveRedisConnection, Mono<Void>> work) {
        withConnectionResult(work);
    }

    private <T> T withConnectionResult(java.util.function.Function<ReactiveRedisConnection, Mono<T>> work) {
        return Mono
            .usingWhen(Mono.fromSupplier(connectionFactory::getReactiveConnection), work,
                connection -> connection.closeLater(), (connection, error) -> connection.closeLater(),
                connection -> connection.closeLater())
            .timeout(OPERATION_TIMEOUT).block(OPERATION_TIMEOUT.plusSeconds(1));
    }
}
