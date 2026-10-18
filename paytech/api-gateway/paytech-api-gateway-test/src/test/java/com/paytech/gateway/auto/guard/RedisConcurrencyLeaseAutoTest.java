package com.paytech.gateway.auto.guard;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.zset.DefaultTuple;
import org.springframework.data.redis.connection.zset.Tuple;
import org.springframework.data.redis.core.types.Expiration;

import com.paytech.gateway.guard.GuardDependencyUnavailableException;
import com.paytech.gateway.guard.concurrency.*;
import com.paytech.gateway.guard.policy.ConcurrencyRule;
import com.paytech.gateway.guard.policy.GuardContext;
import com.paytech.gateway.guard.policy.GuardDimension;
import com.paytech.gateway.guard.policy.GuardProfile;
import com.paytech.gateway.guard.redis.GuardRedisProperties;
import com.paytech.gateway.guard.redis.ReactiveRedisScriptExecutor;
import com.paytech.gateway.guard.redis.RedisGuardKeyFactory;
import com.paytech.gateway.routing.OperationsRouteCatalog;
import com.paytech.gateway.routing.OperationsRouteCode;
import com.paytech.gateway.test.GatewayAutoTestBase;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** 真实 Redis 多维并发 Lease 的原子状态机测试。 */
class RedisConcurrencyLeaseAutoTest extends GatewayAutoTestBase {
    private static final Duration REDIS_TIMEOUT = Duration.ofSeconds(5);
    private static final long STANDARD_LEASE_MILLIS = 5_000L;

    @Autowired
    ReactiveConcurrencyLimiter concurrencyLimiter;
    @Autowired
    ReactiveRedisConnectionFactory connectionFactory;
    @Autowired
    ReactiveRedisScriptExecutor scriptExecutor;
    @Autowired
    RedisGuardKeyFactory keyFactory;
    @Autowired
    GuardRedisProperties redisProperties;
    @Autowired
    ConcurrencyCommandFactory commandFactory;

    private static GuardProfile profile(List<ConcurrencyRule> rules) {
        return new GuardProfile(List.of(), rules, Duration.ofSeconds(30), Duration.ofSeconds(60));
    }

    private static ByteBuffer utf8(String value) {
        return StandardCharsets.UTF_8.encode(value);
    }

    private static String decode(ByteBuffer value) {
        return StandardCharsets.UTF_8.decode(value.asReadOnlyBuffer()).toString();
    }

    private static long decodeLong(ByteBuffer value) {
        Assertions.assertNotNull(value);
        return Long.parseLong(decode(value));
    }

    private static long retrySeconds(long effectiveNow, long expiryMillis, long leaseDurationMillis) {
        long waitMillis = Math.min(leaseDurationMillis, Math.max(0L, expiryMillis - effectiveNow));
        return Math.max(1L, (waitMillis + 999L) / 1_000L);
    }

    private static List<String> replace(List<String> values, int index, String replacement) {
        List<String> copy = new ArrayList<>(values);
        copy.set(index, replacement);
        return List.copyOf(copy);
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    @Test
    void test_successfulAcquireCreatesPairedLeaseAndMetadataState() {
        ConcurrencyCommand command = command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 2);
        long before = redisTimeMillis();

        ConcurrencyDecision decision = acquire(command);

        Assertions.assertTrue(decision.allowed());
        ConcurrencyLease lease = decision.lease().orElseThrow();
        MetadataState metadata = metadata(command.keys());
        Assertions.assertTrue(command.keys().leaseKey().endsWith(":leases"));
        Assertions.assertTrue(command.keys().metadataKey().endsWith(":metadata"));
        Assertions.assertEquals(List.of(command.keys().leaseKey()), lease.leaseKeys());
        Assertions.assertEquals(2L, metadata.limit());
        Assertions.assertTrue(metadata.highWaterMillis() >= before && metadata.highWaterMillis() <= redisTimeMillis());
        Assertions.assertEquals(metadata.highWaterMillis() + command.leaseDurationMillis(),
            score(command.keys().leaseKey(), lease.leaseId()));
        assertPairedTtl(command.keys(), command.stateTtlMillis());
    }

    @Test
    void test_acquireBelowLimitWritesOneLeaseIdToEveryDimension() {
        List<ConcurrencyCommand> commands = List.of(command(sharedKey(OperationsRouteCode.AUTH_LOGOUT), 2), command(
            identityKey(OperationsRouteCode.AUTH_LOGOUT, GuardDimension.AUTHENTICATED_SUBJECT, "operations:用户:17"), 2));

        ConcurrencyDecision decision = acquire(commands);

        Assertions.assertTrue(decision.allowed());
        ConcurrencyLease lease = decision.lease().orElseThrow();
        Assertions.assertEquals(UUID.fromString(lease.leaseId()).toString(), lease.leaseId());
        Assertions.assertEquals(commands.stream().map(ConcurrencyCommand::key).toList(), lease.keys());
        Assertions.assertEquals(Set.of(lease.leaseId()), members(commands.get(0).key()));
        Assertions.assertEquals(Set.of(lease.leaseId()), members(commands.get(1).key()));
    }

    @Test
    void test_exactLimitRejectsWithoutWritingAnotherLease() {
        ConcurrencyCommand command = command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 2);
        ConcurrencyLease first = acquire(command).lease().orElseThrow();
        ConcurrencyLease second = acquire(command).lease().orElseThrow();

        ConcurrencyDecision rejected = acquire(command);

