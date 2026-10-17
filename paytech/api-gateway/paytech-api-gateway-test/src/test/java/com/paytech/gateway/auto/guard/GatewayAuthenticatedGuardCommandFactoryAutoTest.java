package com.paytech.gateway.auto.guard;

import java.net.InetAddress;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.paytech.gateway.guard.concurrency.ConcurrencyCommand;
import com.paytech.gateway.guard.concurrency.ConcurrencyCommandFactory;
import com.paytech.gateway.guard.concurrency.ConcurrencyKeys;
import com.paytech.gateway.guard.policy.GuardContext;
import com.paytech.gateway.guard.ratelimit.RateLimitCommand;
import com.paytech.gateway.guard.ratelimit.RateLimitCommandFactory;
import com.paytech.gateway.guard.redis.GuardRedisProperties;
import com.paytech.gateway.guard.redis.RedisGuardKeyFactory;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotCompiler;
import com.paytech.gateway.security.identity.OperationsIdentity;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;

/** 认证 Guard 命令是 typed Principal/Tenant 到 Redis Key 的纯确定性映射。 */
class GatewayAuthenticatedGuardCommandFactoryAutoTest {
    private static final String NAMESPACE = "gateway:test";
    private static final String CREDENTIAL = "credential-canonical-17";
    private static final String TENANT = "tenant-canonical-42";

    private final RedisGuardKeyFactory keyFactory = new RedisGuardKeyFactory(new GuardRedisProperties(NAMESPACE));
    private final RateLimitCommandFactory rateFactory = new RateLimitCommandFactory(keyFactory);
    private final ConcurrencyCommandFactory concurrencyFactory = new ConcurrencyCommandFactory(keyFactory);

    @Test
    void test_clientCommandsUseExactPrincipalAndTenantKeys() throws Exception {
        GatewayRuntimeRoute route = clientRoute();
        GuardContext context =
            new GuardContext(route.routeCode(), InetAddress.getByName("203.0.113.17"), CREDENTIAL, TENANT);

        Assertions.assertEquals(List.of(
            "gateway:test:guard:v3:client-api-test-post:rate:authenticated-subject:" + CREDENTIAL,
            "gateway:test:guard:v3:client-api-test-post:rate:tenant:" + TENANT),
            rateFactory.createAuthenticated(route.authenticatedGuard(), context).stream().map(RateLimitCommand::key)
                .toList());
        Assertions.assertEquals(List.of(
            "gateway:test:guard:v3:client-api-test-post:concurrency:authenticated-subject:" + CREDENTIAL
                + ":leases",
            "gateway:test:guard:v3:client-api-test-post:concurrency:tenant:" + TENANT + ":leases"),
            concurrencyFactory.createAuthenticated(route.authenticatedGuard(), context).stream()
                .map(ConcurrencyCommand::key).toList());
    }

    @Test
    void test_operationsTenantContextCommandsRemainSubjectOnly() throws Exception {
        GatewayRuntimeRoute route = operationsLogoutRoute();
        GuardContext context = new GuardContext(route.routeCode(), InetAddress.getLoopbackAddress(),
            "operations-subject", "internal-tenant");

        Assertions.assertEquals(List.of(
            "gateway:test:guard:v3:operations-auth-logout:rate:authenticated-subject:operations-subject"),
            rateFactory.createAuthenticated(route.authenticatedGuard(), context).stream().map(RateLimitCommand::key)
                .toList());
        Assertions.assertEquals(List.of(
            "gateway:test:guard:v3:operations-auth-logout:concurrency:authenticated-subject:operations-subject:leases"),
            concurrencyFactory.createAuthenticated(route.authenticatedGuard(), context).stream()
                .map(ConcurrencyCommand::key).toList());
    }

