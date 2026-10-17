package com.paytech.gateway.auto.guard;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.types.Expiration;

import com.paytech.gateway.guard.GuardDependencyUnavailableException;
import com.paytech.gateway.guard.policy.GuardContext;
import com.paytech.gateway.guard.policy.GuardDimension;
import com.paytech.gateway.guard.policy.GuardProfile;
import com.paytech.gateway.guard.policy.RateLimitRule;
import com.paytech.gateway.guard.ratelimit.RateLimitCommand;
import com.paytech.gateway.guard.ratelimit.RateLimitCommandFactory;
import com.paytech.gateway.guard.ratelimit.RateLimitDecision;
import com.paytech.gateway.guard.ratelimit.ReactiveRateLimiter;
import com.paytech.gateway.guard.redis.GuardRedisProperties;
import com.paytech.gateway.guard.redis.ReactiveRedisScriptExecutor;
import com.paytech.gateway.guard.redis.RedisGuardKeyFactory;
import com.paytech.gateway.routing.OperationsRouteCode;
import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** 真实 Redis Token Bucket 状态机和 Key/Command 边界测试。 */
class RedisTokenBucketAutoTest extends GatewayAutoTestBase {
    private static final long MICROTOKEN = 1_000_000L;
    private static final Duration REDIS_TIMEOUT = Duration.ofSeconds(5);
    private static final long SEED_TTL_MILLIS = Duration.ofMinutes(10).toMillis();

    @Autowired
    ReactiveRateLimiter rateLimiter;
    @Autowired
    ReactiveRedisConnectionFactory connectionFactory;
    @Autowired
    ReactiveRedisScriptExecutor scriptExecutor;
    @Autowired
    RedisGuardKeyFactory keyFactory;
    @Autowired
    RateLimitCommandFactory commandFactory;

    private static long allowedCount(List<NamedDecision> decisions, String name) {
        return decisions.stream().filter(decision -> decision.name().equals(name)).map(NamedDecision::decision)
            .filter(RateLimitDecision::allowed).count();
    }

    private static List<String> arguments(RateLimitCommand command) {
        return List.of(Long.toString(command.capacityMicrotokens()), Long.toString(command.refillMicrotokens()),
            Long.toString(command.refillPeriodMillis()), Long.toString(command.requestedMicrotokens()));
    }

    private static List<String> withNamespace(List<String> arguments) {
        List<String> namespaced = new ArrayList<>(arguments.size() + 1);
        namespaced.add(GatewayAutoTestRuntime.redisNamespace().value());
        namespaced.addAll(arguments);
        return List.copyOf(namespaced);
    }