        Assertions.assertFalse(rejected.allowed());
        Assertions.assertTrue(rejected.retryAfterSeconds() >= 1);
        Assertions.assertTrue(rejected.lease().isEmpty());
        Assertions.assertEquals(Set.of(first.leaseId(), second.leaseId()), members(command.key()));
    }

    @Test
    void test_expiredEntriesAreRemovedBeforeCapacityCounting() {
        ConcurrencyCommand command = command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 1);
        seedLease(command, "00000000-0000-0000-0000-000000000001", redisTimeMillis() - 1);

        ConcurrencyDecision decision = acquire(command);

        Assertions.assertTrue(decision.allowed());
        Assertions.assertEquals(Set.of(decision.lease().orElseThrow().leaseId()), members(command.key()));
    }

    @Test
    void test_storedHighWaterPreventsClockRollbackFromShorteningNewLease() {
        ConcurrencyCommand command = command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 2, 5_000L);
        long storedHighWater = redisTimeMillis() + 2_000L;
        seedMetadata(command.keys(), storedHighWater, 2, 20_000L);

        ConcurrencyDecision decision = acquire(command);

        Assertions.assertTrue(decision.allowed());
        String leaseId = decision.lease().orElseThrow().leaseId();
        Assertions.assertEquals(storedHighWater, metadata(command.keys()).highWaterMillis());
        Assertions.assertEquals(storedHighWater + command.leaseDurationMillis(), score(command.key(), leaseId));
    }

    @Test
    void test_higherLimitAppliesImmediatelyWithoutInvalidatingExistingLease() {
        String baseKey = sharedKey(OperationsRouteCode.AUTH_LOGIN);
        ConcurrencyCommand active = command(baseKey, 1, STANDARD_LEASE_MILLIS);
        ConcurrencyLease lease = acquire(active).lease().orElseThrow();

        ConcurrencyDecision expanded = acquire(command(baseKey, 2, STANDARD_LEASE_MILLIS));

        Assertions.assertTrue(expanded.allowed());
        Assertions.assertEquals(Set.of(lease.leaseId(), expanded.lease().orElseThrow().leaseId()),
            members(active.key()));
        Assertions.assertEquals(2L, metadata(active.keys()).limit());
    }

    @Test
    void test_lowerLimitPreservesLiveLeasesAndConservativelyRejectsNewAcquire() {
        String baseKey = sharedKey(OperationsRouteCode.AUTH_LOGIN);
        ConcurrencyCommand original = command(baseKey, 3, STANDARD_LEASE_MILLIS);
        ConcurrencyLease first = acquire(original).lease().orElseThrow();
        ConcurrencyLease second = acquire(original).lease().orElseThrow();
        ConcurrencyLease third = acquire(original).lease().orElseThrow();

        ConcurrencyDecision reduced = acquire(command(baseKey, 2, STANDARD_LEASE_MILLIS));

        Assertions.assertFalse(reduced.allowed());
        Assertions.assertEquals(Set.of(first.leaseId(), second.leaseId(), third.leaseId()), members(original.key()));
        Assertions.assertEquals(2L, metadata(original.keys()).limit());
        release(first);
        Assertions.assertFalse(acquire(command(baseKey, 2, STANDARD_LEASE_MILLIS)).allowed());
        release(second);
        Assertions.assertTrue(acquire(command(baseKey, 2, STANDARD_LEASE_MILLIS)).allowed());
    }

    @Test
    void test_rejectedAcquireReturnsMaximumWaitAcrossDimensions() {
        ConcurrencyCommand fast = command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 1, STANDARD_LEASE_MILLIS);
        ConcurrencyCommand slow = command(sharedKey(OperationsRouteCode.AUTH_REFRESH), 1, STANDARD_LEASE_MILLIS);
        long highWater = redisTimeMillis();
        seedMetadata(fast.keys(), highWater, 1, 20_000L);
        seedMetadata(slow.keys(), highWater, 1, 20_000L);
        seedLeaseOnly(fast.key(), "00000000-0000-0000-0000-000000000013", highWater + 1_000L);
        seedLeaseOnly(slow.key(), "00000000-0000-0000-0000-000000000014", highWater + 3_500L);

        ConcurrencyDecision decision = acquire(List.of(fast, slow));

        long fastWait =
            retrySeconds(metadata(fast.keys()).highWaterMillis(), highWater + 1_000L, STANDARD_LEASE_MILLIS);
        long slowWait =
            retrySeconds(metadata(slow.keys()).highWaterMillis(), highWater + 3_500L, STANDARD_LEASE_MILLIS);
        Assertions.assertFalse(decision.allowed());
        Assertions.assertEquals(Math.max(fastWait, slowWait), decision.retryAfterSeconds());
    }

    @Test
    void test_routeIpAndSubjectAcquisitionIsAllOrNothing() {
        List<ConcurrencyCommand> commands = List.of(command(sharedKey(OperationsRouteCode.AUTH_LOGOUT), 1),
            command(identityKey(OperationsRouteCode.AUTH_LOGOUT, GuardDimension.CLIENT_IP, "203.0.113.17"), 1),
            command(
                identityKey(OperationsRouteCode.AUTH_LOGOUT, GuardDimension.AUTHENTICATED_SUBJECT, "operations:用户:17"),
                1));
        seedLease(commands.get(1), "00000000-0000-0000-0000-000000000002", redisTimeMillis() + STANDARD_LEASE_MILLIS);

        ConcurrencyDecision decision = acquire(commands);

        Assertions.assertFalse(decision.allowed());
        Assertions.assertEquals(0, cardinality(commands.get(0).key()));
        Assertions.assertEquals(1, cardinality(commands.get(1).key()));
        Assertions.assertEquals(0, cardinality(commands.get(2).key()));
    }

    @Test
    void test_rejectionReturnsMaximumCeilingSecondAcrossFullDimensions() {
        String fastKey = sharedKey(OperationsRouteCode.AUTH_LOGIN);
        String slowKey = sharedKey(OperationsRouteCode.AUTH_REFRESH);
        ConcurrencyCommand fast = command(fastKey, 1, STANDARD_LEASE_MILLIS);
        ConcurrencyCommand slow = command(slowKey, 1, STANDARD_LEASE_MILLIS);
        long now = redisTimeMillis();
        seedLease(fast, "00000000-0000-0000-0000-000000000003", now + 1_000L);
        seedLease(slow, "00000000-0000-0000-0000-000000000004", now + 3_500L);

        ConcurrencyDecision decision = acquire(List.of(fast, slow));

        Assertions.assertFalse(decision.allowed());
        Assertions.assertEquals(4, decision.retryAfterSeconds());
    }

    @Test
    void test_releaseIsIdempotentAndNeverRemovesUnrelatedLease() {
        List<ConcurrencyCommand> commands = List.of(command(sharedKey(OperationsRouteCode.AUTH_LOGOUT), 3), command(
            identityKey(OperationsRouteCode.AUTH_LOGOUT, GuardDimension.AUTHENTICATED_SUBJECT, "operations:用户:17"), 3));
        ConcurrencyLease acquired = acquire(commands).lease().orElseThrow();
        String unrelated = "00000000-0000-0000-0000-000000000005";
        commands.forEach(command -> seedLease(command, unrelated, redisTimeMillis() + STANDARD_LEASE_MILLIS));

        Map<ConcurrencyKeys, MetadataState> metadataBefore = commands.stream()
            .collect(java.util.stream.Collectors.toMap(ConcurrencyCommand::keys, command -> metadata(command.keys())));
        Map<ConcurrencyKeys, Long> ttlBefore = commands.stream().collect(java.util.stream.Collectors
            .toMap(ConcurrencyCommand::keys, command -> keyTtl(command.keys().metadataKey())));

        release(acquired);
        release(acquired);

        Assertions.assertEquals(Set.of(unrelated), members(commands.get(0).key()));
        Assertions.assertEquals(Set.of(unrelated), members(commands.get(1).key()));
        commands.forEach(command -> {
            Assertions.assertEquals(metadataBefore.get(command.keys()), metadata(command.keys()));
            Assertions.assertTrue(keyTtl(command.keys().metadataKey()) <= ttlBefore.get(command.keys()));
        });
    }

    @Test
    void test_commandFactoryUsesSemanticDimensionAndRawCanonicalIdentityKeys() throws Exception {
        GuardContext context = new GuardContext(OperationsRouteCode.AUTH_LOGOUT, InetAddress.getByName("203.0.113.17"),
            "operations:用户:17");
        GuardProfile profile = profile(List.of(new ConcurrencyRule(GuardDimension.AUTHENTICATED_SUBJECT, 2),
            new ConcurrencyRule(GuardDimension.SHARED_ROUTE, 50), new ConcurrencyRule(GuardDimension.CLIENT_IP, 7)));

        List<ConcurrencyCommand> first = commandFactory.create(profile, context);
        List<ConcurrencyCommand> second = commandFactory.create(profile, context);

        Assertions.assertEquals(first, second);
        Assertions.assertEquals(
            List.of("shared-route:shared", "client-ip:203.0.113.17", "authenticated-subject:operations:用户:17"),
            first.stream().map(this::keyIdentitySuffix).toList());
        Assertions.assertEquals(List.of(50, 7, 2), first.stream().map(ConcurrencyCommand::limit).toList());
        Assertions.assertTrue(first.stream()
            .allMatch(command -> command.leaseDurationMillis() == 30_000L && command.stateTtlMillis() == 60_000L));
        Assertions.assertTrue(first.stream().allMatch(command -> command.keys().leaseKey().endsWith(":leases")
            && command.keys().metadataKey().endsWith(":metadata")));
    }

    @Test
    void test_tenantLeaseUsesItsOwnCanonicalKeyAndLuaDimensionCode() throws Exception {
        GuardContext context = new GuardContext("client-api-test-post", InetAddress.getLoopbackAddress(),
            "credential-17", "tenant-42");
        GuardProfile profile = profile(List.of(
            new ConcurrencyRule(GuardDimension.AUTHENTICATED_SUBJECT, 2),
            new ConcurrencyRule(GuardDimension.TENANT, 1)));

        List<ConcurrencyCommand> commands = commandFactory.createAuthenticated(profile, context);
        ConcurrencyLease lease = acquire(commands).lease().orElseThrow();
        ConcurrencyDecision rejected = acquire(commands);

        Assertions.assertFalse(rejected.allowed());
        Assertions.assertEquals(GuardDimension.TENANT, rejected.rejectingDimension().orElseThrow());
        Assertions.assertEquals(List.of("authenticated-subject:credential-17", "tenant:tenant-42"),
            commands.stream().map(this::keyIdentitySuffix).toList());
        release(lease);
        Assertions.assertTrue(commands.stream().allMatch(command -> members(command.key()).isEmpty()));
    }

    @Test
    void test_catalogProfileLeaseAndStateTtlIgnoreRouteResponseTimeout() throws Exception {
        GuardContext context =
            new GuardContext(OperationsRouteCode.AUTH_LOGIN, InetAddress.getByName("127.0.0.1"), null);
        GuardProfile profile = new OperationsRouteCatalog(Duration.ofSeconds(1)).all().stream()
            .filter(route -> route.code() == OperationsRouteCode.AUTH_LOGIN).findFirst().orElseThrow().ingressGuard();

        List<ConcurrencyCommand> commands = commandFactory.create(profile, context);

        Assertions.assertEquals(List.of(20, 2), commands.stream().map(ConcurrencyCommand::limit).toList());
        Assertions.assertTrue(commands.stream()
            .allMatch(command -> command.leaseDurationMillis() == 30_000L && command.stateTtlMillis() == 60_000L));
    }

    @Test
    void test_concurrentContendersNeverExceedAnyLimit() {
        List<ConcurrencyCommand> commands = List.of(command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 8),
            command(identityKey(OperationsRouteCode.AUTH_LOGIN, GuardDimension.CLIENT_IP, "203.0.113.17"), 3));

        List<ConcurrencyDecision> decisions = Flux.range(0, 40)
            .flatMap(index -> concurrencyLimiter.acquire(commands), 40).collectList().block(REDIS_TIMEOUT);

        Assertions.assertNotNull(decisions);
        Assertions.assertEquals(3, decisions.stream().filter(ConcurrencyDecision::allowed).count());
        Assertions.assertEquals(3, cardinality(commands.get(0).key()));
        Assertions.assertEquals(3, cardinality(commands.get(1).key()));
        Assertions.assertEquals(
            decisions.stream().filter(ConcurrencyDecision::allowed)
                .map(decision -> decision.lease().orElseThrow().leaseId()).collect(java.util.stream.Collectors.toSet()),
            members(commands.get(0).key()));
    }

    @Test
    void test_keyTtlIsExactlyTwiceLeaseDuration() {
        ConcurrencyCommand command = command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 1, 4_000L);

        acquire(command);

        assertTtlNear(command.key(), 8_000L);
        assertTtlNear(command.keys().metadataKey(), 8_000L);
    }

    @Test
    void test_rejectedAcquireDoesNotRefreshEitherPairedTtl() {
        ConcurrencyCommand command = command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 1, 5_000L);
        acquire(command);
        setTtl(command.keys(), 2_500L);
        long leaseTtlBefore = keyTtl(command.key());
        long metadataTtlBefore = keyTtl(command.keys().metadataKey());

        Assertions.assertFalse(acquire(command).allowed());

        long leaseTtlAfter = keyTtl(command.key());
        long metadataTtlAfter = keyTtl(command.keys().metadataKey());
        Assertions.assertTrue(leaseTtlAfter > 0 && leaseTtlAfter <= leaseTtlBefore);
        Assertions.assertTrue(metadataTtlAfter > 0 && metadataTtlAfter <= metadataTtlBefore);
        Assertions.assertTrue(leaseTtlAfter < command.stateTtlMillis() / 2);
        Assertions.assertTrue(metadataTtlAfter < command.stateTtlMillis() / 2);
    }

    @Test
    void test_releaseLeavesMetadataOnlyStateAndNeverExtendsTtl() {
        ConcurrencyCommand command = command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 1);
        ConcurrencyLease lease = acquire(command).lease().orElseThrow();
        MetadataState before = metadata(command.keys());
        long metadataTtlBefore = keyTtl(command.keys().metadataKey());

        release(lease);
        release(lease);

        Assertions.assertEquals(0, cardinality(command.key()));
        Assertions.assertFalse(exists(command.key()));
        Assertions.assertTrue(exists(command.keys().metadataKey()));
        Assertions.assertEquals(before, metadata(command.keys()));
        Assertions.assertTrue(keyTtl(command.keys().metadataKey()) <= metadataTtlBefore);
        Assertions.assertTrue(acquire(command).allowed(), "metadata-only 状态必须允许后续合法 Acquire");
    }

    @Test
    void test_capacityRecoversAfterLeaseExpiryWithoutRelease() {
        ConcurrencyCommand command = command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 1, 250L);
        acquire(command);
        Assertions.assertFalse(acquire(command).allowed());

        Awaitility.await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(25))
            .until(() -> acquire(command).allowed());

        Assertions.assertEquals(1, cardinality(command.key()));
    }

    @Test
    void test_crashRecoveryExpiresBothKeysWithoutRelease() {
        ConcurrencyCommand command = command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 1, 250L);

        acquire(command);

        Awaitility.await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(25))
            .until(() -> !exists(command.key()) && !exists(command.keys().metadataKey()));
    }

    @Test
    void test_rejectedAcquireDoesNotCreateFreshDimensionMetadata() {
        ConcurrencyCommand full = command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 1);
        ConcurrencyCommand fresh = command(sharedKey(OperationsRouteCode.AUTH_REFRESH), 1);
        acquire(full);

        ConcurrencyDecision decision = acquire(List.of(full, fresh));

        Assertions.assertFalse(decision.allowed());
        Assertions.assertFalse(exists(fresh.key()));
        Assertions.assertFalse(exists(fresh.keys().metadataKey()));
    }

    @Test
    void test_v3AcquireNeverReadsOrMutatesLegacyV2Keys() {
        ConcurrencyCommand command = command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 1);
        String legacyLease = command.key().replace(":guard:v3:", ":guard:v2:");
        String legacyMetadata = command.keys().metadataKey().replace(":guard:v3:", ":guard:v2:");
        seedString(legacyLease, "legacy-lease-untouched", 20_000L);
        seedString(legacyMetadata, "legacy-metadata-untouched", 20_000L);

        Assertions.assertTrue(acquire(command).allowed());

        Assertions.assertEquals("legacy-lease-untouched", stringValue(legacyLease));
        Assertions.assertEquals("legacy-metadata-untouched", stringValue(legacyMetadata));
    }

    @Test
    void test_laterWrongTypePreventsEarlierExpiredCleanupOrLeaseAdd() {
        ConcurrencyCommand earlier = command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 2);
        ConcurrencyCommand later = command(sharedKey(OperationsRouteCode.AUTH_REFRESH), 2);
        String expiredLease = "00000000-0000-0000-0000-000000000006";
        seedLease(earlier, expiredLease, redisTimeMillis() - 1);
        MetadataState metadataBefore = metadata(earlier.keys());
        long leaseTtlBefore = keyTtl(earlier.key());
        long metadataTtlBefore = keyTtl(earlier.keys().metadataKey());
        seedString(later.key(), "not-a-zset", 20_000L);

        StepVerifier.create(concurrencyLimiter.acquire(List.of(earlier, later)))
            .expectError(GuardDependencyUnavailableException.class).verify();

        Assertions.assertEquals(Set.of(expiredLease), members(earlier.key()));
        Assertions.assertEquals(metadataBefore, metadata(earlier.keys()));
        Assertions.assertTrue(keyTtl(earlier.key()) <= leaseTtlBefore);
        Assertions.assertTrue(keyTtl(earlier.keys().metadataKey()) <= metadataTtlBefore);
    }

    @Test
    void test_nonEmptyLeaseSetWithoutMetadataFailsBeforeMutation() {
        ConcurrencyCommand command = command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 2);
        String leaseId = "00000000-0000-0000-0000-000000000015";
        seedLeaseOnly(command.key(), leaseId, redisTimeMillis() + STANDARD_LEASE_MILLIS);

        StepVerifier.create(concurrencyLimiter.acquire(List.of(command)))
            .expectError(GuardDependencyUnavailableException.class).verify();

        Assertions.assertEquals(Set.of(leaseId), members(command.key()));
        Assertions.assertFalse(exists(command.keys().metadataKey()));
    }

    @Test
    void test_malformedMetadataShapesAndMembersFailClosedWithoutEarlierMutation() {
        ConcurrencyCommand earlier = command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 2);
        ConcurrencyCommand later = command(sharedKey(OperationsRouteCode.AUTH_REFRESH), 2);
        String expiredLease = "00000000-0000-0000-0000-000000000016";
        seedLease(earlier, expiredLease, redisTimeMillis() - 1);
        MetadataState earlierMetadata = metadata(earlier.keys());
        List<InvalidStateSeeder> invalidStates = List.of(new InvalidStateSeeder("metadata-wrong-type", () -> {
            seedLeaseOnly(later.key(), "00000000-0000-0000-0000-000000000017",
                redisTimeMillis() + STANDARD_LEASE_MILLIS);
            seedString(later.keys().metadataKey(), "not-a-hash", 20_000L);
        }), new InvalidStateSeeder("metadata-malformed-integer", () -> {
            seedLease(later, "00000000-0000-0000-0000-000000000018", redisTimeMillis() + STANDARD_LEASE_MILLIS);
            setHashField(later.keys().metadataKey(), "l", "not-an-integer");
        }), new InvalidStateSeeder("metadata-unsafe-integer", () -> {
            seedLease(later, "00000000-0000-0000-0000-000000000022", redisTimeMillis() + STANDARD_LEASE_MILLIS);
            setHashField(later.keys().metadataKey(), "hw", "9007199254740992");
        }), new InvalidStateSeeder("metadata-extra-field", () -> {
            seedLease(later, "00000000-0000-0000-0000-000000000019", redisTimeMillis() + STANDARD_LEASE_MILLIS);
            setHashField(later.keys().metadataKey(), "extra", "1");
        }), new InvalidStateSeeder("metadata-missing-field", () -> {
            seedLeaseOnly(later.key(), "00000000-0000-0000-0000-000000000020",
                redisTimeMillis() + STANDARD_LEASE_MILLIS);
            seedMetadataFields(later.keys().metadataKey(), Map.of("hw", "0"), 20_000L);
        }), new InvalidStateSeeder("invalid-lease-member", () -> {
            seedMetadata(later.keys(), redisTimeMillis(), 2, 20_000L);
            seedLeaseOnly(later.key(), "not-a-uuid", redisTimeMillis() + STANDARD_LEASE_MILLIS);
        }), new InvalidStateSeeder("invalid-lease-score", () -> {
            seedMetadata(later.keys(), redisTimeMillis(), 2, 20_000L);
            seedLeaseScore(later.key(), "00000000-0000-0000-0000-000000000021", 1.5d);
        }));

        for (InvalidStateSeeder invalidState : invalidStates) {
            unlink(later.key(), later.keys().metadataKey());
            invalidState.seed().run();

            StepVerifier.create(concurrencyLimiter.acquire(List.of(earlier, later))).as(invalidState.name())
                .expectError(GuardDependencyUnavailableException.class).verify();

            Assertions.assertEquals(Set.of(expiredLease), members(earlier.key()), invalidState.name());
            Assertions.assertEquals(earlierMetadata, metadata(earlier.keys()), invalidState.name());
        }
    }

    @Test
    void test_laterWrongTypePreventsEarlierReleaseRemoval() {
        ConcurrencyCommand earlier = command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 2);
        ConcurrencyCommand later = command(sharedKey(OperationsRouteCode.AUTH_REFRESH), 2);
        String leaseId = "00000000-0000-0000-0000-000000000007";
        seedLease(earlier, leaseId, redisTimeMillis() + STANDARD_LEASE_MILLIS);
        seedString(later.key(), "not-a-zset", 20_000L);

        StepVerifier
            .create(concurrencyLimiter.release(new ConcurrencyLease(leaseId, List.of(earlier.key(), later.key()))))
            .expectError(GuardDependencyUnavailableException.class).verify();

        Assertions.assertEquals(Set.of(leaseId), members(earlier.key()));
    }

    @Test
    void test_rawAcquireValidationFailsBeforeEarlierMutation() {
        ConcurrencyCommand earlier = command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 2);
        ConcurrencyCommand later = command(sharedKey(OperationsRouteCode.AUTH_REFRESH), 2);
        String original = "00000000-0000-0000-0000-000000000008";
        seedLease(earlier, original, redisTimeMillis() - 1);
        ReactiveRedisScriptExecutor.LoadedScript script =
            scriptExecutor.loadUtf8Script("com/paytech/gateway/guard/concurrency/concurrency-acquire.lua");
        String newLease = "00000000-0000-0000-0000-000000000009";
        List<String> validKeys =
            List.of(earlier.key(), earlier.keys().metadataKey(), later.key(), later.keys().metadataKey());
        List<String> validArguments =
            List.of(newLease, redisProperties.namespace(), "2", "5000", "10000", "2", "5000", "10000");
        List<InvalidScriptInvocation> invocations = List.of(
            new InvalidScriptInvocation("argument-count", validKeys,
                validArguments.subList(0, validArguments.size() - 1)),
            new InvalidScriptInvocation("duplicate-key",
                List.of(earlier.key(), earlier.keys().metadataKey(), earlier.key(), later.keys().metadataKey()),
                validArguments),
            new InvalidScriptInvocation("invalid-pair",
                List.of(earlier.key(), later.keys().metadataKey(), later.key(), earlier.keys().metadataKey()),
                validArguments),
            new InvalidScriptInvocation("invalid-lease-id", validKeys, replace(validArguments, 0, "not-a-uuid")),
            new InvalidScriptInvocation("unsafe-number", validKeys, replace(validArguments, 6, "9007199254740992")),
            new InvalidScriptInvocation("invalid-ttl", validKeys, replace(validArguments, 7, "10001")));

        for (InvalidScriptInvocation invocation : invocations) {
            StepVerifier.create(scriptExecutor.execute(script, invocation.keys(), invocation.arguments(), 3))
                .as(invocation.name()).expectError(GuardDependencyUnavailableException.class).verify();
            Assertions.assertEquals(Set.of(original), members(earlier.key()), invocation.name());
        }
    }

    @Test
    void test_javaBoundaryRejectsNonCanonicalAndForeignConcurrencyKeysBeforeRedisExecution() {
        long evalCallsBefore = redisEvalCalls();
        ConcurrencyCommand canonical = command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 2);
        String base = baseKey(canonical);
        int markerIndex = base.indexOf(":guard:v3:");
        String namespace = base.substring(0, markerIndex);
        String canonicalTail = base.substring(markerIndex);
        String identityPrefix = namespace + ":guard:v3:operations-auth-login:concurrency:";
        List<String> invalidBases = List.of(base.replace(":guard:v3:", ":guard:v2:"),
            base.replace(":concurrency:", ":rate:"), namespace + ":guard:v3:outer" + canonicalTail,
            "x".repeat(129) + canonicalTail, "Invalid-Namespace" + canonicalTail,
            "gateway:guard:v4:test" + canonicalTail, identityPrefix + "shared-route:not-shared",
            identityPrefix + "other:shared", identityPrefix + "client-ip", identityPrefix + "authenticated-subject: ",
            identityPrefix + "authenticated-subject:" + Character.toString(0x2003),
            identityPrefix + "authenticated-subject:operations" + Character.toString(0x85) + "user",
            identityPrefix + "authenticated-subject:subject\ncontrol",
            identityPrefix + "authenticated-subject:" + "x".repeat(513),
            identityPrefix + "tenant:", identityPrefix + "tenant:" + Character.toString(0x2003),
            identityPrefix + "tenant:tenant" + Character.toString(0x85) + "identity",
            identityPrefix + "tenant:" + "x".repeat(513),
            namespace + ":guard:v3:Invalid-Route:concurrency:shared-route:shared", "arbitrary");
        invalidBases.forEach(invalidBase -> Assertions.assertThrows(IllegalArgumentException.class,
            () -> ConcurrencyKeys.fromBaseKey(invalidBase), invalidBase));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ConcurrencyKeys(canonical.keys().leaseKey(),
            ConcurrencyKeys.fromBaseKey(sharedKey(OperationsRouteCode.AUTH_REFRESH)).metadataKey()));

        String foreignBase = "foreign-namespace" + base.substring(base.indexOf(":guard:v3:"));
        ConcurrencyKeys foreignKeys = new ConcurrencyKeys(foreignBase + ":leases", foreignBase + ":metadata");
        ConcurrencyCommand foreignCommand = new ConcurrencyCommand(foreignKeys, 2, 5_000L, 10_000L);
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> concurrencyLimiter.acquire(List.of(foreignCommand)), "foreign acquire 必须在创建 Redis publisher 前失败");
        ConcurrencyLease foreignLease =
            new ConcurrencyLease(UUID.randomUUID().toString(), List.of(foreignKeys.leaseKey()));
        Assertions.assertThrows(IllegalArgumentException.class, () -> concurrencyLimiter.release(foreignLease),
            "foreign release 必须在创建 Redis publisher 前失败");

        List<String> invalidLeaseKeys = List.of(canonical.keys().metadataKey(),
            canonical.key().replace(":guard:v3:", ":guard:v2:"), "arbitrary:leases");
        invalidLeaseKeys.forEach(invalidLeaseKey -> Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ConcurrencyLease(UUID.randomUUID().toString(), List.of(invalidLeaseKey)), invalidLeaseKey));
        Assertions.assertEquals(evalCallsBefore, redisEvalCalls(), "Java canonical key boundary 不得执行 Redis EVAL");
    }

    @Test
    void test_rawLuaRejectsNonCanonicalAcquireAndReleaseKeysBeforeMutation() {
        ConcurrencyCommand earlier = command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 1);
        ConcurrencyLease lease = acquire(earlier).lease().orElseThrow();
        MetadataState metadataBefore = metadata(earlier.keys());
        Set<String> membersBefore = members(earlier.key());
        ReactiveRedisScriptExecutor.LoadedScript acquireScript =
            scriptExecutor.loadUtf8Script("com/paytech/gateway/guard/concurrency/concurrency-acquire.lua");
        ReactiveRedisScriptExecutor.LoadedScript releaseScript =
            scriptExecutor.loadUtf8Script("com/paytech/gateway/guard/concurrency/concurrency-release.lua");
        String base = baseKey(earlier);
        String foreignBase = "foreign-namespace" + base.substring(base.indexOf(":guard:v3:"));
        String dimensionPrefix = redisProperties.namespace() + ":guard:v3:operations-auth-login:concurrency:";
        String identityPrefix = dimensionPrefix + "authenticated-subject:";
        List<String> invalidBases = List.of(base.replace(":guard:v3:", ":guard:v2:"),
            base.replace(":concurrency:", ":rate:"), foreignBase, identityPrefix + Character.toString(0x2003),
            identityPrefix + "operations" + Character.toString(0x85) + "user",
            dimensionPrefix + "shared-route:not-shared", dimensionPrefix + "unknown:identity",
            dimensionPrefix + "tenant:",
            dimensionPrefix + "tenant:" + Character.toString(0x2003),
            dimensionPrefix + "tenant:tenant" + Character.toString(0x85) + "identity",
            dimensionPrefix + "tenant:" + "x".repeat(513), "arbitrary");
        List<String> acquireArguments = List.of(UUID.randomUUID().toString(), redisProperties.namespace(), "1", "5000",
            "10000", "1", "5000", "10000");
        for (String invalidBase : invalidBases) {
            List<String> keys = List.of(earlier.key(), earlier.keys().metadataKey(), invalidBase + ":leases",
                invalidBase + ":metadata");
            StepVerifier.create(scriptExecutor.execute(acquireScript, keys, acquireArguments, 3))
                .as("acquire " + invalidBase).expectError(GuardDependencyUnavailableException.class).verify();
            Assertions.assertEquals(membersBefore, members(earlier.key()), invalidBase);
            Assertions.assertEquals(metadataBefore, metadata(earlier.keys()), invalidBase);
        }

        List<String> invalidReleaseKeys = new ArrayList<>(invalidBases.stream().map(value -> value + ":leases").toList());
        invalidReleaseKeys.add(earlier.keys().metadataKey());
        for (String invalidReleaseKey : invalidReleaseKeys) {
            StepVerifier
                .create(scriptExecutor.execute(releaseScript, List.of(earlier.key(), invalidReleaseKey),
                    List.of(lease.leaseId(), redisProperties.namespace()), 1))
                .as("release " + invalidReleaseKey).expectError(GuardDependencyUnavailableException.class).verify();
            Assertions.assertEquals(membersBefore, members(earlier.key()), invalidReleaseKey);
        }
    }

    @Test
    void test_acquireScriptUsesExactlyOneRedisTimeRead() throws Exception {
        String source = new ClassPathResource("com/paytech/gateway/guard/concurrency/concurrency-acquire.lua")
            .getContentAsString(StandardCharsets.UTF_8);

        Assertions.assertEquals(1, countOccurrences(source, "redis.call('TIME')"));
    }

    @Test
    void test_javaAdapterRejectsInvalidScriptReplySemantics() throws Exception {
        Method toDecision =
            RedisReactiveConcurrencyLimiter.class.getDeclaredMethod("toDecision", List.class, String.class, List.class);
        toDecision.setAccessible(true);
        String leaseId = UUID.randomUUID().toString();
        List<String> leaseKeys = List.of(command(sharedKey(OperationsRouteCode.AUTH_LOGIN), 1).key());
        for (List<Long> invalidReply : List.of(List.of(1L, 1L, 0L), List.of(1L, 0L, 2L), List.of(2L, 0L, 0L),
            List.of(0L, 0L, 1L), List.of(0L, 1L, 0L), List.of(0L, 1L, 5L), List.of(0L, 9_007_199_254_740_992L, 1L))) {
            InvocationTargetException exception = Assertions.assertThrows(InvocationTargetException.class,
                () -> toDecision.invoke(null, invalidReply, leaseId, leaseKeys));
            Assertions.assertInstanceOf(GuardDependencyUnavailableException.class, exception.getCause());
        }

        Method validateReleaseReply =
            RedisReactiveConcurrencyLimiter.class.getDeclaredMethod("validateReleaseReply", List.class);
        validateReleaseReply.setAccessible(true);
        for (long invalidRemoved : List.of(-1L, 9_007_199_254_740_992L)) {
            @SuppressWarnings("unchecked")
            Mono<Void> result = (Mono<Void>)validateReleaseReply.invoke(null, List.of(invalidRemoved));
            StepVerifier.create(result).expectError(GuardDependencyUnavailableException.class).verify();
        }
    }

    @Test
    void test_valueObjectsRejectDuplicateKeysAndFreeFormTtl() {
        String key = sharedKey(OperationsRouteCode.AUTH_LOGIN);
        ConcurrencyKeys keys = ConcurrencyKeys.fromBaseKey(key);
        String leaseEndingBase =
            identityKey(OperationsRouteCode.AUTH_LOGIN, GuardDimension.AUTHENTICATED_SUBJECT, "operations:leases");
        String metadataEndingBase =
            identityKey(OperationsRouteCode.AUTH_LOGIN, GuardDimension.AUTHENTICATED_SUBJECT, "operations:metadata");
        ConcurrencyKeys leaseEndingKeys = ConcurrencyKeys.fromBaseKey(leaseEndingBase);
        ConcurrencyKeys metadataEndingKeys = ConcurrencyKeys.fromBaseKey(metadataEndingBase);
        Assertions.assertEquals(leaseEndingBase + ":leases", leaseEndingKeys.leaseKey());
        Assertions.assertEquals(leaseEndingBase + ":metadata", leaseEndingKeys.metadataKey());
        Assertions.assertEquals(metadataEndingBase + ":leases", metadataEndingKeys.leaseKey());
        Assertions.assertEquals(metadataEndingBase + ":metadata", metadataEndingKeys.metadataKey());
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ConcurrencyCommand(keys, 1, 5_000L, 10_001L));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ConcurrencyCommand(keys, 0, 5_000L, 10_000L));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ConcurrencyKeys(keys.leaseKey(), keys.leaseKey()));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ConcurrencyKeys("invalid:lease", "invalid:metadata"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ConcurrencyKeys(":leases", ":metadata"));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ConcurrencyKeys("first:leases", "second:metadata"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ConcurrencyKeys(null, "invalid:metadata"));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ConcurrencyLease(UUID.randomUUID().toString(), List.of(keys.leaseKey(), keys.leaseKey())));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ConcurrencyLease(UUID.randomUUID().toString(), Arrays.asList((String)null)));
        Assertions.assertThrows(IllegalArgumentException.class, () -> concurrencyLimiter.acquire(List.of()));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> concurrencyLimiter.acquire(List.of(command(key, 1), command(key, 1))));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ConcurrencyDecision(true, 1,
                Optional.of(new ConcurrencyLease(UUID.randomUUID().toString(), List.of(keys.leaseKey()))),
                Optional.empty()));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> ConcurrencyDecision.rejected(0, GuardDimension.SHARED_ROUTE));
    }

    private ConcurrencyDecision acquire(ConcurrencyCommand command) {
        return acquire(List.of(command));
    }

    private ConcurrencyDecision acquire(List<ConcurrencyCommand> commands) {
        return concurrencyLimiter.acquire(commands).block(REDIS_TIMEOUT);
    }

    private void release(ConcurrencyLease lease) {
        concurrencyLimiter.release(lease).block(REDIS_TIMEOUT);
    }

    private ConcurrencyCommand command(String key, int limit) {
        return command(key, limit, STANDARD_LEASE_MILLIS);
    }

    private ConcurrencyCommand command(String key, int limit, long leaseMillis) {
        return new ConcurrencyCommand(ConcurrencyKeys.fromBaseKey(key), limit, leaseMillis, leaseMillis * 2);
    }

    private String baseKey(ConcurrencyCommand command) {
        return command.key().substring(0, command.key().length() - ":leases".length());
    }

    private String sharedKey(OperationsRouteCode routeCode) {
        return keyFactory.concurrencyKey(routeCode, GuardDimension.SHARED_ROUTE);
    }

    private String identityKey(OperationsRouteCode routeCode, GuardDimension dimension, String rawIdentity) {
        return keyFactory.concurrencyKey(routeCode, dimension, rawIdentity);
    }

    private void seedLease(ConcurrencyCommand command, String leaseId, long expiryMillis) {
        if (!exists(command.keys().metadataKey())) {
            seedMetadata(command.keys(), redisTimeMillis(), command.limit(), command.stateTtlMillis());
        }
        seedLeaseOnly(command.key(), leaseId, expiryMillis);
        withRedis(connection -> connection.keyCommands()
            .pExpire(utf8(command.key()), Duration.ofMillis(command.stateTtlMillis())).then());
    }

    private void seedLeaseOnly(String key, String leaseId, long expiryMillis) {
        Tuple tuple = new DefaultTuple(leaseId.getBytes(StandardCharsets.UTF_8), (double)expiryMillis);
        withRedis(connection -> connection.zSetCommands().zAdd(utf8(key), List.of(tuple)).then());
    }

    private void seedLeaseScore(String key, String leaseId, double expiryMillis) {
        Tuple tuple = new DefaultTuple(leaseId.getBytes(StandardCharsets.UTF_8), expiryMillis);
        withRedis(connection -> connection.zSetCommands().zAdd(utf8(key), List.of(tuple)).then());
    }

    private void seedMetadata(ConcurrencyKeys keys, long highWaterMillis, long limit, long ttlMillis) {
        seedMetadataFields(keys.metadataKey(), Map.of("hw", Long.toString(highWaterMillis), "l", Long.toString(limit)),
            ttlMillis);
    }

    private void seedMetadataFields(String key, Map<String, String> values, long ttlMillis) {
        Map<ByteBuffer, ByteBuffer> fields = new LinkedHashMap<>();
        values.forEach((field, value) -> fields.put(utf8(field), utf8(value)));
        withRedis(connection -> connection.hashCommands().hMSet(utf8(key), fields)
            .then(connection.keyCommands().pExpire(utf8(key), Duration.ofMillis(ttlMillis))).then());
    }

    private void setHashField(String key, String field, String value) {
        withRedis(connection -> connection.hashCommands().hSet(utf8(key), utf8(field), utf8(value)).then());
    }

    private void seedString(String key, String value, long ttlMillis) {
        withRedis(connection -> connection.stringCommands()
            .set(utf8(key), utf8(value), Expiration.milliseconds(ttlMillis), RedisStringCommands.SetOption.UPSERT)
            .then());
    }

    private String stringValue(String key) {
        ByteBuffer value = withRedis(connection -> connection.stringCommands().get(utf8(key)));
        Assertions.assertNotNull(value);
        return decode(value);
    }

    private Set<String> members(String key) {
        return Set.copyOf(withRedis(connection -> connection.zSetCommands().zRange(utf8(key), Range.closed(0L, -1L))
            .map(RedisConcurrencyLeaseAutoTest::decode).collectList()));
    }

    private long score(String key, String member) {
        Double value = withRedis(connection -> connection.zSetCommands().zScore(utf8(key), utf8(member)));
        Assertions.assertNotNull(value);
        return (long)value.doubleValue();
    }

    private int cardinality(String key) {
        Long value = withRedis(connection -> connection.zSetCommands().zCard(utf8(key)).defaultIfEmpty(0L));
        return Math.toIntExact(value == null ? 0 : value);
    }

    private long redisTimeMillis() {
        return withRedis(connection -> connection.serverCommands().time(TimeUnit.MILLISECONDS));
    }

    private long redisEvalCalls() {
        Properties commandStats = withRedis(connection -> connection.serverCommands().info("commandstats"));
        String evalStats = commandStats.getProperty("cmdstat_eval", "calls=0");
        return Arrays.stream(evalStats.split(",")).filter(value -> value.startsWith("calls="))
            .mapToLong(value -> Long.parseLong(value.substring("calls=".length()))).findFirst()
            .orElseThrow(() -> new IllegalStateException("Redis commandstats 缺少 EVAL calls"));
    }

    private MetadataState metadata(ConcurrencyKeys keys) {
        Long fieldCount = withRedis(connection -> connection.hashCommands().hLen(utf8(keys.metadataKey())));
        Assertions.assertEquals(2L, fieldCount, "并发 metadata 必须精确包含 v3 的 hw/l 两字段");
        List<ByteBuffer> values = withRedis(
            connection -> connection.hashCommands().hMGet(utf8(keys.metadataKey()), List.of(utf8("hw"), utf8("l"))));
        Assertions.assertEquals(2, values.size());
        return new MetadataState(decodeLong(values.get(0)), decodeLong(values.get(1)));
    }

    private boolean exists(String key) {
        Boolean value = withRedis(connection -> connection.keyCommands().exists(utf8(key)));
        return Boolean.TRUE.equals(value);
    }

    private long keyTtl(String key) {
        return withRedis(connection -> connection.keyCommands().pTtl(utf8(key)));
    }

    private void setTtl(ConcurrencyKeys keys, long ttlMillis) {
        withRedis(connection -> connection.keyCommands().pExpire(utf8(keys.leaseKey()), Duration.ofMillis(ttlMillis))
            .then(connection.keyCommands().pExpire(utf8(keys.metadataKey()), Duration.ofMillis(ttlMillis))).then());
    }

    private void assertPairedTtl(ConcurrencyKeys keys, long expectedMillis) {
        long leaseTtl = keyTtl(keys.leaseKey());
        long metadataTtl = keyTtl(keys.metadataKey());
        Assertions.assertTrue(leaseTtl <= expectedMillis && leaseTtl >= expectedMillis - 2_000L);
        Assertions.assertTrue(metadataTtl <= expectedMillis && metadataTtl >= expectedMillis - 2_000L);
        Assertions.assertTrue(Math.abs(leaseTtl - metadataTtl) <= 100L,
            () -> "并发 paired TTL 不一致: lease=" + leaseTtl + ", metadata=" + metadataTtl);
    }

    private void unlink(String... keys) {
        withRedis(connection -> connection.keyCommands()
            .mUnlink(java.util.Arrays.stream(keys).map(RedisConcurrencyLeaseAutoTest::utf8).toList()).then());
    }

    private void assertTtlNear(String key, long expectedMillis) {
        long actual = withRedis(connection -> connection.keyCommands().pTtl(utf8(key)));
        Assertions.assertTrue(actual <= expectedMillis && actual >= expectedMillis - 2_000L,
            () -> "Redis TTL 不符合 Lease 推导值: expected=" + expectedMillis + ", actual=" + actual);
    }

    private <T> T withRedis(Function<ReactiveRedisConnection, Mono<T>> operation) {
        return Mono.usingWhen(Mono.fromSupplier(connectionFactory::getReactiveConnection), operation,
            ReactiveRedisConnection::closeLater, (connection, error) -> connection.closeLater(),
            ReactiveRedisConnection::closeLater).block(REDIS_TIMEOUT);
    }

    private String keyIdentitySuffix(ConcurrencyCommand command) {
        String baseKey = command.key().substring(0, command.key().length() - ":leases".length());
        String marker = ":concurrency:";
        return baseKey.substring(baseKey.indexOf(marker) + marker.length());
    }

    private record InvalidScriptInvocation(String name, List<String> keys, List<String> arguments) {
    }

    private record InvalidStateSeeder(String name, Runnable seed) {
    }

    private record MetadataState(long highWaterMillis, long limit) {
    }
}