    @Test
    void test_stageSpecificFactoriesRejectDimensionsOwnedByTheOtherStage() throws Exception {
        GatewayRuntimeRoute route = clientRoute();
        GuardContext context =
            new GuardContext(route.routeCode(), InetAddress.getByName("203.0.113.17"), CREDENTIAL, TENANT);

        Assertions.assertAll(
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> rateFactory.createIngress(route.authenticatedGuard(), context)),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> concurrencyFactory.createIngress(route.authenticatedGuard(), context)),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> rateFactory.createAuthenticated(route.ingressGuard(), context)),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> concurrencyFactory.createAuthenticated(route.ingressGuard(), context)));
    }

    @Test
    void test_identityBearingValueObjectsDoNotExposeRawValuesInToString() throws Exception {
        String identityCanary = "secret-identity-canary-842";
        String routeCanary = "secret-route-canary-731";
        String ipCanary = "203.0.113.83";
        OperationsIdentity operations =
            new OperationsIdentity(identityCanary, "session-canary", "TENANT", "tenant-canary", "member-canary");
        ConcurrencyKeys keys = ConcurrencyKeys.fromBaseKey(
            "gateway:test:guard:v3:client-api-test-post:concurrency:tenant:" + identityCanary);
        ConcurrencyCommand command = new ConcurrencyCommand(keys, 3, 30_000, 60_000);
        GuardContext context =
            new GuardContext(routeCanary, InetAddress.getByName(ipCanary), identityCanary, "tenant-canary");

        Assertions.assertAll(
            () -> Assertions.assertFalse(operations.toString().contains(identityCanary)),
            () -> Assertions.assertFalse(operations.toString().contains("session-canary")),
            () -> Assertions.assertFalse(operations.toString().contains("tenant-canary")),
            () -> Assertions.assertFalse(operations.toString().contains("member-canary")),
            () -> Assertions.assertFalse(keys.toString().contains(identityCanary)),
            () -> Assertions.assertFalse(keys.toString().contains(keys.leaseKey())),
            () -> Assertions.assertFalse(command.toString().contains(identityCanary)),
            () -> Assertions.assertFalse(command.toString().contains(keys.leaseKey())),
            () -> Assertions.assertFalse(context.toString().contains(identityCanary)),
            () -> Assertions.assertFalse(context.toString().contains("tenant-canary")),
            () -> Assertions.assertFalse(context.toString().contains(routeCanary)),
            () -> Assertions.assertFalse(context.toString().contains(ipCanary)),
            () -> Assertions.assertTrue(keys.toString().contains("leaseKeyPresent=true")),
            () -> Assertions.assertTrue(keys.toString().contains("metadataKeyPresent=true")));
    }

    @Test
    void test_identityValidationErrorsDoNotEchoRawValues() {
        String principalCanary = "principal-sensitive-canary\n";
        String tenantCanary = "tenant-sensitive-canary\n";

        IllegalArgumentException contextFailure = Assertions.assertThrows(IllegalArgumentException.class,
            () -> new GuardContext("client-api-test-post", InetAddress.getLoopbackAddress(), principalCanary,
                "tenant-42"));
        IllegalArgumentException identityFailure = Assertions.assertThrows(IllegalArgumentException.class,
            () -> new OperationsIdentity("operations-subject", "operations-session", "TENANT", tenantCanary,
                "member-42"));

        Assertions.assertFalse(contextFailure.getMessage().contains(principalCanary));
        Assertions.assertFalse(identityFailure.getMessage().contains(tenantCanary));
    }

    private GatewayRuntimeRoute clientRoute() {
        return new GatewaySnapshotCompiler()
            .compile(GatewaySnapshotFixture.clientApiSnapshot()).catalog().all().getFirst();
    }

    private GatewayRuntimeRoute operationsLogoutRoute() {
        return new GatewaySnapshotCompiler()
            .compile(GatewaySnapshotFixture.operationsSnapshot()).catalog().all().stream()
            .filter(route -> route.routeCode().equals("operations-auth-logout")).findFirst().orElseThrow();
    }
}