    private static List<String> concatenate(List<String> first, List<String> second) {
        List<String> combined = new ArrayList<>(first.size() + second.size());
        combined.addAll(first);
        combined.addAll(second);
        return List.copyOf(combined);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<String> heapPollutedStrings(Object value) {
        return (List)List.of(value);
    }

    private static GuardProfile profile(List<RateLimitRule> rules) {
        return new GuardProfile(rules, List.of(), Duration.ofSeconds(30), Duration.ofSeconds(60));
    }

    private static ByteBuffer utf8(String value) {
        return StandardCharsets.UTF_8.encode(value);
    }

    private static long decodeLong(ByteBuffer value) {
        Assertions.assertNotNull(value, "Redis Bucket 字段不可缺失");
        return Long.parseLong(StandardCharsets.UTF_8.decode(value.asReadOnlyBuffer()).toString());
    }

    private static BucketState bucketState(long tokens, long lastRefillMillis, long refillRemainder) {
        return new BucketState(tokens, lastRefillMillis, refillRemainder);
    }

    @Test
    void test_firstRequestConsumesOneTokenFromNewBucket() {
        String key = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        RateLimitCommand command = command(key, 2 * MICROTOKEN, 2 * MICROTOKEN, 60_000L);

        verifyDecision(List.of(command), RateLimitDecision.allowed(0));

        BucketState state = bucket(key);
        Assertions.assertEquals(1_000_000L, state.tokens());
        Assertions.assertEquals(List.of(key), redisTestCleaner.guardKeys());
    }

    @Test
    void test_lowerCapacityClampsExistingTokensWithoutDependencyFailure() {
        String key = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        verifyDecision(List.of(command(key, 5 * MICROTOKEN, MICROTOKEN, Duration.ofDays(1).toMillis())),
            RateLimitDecision.allowed(0));

        verifyDecision(List.of(command(key, 2 * MICROTOKEN, MICROTOKEN, Duration.ofDays(1).toMillis())),
            RateLimitDecision.allowed(0));

        Assertions.assertEquals(MICROTOKEN, bucket(key).tokens());
    }

    @Test
    void test_higherCapacityPreservesExistingTokensInsteadOfGrantingCapacityDelta() {
        String key = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        verifyDecision(List.of(command(key, 2 * MICROTOKEN, MICROTOKEN, Duration.ofDays(1).toMillis())),
            RateLimitDecision.allowed(0));

        verifyDecision(List.of(command(key, 5 * MICROTOKEN, MICROTOKEN, Duration.ofDays(1).toMillis())),
            RateLimitDecision.allowed(0));

        Assertions.assertEquals(0L, bucket(key).tokens());
    }

    @Test
    void test_exactBurstExhaustionRejectsTheNextRequest() {
        String key = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        RateLimitCommand command = command(key, 2 * MICROTOKEN, MICROTOKEN, Duration.ofDays(1).toMillis());

        verifyDecision(List.of(command), RateLimitDecision.allowed(0));
        verifyDecision(List.of(command), RateLimitDecision.allowed(0));
        verifyDecision(List.of(command), RateLimitDecision.rejected(86_400, GuardDimension.SHARED_ROUTE));

        Assertions.assertEquals(0L, bucket(key).tokens());
    }

    @Test
    void test_partialRefillIsPersistedBeforeDeduction() {
        String key = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        RateLimitCommand command = command(key, 2 * MICROTOKEN, MICROTOKEN, 10_000L);
        long before = redisTimeMillis();
        seedBucket(key, 900_000L, before - 2_000L, 0L, SEED_TTL_MILLIS);

        verifyDecision(List.of(command), RateLimitDecision.allowed(0));

        BucketState state = bucket(key);
        Assertions.assertTrue(state.tokens() >= 100_000L && state.tokens() < 300_000L,
            () -> "局部补充后的 Token 超出预期范围: " + state.tokens());
        Assertions.assertTrue(state.refillRemainder() >= 0 && state.refillRemainder() < command.refillPeriodMillis());
    }

    @Test
    void test_fullRefillCapsCapacityAndResetsRemainder() {
        String key = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        RateLimitCommand command = command(key, 2 * MICROTOKEN, MICROTOKEN, 1_000L);
        seedBucket(key, 17L, redisTimeMillis() - 10_000L, 777L, SEED_TTL_MILLIS);

        verifyDecision(List.of(command), RateLimitDecision.allowed(0));

        BucketState state = bucket(key);
        Assertions.assertEquals(MICROTOKEN, state.tokens());
        Assertions.assertEquals(0L, state.refillRemainder());
    }

    @Test
    void test_fractionalMicrotokenRemainderSurvivesBetweenRequests() {
        String key = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        RateLimitCommand command = command(key, MICROTOKEN, 1L, 10_000L, 1L);
        long before = redisTimeMillis();
        seedBucket(key, 0L, before - 1L, 5_000L, SEED_TTL_MILLIS);

        verifyDecision(List.of(command), RateLimitDecision.rejected(5, GuardDimension.SHARED_ROUTE));

        BucketState state = bucket(key);
        Assertions.assertEquals(0L, state.tokens());
        Assertions.assertTrue(state.refillRemainder() >= 5_001L && state.refillRemainder() < 10_000L,
            () -> "亚 Microtoken 余数未按整数累计: " + state.refillRemainder());
    }

    @Test
    void test_rejectionReturnsMaximumIntegerSecondRecoveryAcrossBuckets() {
        String fastKey = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        String slowKey = sharedRateKey(OperationsRouteCode.AUTH_REFRESH);
        RateLimitCommand fast = command(fastKey, MICROTOKEN, MICROTOKEN, 1_000L);
        RateLimitCommand slow = command(slowKey, MICROTOKEN, MICROTOKEN, 3_500L);
        long now = redisTimeMillis();
        seedBucket(fastKey, 0L, now, 0L, SEED_TTL_MILLIS);
        seedBucket(slowKey, 0L, now, 0L, SEED_TTL_MILLIS);

        verifyDecision(List.of(fast, slow), RateLimitDecision.rejected(4, GuardDimension.SHARED_ROUTE));
    }

    @Test
    void test_ttlUsesMinimumAndTwiceEmptyToFullDuration() {
        String minimumKey = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        String derivedKey = sharedRateKey(OperationsRouteCode.AUTH_REFRESH);
        RateLimitCommand minimum = command(minimumKey, MICROTOKEN, MICROTOKEN, 1_000L);
        RateLimitCommand derived = command(derivedKey, 2 * MICROTOKEN, MICROTOKEN, 60_000L);

        verifyDecision(List.of(minimum, derived), RateLimitDecision.allowed(0));

        assertTtlNear(minimumKey, 60_000L);
        assertTtlNear(derivedKey, 240_000L);
    }

    @Test
    void test_routeIpAndSubjectDeductionIsAllOrNothing() {
        List<String> keys = List.of(sharedRateKey(OperationsRouteCode.AUTH_LOGOUT),
            identityRateKey(OperationsRouteCode.AUTH_LOGOUT, GuardDimension.CLIENT_IP, "203.0.113.17"), identityRateKey(
                OperationsRouteCode.AUTH_LOGOUT, GuardDimension.AUTHENTICATED_SUBJECT, "operations-user-17"));
        List<RateLimitCommand> commands =
            keys.stream().map(key -> command(key, MICROTOKEN, MICROTOKEN, Duration.ofDays(1).toMillis())).toList();
        long now = redisTimeMillis();
        seedBucket(keys.get(0), MICROTOKEN, now, 0L, SEED_TTL_MILLIS);
        seedBucket(keys.get(1), 0L, now, 0L, SEED_TTL_MILLIS);
        seedBucket(keys.get(2), MICROTOKEN, now, 0L, SEED_TTL_MILLIS);

        verifyDecision(commands, RateLimitDecision.rejected(86_400, GuardDimension.CLIENT_IP));

        Assertions.assertEquals(MICROTOKEN, bucket(keys.get(0)).tokens());
        Assertions.assertEquals(0L, bucket(keys.get(1)).tokens());
        Assertions.assertEquals(MICROTOKEN, bucket(keys.get(2)).tokens());
    }

    @Test
    void test_commandFactoryUsesSemanticDimensionAndRawCanonicalIdentityKeys() throws Exception {
        GuardContext context = new GuardContext(OperationsRouteCode.AUTH_LOGOUT, InetAddress.getByName("203.0.113.17"),
            "operations:用户:17");
        GuardProfile profile = new GuardProfile(
            List.of(new RateLimitRule(GuardDimension.AUTHENTICATED_SUBJECT, 10, 10, Duration.ofMinutes(1)),
                new RateLimitRule(GuardDimension.SHARED_ROUTE, 300, 300, Duration.ofMinutes(1)),
                new RateLimitRule(GuardDimension.CLIENT_IP, 5, 5, Duration.ofMinutes(1))),
            List.of(), Duration.ofSeconds(30), Duration.ofSeconds(60));

        List<RateLimitCommand> first = commandFactory.create(profile, context);
        List<RateLimitCommand> second = commandFactory.create(profile, context);

        Assertions.assertEquals(first.stream().map(CommandView::from).toList(),
            second.stream().map(CommandView::from).toList());
        Assertions.assertEquals(
            List.of("shared-route:shared", "client-ip:203.0.113.17", "authenticated-subject:operations:用户:17"),
            first.stream().map(this::keyIdentitySuffix).toList());
        Assertions.assertEquals(3, first.stream().map(RateLimitCommand::key).distinct().count());
        Assertions.assertEquals(List.of(300L, 5L, 10L),
            first.stream().map(command -> command.capacityMicrotokens() / MICROTOKEN).toList());
        Assertions.assertTrue(first.stream().allMatch(command -> command.requestedMicrotokens() == MICROTOKEN));
        Assertions.assertTrue(first.stream().allMatch(command -> command.key().startsWith(ownedGuardPrefix())));
    }

    @Test
    void test_tenantBucketUsesItsOwnCanonicalKeyAndLuaDimensionCode() throws Exception {
        GuardContext context = new GuardContext("client-api-test-post", InetAddress.getLoopbackAddress(),
            "credential-17", "tenant-42");
        GuardProfile profile = profile(List.of(
            new RateLimitRule(GuardDimension.AUTHENTICATED_SUBJECT, 2, 2, Duration.ofMinutes(1)),
            new RateLimitRule(GuardDimension.TENANT, 1, 1, Duration.ofDays(1))));

        List<RateLimitCommand> commands = commandFactory.createAuthenticated(profile, context);
        verifyDecision(commands, RateLimitDecision.allowed(0));
        verifyDecision(commands, RateLimitDecision.rejected(86_400, GuardDimension.TENANT));

        Assertions.assertEquals(List.of("authenticated-subject:credential-17", "tenant:tenant-42"),
            commands.stream().map(this::keyIdentitySuffix).toList());
        Assertions.assertNotEquals(commands.get(0).key(), commands.get(1).key());
    }

    @Test
    void test_commandFactoryNeverDigestsSharedRoute() throws Exception {
        GuardContext context =
            new GuardContext(OperationsRouteCode.AUTH_LOGIN, InetAddress.getByName("198.51.100.1"), null);
        GuardProfile profile =
            profile(List.of(new RateLimitRule(GuardDimension.SHARED_ROUTE, 300, 300, Duration.ofMinutes(1))));

        List<RateLimitCommand> commands = commandFactory.create(profile, context);

        Assertions.assertEquals(List.of(sharedRateKey(OperationsRouteCode.AUTH_LOGIN)),
            commands.stream().map(RateLimitCommand::key).toList());
        Assertions.assertFalse(commands.getFirst().key().contains(context.remoteAddress().getHostAddress()));
    }

    @Test
    void test_concurrentCallersNeverExceedCapacityAndChildPrefixesRemainIsolated() {
        String loginKey = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        String refreshKey = sharedRateKey(OperationsRouteCode.AUTH_REFRESH);
        RateLimitCommand login = command(loginKey, 8 * MICROTOKEN, MICROTOKEN, Duration.ofDays(1).toMillis());
        RateLimitCommand refresh = command(refreshKey, 5 * MICROTOKEN, MICROTOKEN, Duration.ofDays(1).toMillis());

        List<NamedDecision> decisions =
            Flux.merge(concurrentEvaluate("login", login, 40), concurrentEvaluate("refresh", refresh, 40)).collectList()
                .block(REDIS_TIMEOUT);

        Assertions.assertNotNull(decisions);
        Assertions.assertEquals(8, allowedCount(decisions, "login"));
        Assertions.assertEquals(5, allowedCount(decisions, "refresh"));
        Assertions.assertEquals(0L, bucket(loginKey).tokens());
        Assertions.assertEquals(0L, bucket(refreshKey).tokens());
        Assertions.assertTrue(redisTestCleaner.guardKeys().stream()
            .allMatch(key -> key.startsWith(ownedGuardPrefix())));
        redisTestCleaner.assertSentinelPresent();
    }

    @Test
    void test_bucketTimestampComesFromRedisTime() {
        String key = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        RateLimitCommand command = command(key, MICROTOKEN, MICROTOKEN, 60_000L);
        long before = redisTimeMillis();

        verifyDecision(List.of(command), RateLimitDecision.allowed(0));

        long after = redisTimeMillis();
        long stored = bucket(key).lastRefillMillis();
        Assertions.assertTrue(stored >= before && stored <= after,
            () -> "Bucket 时间戳不是本次 Redis TIME: " + stored + " not in [" + before + ", " + after + "]");
    }

    @Test
    void test_multiBucketDecisionUsesOneRedisTimeForEveryBucket() {
        List<RateLimitCommand> commands =
            List.of(command(sharedRateKey(OperationsRouteCode.AUTH_LOGIN), 2 * MICROTOKEN, MICROTOKEN, 60_000L),
                command(sharedRateKey(OperationsRouteCode.AUTH_REFRESH), 2 * MICROTOKEN, MICROTOKEN, 60_000L),
                command(sharedRateKey(OperationsRouteCode.AUTH_LOGOUT), 2 * MICROTOKEN, MICROTOKEN, 60_000L));

        verifyDecision(commands, RateLimitDecision.allowed(0));

        Assertions.assertEquals(1L,
            commands.stream().map(command -> bucket(command.key()).lastRefillMillis()).distinct().count(),
            "一次决策的全部 Bucket 必须共享唯一 Redis TIME");
    }

    @Test
    void test_backwardRedisTimeNeverMovesPersistedTimeOrRefillsTokens() {
        String key = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        RateLimitCommand command = command(key, 2 * MICROTOKEN, MICROTOKEN, 60_000L);
        long futureMillis = redisTimeMillis() + 60_000L;
        seedBucket(key, MICROTOKEN, futureMillis, 777L, SEED_TTL_MILLIS);

        verifyDecision(List.of(command), RateLimitDecision.allowed(0));

        BucketState state = bucket(key);
        Assertions.assertEquals(0L, state.tokens());
        Assertions.assertEquals(futureMillis, state.lastRefillMillis());
        Assertions.assertEquals(777L, state.refillRemainder());
    }

    @Test
    void test_rejectionPersistsValidRefillWithoutAnyDeduction() {
        String refillingKey = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        String rejectingKey = sharedRateKey(OperationsRouteCode.AUTH_REFRESH);
        RateLimitCommand refilling = command(refillingKey, 2 * MICROTOKEN, 2 * MICROTOKEN, 1_000L, MICROTOKEN);
        RateLimitCommand rejecting =
            command(rejectingKey, MICROTOKEN, MICROTOKEN, Duration.ofDays(1).toMillis(), MICROTOKEN);
        long now = redisTimeMillis();
        seedBucket(refillingKey, 0L, now - 2_000L, 19L, SEED_TTL_MILLIS);
        seedBucket(rejectingKey, 0L, now, 0L, SEED_TTL_MILLIS);

        verifyDecision(List.of(refilling, rejecting), RateLimitDecision.rejected(86_400L, GuardDimension.SHARED_ROUTE));

        BucketState refilled = bucket(refillingKey);
        Assertions.assertEquals(2 * MICROTOKEN, refilled.tokens(), "拒绝时不得扣减先前通过的 Bucket");
        Assertions.assertEquals(0L, refilled.refillRemainder());
        Assertions.assertEquals(0L, bucket(rejectingKey).tokens());
    }

    @Test
    void test_v3DecisionDoesNotReadOrMutateLegacyV2Key() {
        String v3Key = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        String v2Key = v3Key.replace(":guard:v3:", ":guard:v2:");
        seedString(v2Key, "legacy-v2-untouched", SEED_TTL_MILLIS);

        verifyDecision(List.of(command(v3Key, 2 * MICROTOKEN, MICROTOKEN, 60_000L)), RateLimitDecision.allowed(0));

        Assertions.assertEquals("legacy-v2-untouched", stringValue(v2Key));
        Assertions.assertTrue(redisTestCleaner.guardKeys().containsAll(List.of(v2Key, v3Key)));
    }

    @Test
    void test_laterWrongTypeDoesNotMutateEarlierBucket() {
        String validKey = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        String wrongTypeKey = sharedRateKey(OperationsRouteCode.AUTH_REFRESH);
        RateLimitCommand valid = command(validKey, 2 * MICROTOKEN, MICROTOKEN, 1_000L);
        RateLimitCommand wrongType = command(wrongTypeKey, 2 * MICROTOKEN, MICROTOKEN, 1_000L);
        BucketState original = bucketState(MICROTOKEN, redisTimeMillis() - 10_000L, 77L);
        seedBucket(validKey, original, SEED_TTL_MILLIS);
        seedString(wrongTypeKey, "not-a-bucket", SEED_TTL_MILLIS);

        StepVerifier.create(rateLimiter.evaluate(List.of(valid, wrongType)))
            .expectError(GuardDependencyUnavailableException.class).verify();

        Assertions.assertEquals(original, bucket(validKey));
    }

    @Test
    void test_laterMalformedHashDoesNotMutateEarlierBucket() {
        String validKey = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        String malformedKey = sharedRateKey(OperationsRouteCode.AUTH_REFRESH);
        RateLimitCommand valid = command(validKey, 2 * MICROTOKEN, MICROTOKEN, 1_000L);
        RateLimitCommand malformed = command(malformedKey, 2 * MICROTOKEN, MICROTOKEN, 1_000L);
        BucketState original = bucketState(MICROTOKEN, redisTimeMillis() - 10_000L, 77L);
        seedBucket(validKey, original, SEED_TTL_MILLIS);
        seedMalformedBucket(malformedKey, SEED_TTL_MILLIS);

        StepVerifier.create(rateLimiter.evaluate(List.of(valid, malformed)))
            .expectError(GuardDependencyUnavailableException.class).verify();

        Assertions.assertEquals(original, bucket(validKey));
    }

    @Test
    void test_laterMissingHashFieldDoesNotMutateEarlierBucket() {
        String validKey = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        String missingFieldKey = sharedRateKey(OperationsRouteCode.AUTH_REFRESH);
        RateLimitCommand valid = command(validKey, 2 * MICROTOKEN, MICROTOKEN, 1_000L);
        RateLimitCommand missingField = command(missingFieldKey, 2 * MICROTOKEN, MICROTOKEN, 1_000L);
        BucketState original = bucketState(MICROTOKEN, redisTimeMillis() - 10_000L, 77L);
        seedBucket(validKey, original, SEED_TTL_MILLIS);
        seedMissingFieldBucket(missingFieldKey, SEED_TTL_MILLIS);

        StepVerifier.create(rateLimiter.evaluate(List.of(valid, missingField)))
            .expectError(GuardDependencyUnavailableException.class).verify();

        Assertions.assertEquals(original, bucket(validKey));
    }

    @Test
    void test_laterExtraHashFieldDoesNotMutateEarlierBucket() {
        String validKey = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        String extraFieldKey = sharedRateKey(OperationsRouteCode.AUTH_REFRESH);
        RateLimitCommand valid = command(validKey, 2 * MICROTOKEN, MICROTOKEN, 1_000L);
        RateLimitCommand extraField = command(extraFieldKey, 2 * MICROTOKEN, MICROTOKEN, 1_000L);
        BucketState original = bucketState(MICROTOKEN, redisTimeMillis() - 10_000L, 77L);
        seedBucket(validKey, original, SEED_TTL_MILLIS);
        seedBucket(extraFieldKey, 0L, redisTimeMillis(), 0L, SEED_TTL_MILLIS);
        addHashField(extraFieldKey, "unexpected", "1");

        StepVerifier.create(rateLimiter.evaluate(List.of(valid, extraField)))
            .expectError(GuardDependencyUnavailableException.class).verify();

        Assertions.assertEquals(original, bucket(validKey));
    }

    @Test
    void test_namespaceAndKeySegmentsRejectUnsafeShapes() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new GuardRedisProperties(null));
        List<String> invalidNamespaces = List.of("", "Gateway:prod", "gateway::prod", "gateway:prod ", "gateway:{prod}",
            "gateway:prod*", "gateway:guard:v1:prod", "gateway:guard:v1", "gateway:guard:v2:prod",
            "gateway:guard:v3:prod", "gateway:guard:v17", "gateway:guard:v0:prod");
        invalidNamespaces.forEach(namespace -> Assertions.assertThrows(IllegalArgumentException.class,
            () -> new GuardRedisProperties(namespace), namespace));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new GuardRedisProperties("a".repeat(129)));

        Assertions.assertThrows(IllegalArgumentException.class,
            () -> keyFactory.rateKey(OperationsRouteCode.AUTH_LOGIN, GuardDimension.CLIENT_IP));
        Assertions.assertThrows(IllegalArgumentException.class, () -> keyFactory.rateKey(OperationsRouteCode.AUTH_LOGIN,
            GuardDimension.SHARED_ROUTE, "unexpected-identity"));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> keyFactory.rateKey(OperationsRouteCode.AUTH_LOGIN, GuardDimension.CLIENT_IP, " "));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> keyFactory.rateKey(OperationsRouteCode.AUTH_LOGIN, GuardDimension.CLIENT_IP, "x".repeat(513)));
        Assertions.assertThrows(IllegalArgumentException.class, () -> keyFactory.rateKey(OperationsRouteCode.AUTH_LOGIN,
            GuardDimension.AUTHENTICATED_SUBJECT, "subject\ncontrol"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> keyFactory.rateKey(OperationsRouteCode.AUTH_LOGIN,
            GuardDimension.AUTHENTICATED_SUBJECT, "subject\uD800"));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> keyFactory.rateKey(OperationsRouteCode.AUTH_LOGIN, GuardDimension.TENANT, ""));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> keyFactory.rateKey(OperationsRouteCode.AUTH_LOGIN, GuardDimension.TENANT,
                Character.toString(0x2003)));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> keyFactory.rateKey(OperationsRouteCode.AUTH_LOGIN, GuardDimension.TENANT,
                "tenant" + Character.toString(0x85) + "identity"));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> keyFactory.rateKey(OperationsRouteCode.AUTH_LOGIN, GuardDimension.TENANT, "x".repeat(513)));
    }

    @Test
    void test_keyFactoryEmitsApprovedSharedIpPrincipalAndTenantShapes() {
        String namespace = GatewayAutoTestRuntime.redisNamespace().value();
        Assertions.assertEquals(namespace + ":guard:v3:operations-auth-login:rate:shared-route:shared",
            keyFactory.rateKey(OperationsRouteCode.AUTH_LOGIN, GuardDimension.SHARED_ROUTE));
        Assertions.assertEquals(namespace + ":guard:v3:operations-auth-login:rate:client-ip:203.0.113.17",
            keyFactory.rateKey(OperationsRouteCode.AUTH_LOGIN, GuardDimension.CLIENT_IP, "203.0.113.17"));
        Assertions.assertEquals(namespace + ":guard:v3:operations-auth-logout:concurrency:shared-route:shared",
            keyFactory.concurrencyKey(OperationsRouteCode.AUTH_LOGOUT, GuardDimension.SHARED_ROUTE));
        Assertions.assertEquals(
            namespace + ":guard:v3:operations-auth-logout:concurrency:authenticated-subject:operations:用户:17",
            keyFactory.concurrencyKey(OperationsRouteCode.AUTH_LOGOUT, GuardDimension.AUTHENTICATED_SUBJECT,
                "operations:用户:17"));
        Assertions.assertEquals(namespace + ":guard:v3:operations-auth-login:rate:tenant:tenant:17",
            keyFactory.rateKey(OperationsRouteCode.AUTH_LOGIN, GuardDimension.TENANT, "tenant:17"));
        Assertions.assertEquals(namespace + ":guard:v3:operations-auth-logout:concurrency:tenant:tenant:17",
            keyFactory.concurrencyKey(OperationsRouteCode.AUTH_LOGOUT, GuardDimension.TENANT, "tenant:17"));
    }

    @Test
    void test_rateCommandRejectsUnsafeIntegerIntermediatesAndInvalidRequests() {
        String key = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> RateLimitCommand.deriveTtlMillis(9_007_199_254_740_991L, 1, 2));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new RateLimitCommand(key, MICROTOKEN, MICROTOKEN, 1_000, MICROTOKEN + 1));
        RateLimitCommand command = command(key, MICROTOKEN, MICROTOKEN, 1_000L);
        Assertions.assertThrows(IllegalArgumentException.class, () -> rateLimiter.evaluate(List.of()));
        Assertions.assertThrows(IllegalArgumentException.class, () -> rateLimiter.evaluate(List.of(command, command)));
    }

    @Test
    void test_maximumSafeIntegersRemainDecimalAndReusableInRedis() {
        String key = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        long maximumSafeInteger = 9_007_199_254_740_991L;
        RateLimitCommand command = command(key, maximumSafeInteger, maximumSafeInteger, 1L, 1L);

        verifyDecision(List.of(command), RateLimitDecision.allowed(0));
        Assertions.assertEquals(maximumSafeInteger - 1, bucket(key).tokens());
        seedBucket(key, maximumSafeInteger - 1, redisTimeMillis() + 60_000L, 0L, SEED_TTL_MILLIS);
        verifyDecision(List.of(command), RateLimitDecision.allowed(0));

        Assertions.assertEquals(maximumSafeInteger - 2, bucket(key).tokens());
    }

    @Test
    void test_rawLuaValidationRejectsBeforeMutatingAnEarlierBucket() {
        String earlierKey = sharedRateKey(OperationsRouteCode.AUTH_LOGIN);
        String laterKey = sharedRateKey(OperationsRouteCode.AUTH_REFRESH);
        RateLimitCommand earlier = command(earlierKey, 2 * MICROTOKEN, MICROTOKEN, 1_000L);
        RateLimitCommand later = command(laterKey, MICROTOKEN, MICROTOKEN, 1_000L);
        BucketState original = bucketState(MICROTOKEN, redisTimeMillis() + 60_000L, 17L);
        ReactiveRedisScriptExecutor.LoadedScript script =
            scriptExecutor.loadUtf8Script("com/paytech/gateway/guard/ratelimit/token-bucket.lua");
        List<String> validArguments = withNamespace(concatenate(arguments(earlier), arguments(later)));
        String identityPrefix = GatewayAutoTestRuntime.redisNamespace().value()
            + ":guard:v3:operations-auth-refresh:rate:";
        List<InvalidScriptInvocation> invalidInvocations = List.of(
            new InvalidScriptInvocation("argument-count", List.of(earlierKey, laterKey),
                validArguments.subList(0, validArguments.size() - 1)),
            new InvalidScriptInvocation("extra-argument", List.of(earlierKey, laterKey),
                concatenate(validArguments, List.of("1"))),
            new InvalidScriptInvocation("duplicate-key", List.of(earlierKey, earlierKey),
                withNamespace(concatenate(arguments(earlier), arguments(earlier)))),
            new InvalidScriptInvocation("unsafe-capacity", List.of(earlierKey, laterKey),
                withNamespace(concatenate(arguments(earlier), List.of("9007199254740992", "1", "1", "1")))),
            new InvalidScriptInvocation("zero-capacity", List.of(earlierKey, laterKey),
                withNamespace(concatenate(arguments(earlier), List.of("0", "1", "1", "1")))),
            new InvalidScriptInvocation("unsafe-intermediate", List.of(earlierKey, laterKey),
                withNamespace(concatenate(arguments(earlier), List.of("9007199254740991", "1", "2", "1")))),
            new InvalidScriptInvocation("legacy-schema",
                List.of(earlierKey, laterKey.replace(":guard:v3:", ":guard:v2:")), validArguments),
            new InvalidScriptInvocation("foreign-namespace",
                List.of(earlierKey, "foreign" + laterKey.substring(laterKey.indexOf(":guard:v3:"))), validArguments),
            new InvalidScriptInvocation("wrong-policy",
                List.of(earlierKey, laterKey.replace(":rate:", ":concurrency:")), validArguments),
            new InvalidScriptInvocation("shared-route-non-canonical-identity",
                List.of(earlierKey, identityPrefix + "shared-route:not-shared"), validArguments),
            new InvalidScriptInvocation("unknown-dimension",
                List.of(earlierKey, identityPrefix + "unknown:identity"), validArguments),
            new InvalidScriptInvocation("tenant-empty-identity",
                List.of(earlierKey, identityPrefix + "tenant:"), validArguments),
            new InvalidScriptInvocation("tenant-blank-identity",
                List.of(earlierKey, identityPrefix + "tenant:" + Character.toString(0x2003)), validArguments),
            new InvalidScriptInvocation("tenant-control-identity",
                List.of(earlierKey, identityPrefix + "tenant:tenant" + Character.toString(0x85) + "identity"),
                validArguments),
            new InvalidScriptInvocation("tenant-oversized-identity",
                List.of(earlierKey, identityPrefix + "tenant:" + "x".repeat(513)), validArguments));

        for (InvalidScriptInvocation invocation : invalidInvocations) {
            seedBucket(earlierKey, original.tokens(), original.lastRefillMillis(), original.refillRemainder(),
                SEED_TTL_MILLIS);
            StepVerifier.create(scriptExecutor.execute(script, invocation.keys(), invocation.arguments(), 3))
                .as(invocation.name()).expectError(GuardDependencyUnavailableException.class).verify();
            Assertions.assertEquals(original, bucket(earlierKey), invocation.name());
        }
    }

    @Test
    void test_programmerExceptionInDeferredEncodingKeepsItsOriginalType() {
        ReactiveRedisScriptExecutor.LoadedScript script =
            scriptExecutor.loadUtf8Script("com/paytech/gateway/guard/ratelimit/token-bucket.lua");

        StepVerifier.create(scriptExecutor.execute(script, heapPollutedStrings(42), List.of(), 3))
            .expectError(ClassCastException.class).verify();
    }

    private void verifyDecision(List<RateLimitCommand> commands, RateLimitDecision expected) {
        StepVerifier.create(rateLimiter.evaluate(commands)).expectNext(expected).verifyComplete();
    }

    private RateLimitDecision evaluate(List<RateLimitCommand> commands) {
        return rateLimiter.evaluate(commands).block(REDIS_TIMEOUT);
    }

    private Flux<NamedDecision> concurrentEvaluate(String name, RateLimitCommand command, int callers) {
        return Flux.range(0, callers).flatMap(index -> rateLimiter.evaluate(List.of(command)), callers)
            .map(decision -> new NamedDecision(name, decision));
    }

    private RateLimitCommand command(String key, long capacity, long refill, long periodMillis) {
        return command(key, capacity, refill, periodMillis, MICROTOKEN);
    }

    private RateLimitCommand command(String key, long capacity, long refill, long periodMillis, long requested) {
        return new RateLimitCommand(key, capacity, refill, periodMillis, requested);
    }

    private String sharedRateKey(OperationsRouteCode routeCode) {
        return keyFactory.rateKey(routeCode, GuardDimension.SHARED_ROUTE);
    }

    private String identityRateKey(OperationsRouteCode routeCode, GuardDimension dimension, String identity) {
        return keyFactory.rateKey(routeCode, dimension, identity);
    }

    private String ownedGuardPrefix() {
        return GatewayAutoTestRuntime.redisNamespace().guardPrefix();
    }

    private String keyIdentitySuffix(RateLimitCommand command) {
        String marker = ":rate:";
        return command.key().substring(command.key().indexOf(marker) + marker.length());
    }

    private void seedBucket(String key, long tokens, long lastRefillMillis, long remainder, long ttlMillis) {
        seedBucket(key, bucketState(tokens, lastRefillMillis, remainder), ttlMillis);
    }

    private void seedBucket(String key, BucketState state, long ttlMillis) {
        Map<ByteBuffer, ByteBuffer> fields = new LinkedHashMap<>();
        fields.put(utf8("t"), utf8(Long.toString(state.tokens())));
        fields.put(utf8("lr"), utf8(Long.toString(state.lastRefillMillis())));
        fields.put(utf8("rr"), utf8(Long.toString(state.refillRemainder())));
        withRedis(connection -> connection.hashCommands().hMSet(utf8(key), fields)
            .then(connection.keyCommands().pExpire(utf8(key), Duration.ofMillis(ttlMillis))).then());
    }

    private void seedString(String key, String value, long ttlMillis) {
        withRedis(connection -> connection.stringCommands()
            .set(utf8(key), utf8(value), Expiration.milliseconds(ttlMillis), RedisStringCommands.SetOption.UPSERT)
            .then());
    }

    private void seedMalformedBucket(String key, long ttlMillis) {
        Map<ByteBuffer, ByteBuffer> fields = new LinkedHashMap<>();
        fields.put(utf8("t"), utf8("not-an-integer"));
        fields.put(utf8("lr"), utf8("0"));
        fields.put(utf8("rr"), utf8("0"));
        withRedis(connection -> connection.hashCommands().hMSet(utf8(key), fields)
            .then(connection.keyCommands().pExpire(utf8(key), Duration.ofMillis(ttlMillis))).then());
    }

    private void seedMissingFieldBucket(String key, long ttlMillis) {
        Map<ByteBuffer, ByteBuffer> fields = new LinkedHashMap<>();
        fields.put(utf8("t"), utf8("0"));
        fields.put(utf8("lr"), utf8("0"));
        withRedis(connection -> connection.hashCommands().hMSet(utf8(key), fields)
            .then(connection.keyCommands().pExpire(utf8(key), Duration.ofMillis(ttlMillis))).then());
    }

    private void addHashField(String key, String field, String value) {
        withRedis(connection -> connection.hashCommands().hSet(utf8(key), utf8(field), utf8(value)).then());
    }

    private String stringValue(String key) {
        ByteBuffer value = withRedis(connection -> connection.stringCommands().get(utf8(key)));
        Assertions.assertNotNull(value);
        return StandardCharsets.UTF_8.decode(value.asReadOnlyBuffer()).toString();
    }

    private BucketState bucket(String key) {
        Long fieldCount = withRedis(connection -> connection.hashCommands().hLen(utf8(key)));
        Assertions.assertEquals(3L, fieldCount, "Redis Bucket 必须精确包含 v3 的 t/lr/rr 三字段");
        List<ByteBuffer> values = withRedis(
            connection -> connection.hashCommands().hMGet(utf8(key), List.of(utf8("t"), utf8("lr"), utf8("rr"))));
        Assertions.assertEquals(3, values.size());
        return new BucketState(decodeLong(values.get(0)), decodeLong(values.get(1)), decodeLong(values.get(2)));
    }

    private long redisTimeMillis() {
        return withRedis(connection -> connection.serverCommands().time(TimeUnit.MILLISECONDS));
    }

    private void assertTtlNear(String key, long expectedMillis) {
        long actual = withRedis(connection -> connection.keyCommands().pTtl(utf8(key)));
        Assertions.assertTrue(actual <= expectedMillis && actual >= expectedMillis - 2_000L,
            () -> "Redis TTL 不符合推导值: expected=" + expectedMillis + ", actual=" + actual);
    }

    private <T> T withRedis(Function<ReactiveRedisConnection, Mono<T>> operation) {
        return Mono.usingWhen(Mono.fromSupplier(connectionFactory::getReactiveConnection), operation,
            ReactiveRedisConnection::closeLater, (connection, error) -> connection.closeLater(),
            ReactiveRedisConnection::closeLater).block(REDIS_TIMEOUT);
    }

    private record CommandView(String key, long capacityMicrotokens, long refillMicrotokens, long refillPeriodMillis,
        long requestedMicrotokens) {
        private static CommandView from(RateLimitCommand command) {
            return new CommandView(command.key(), command.capacityMicrotokens(), command.refillMicrotokens(),
                command.refillPeriodMillis(), command.requestedMicrotokens());
        }
    }

    private record BucketState(long tokens, long lastRefillMillis, long refillRemainder) {
    }

    private record NamedDecision(String name, RateLimitDecision decision) {
    }

    private record InvalidScriptInvocation(String name, List<String> keys, List<String> arguments) {
    }
}
