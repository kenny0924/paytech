package com.paytech.gateway.test;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

import org.springframework.test.context.DynamicPropertyRegistry;

import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.test.redis.RedisTestNamespace;
import com.paytech.gateway.test.simulator.bff.OperationsBffSimulator;
import com.paytech.gateway.test.simulator.clientapi.ClientApiOwnerSimulator;
import com.paytech.gateway.test.simulator.jwks.JwksSimulator;
import com.paytech.gateway.test.simulator.platform.PlatformSnapshotSimulator;
import com.paytech.gateway.test.simulator.platform.PlatformCredentialValidationSimulator;

/** 只注册运行时产生的 Simulator 地址和隔离标识；固定配置归 application-auto.yml。 */
public final class GatewayAutoTestRuntime {
    private static final PlatformCredentialValidationSimulator CREDENTIAL_SIMULATOR =
        new PlatformCredentialValidationSimulator();
    private static final OperationsBffSimulator OPERATIONS_BFF_SIMULATOR = new OperationsBffSimulator();
    private static final ClientApiOwnerSimulator CLIENT_API_OWNER_SIMULATOR = new ClientApiOwnerSimulator();
    private static final JwksSimulator JWKS_SIMULATOR = new JwksSimulator();
    private static final PlatformSnapshotSimulator PLATFORM_SNAPSHOT_SIMULATOR =
        new PlatformSnapshotSimulator(GatewaySnapshotFixture.TEST_ROUTE_RESPONSE_TIMEOUT);
    public static final String FIRST_DYNAMIC_SERVICE = "snapshot-first-" + UUID.randomUUID();
    public static final String SECOND_DYNAMIC_SERVICE = "snapshot-second-" + UUID.randomUUID();
    private static final RedisTestNamespace REDIS_NAMESPACE = RedisTestNamespace.from(UUID.randomUUID());

    private GatewayAutoTestRuntime() {}

    public static PlatformCredentialValidationSimulator credentialSimulator() {
        return CREDENTIAL_SIMULATOR;
    }

    public static OperationsBffSimulator operationsBffSimulator() {
        OPERATIONS_BFF_SIMULATOR.start();
        return OPERATIONS_BFF_SIMULATOR;
    }

    public static JwksSimulator jwksSimulator() {
        JWKS_SIMULATOR.start();
        return JWKS_SIMULATOR;
    }

    public static ClientApiOwnerSimulator clientApiOwnerSimulator() {
        return CLIENT_API_OWNER_SIMULATOR;
    }

    public static PlatformSnapshotSimulator platformSnapshotSimulator() {
        PLATFORM_SNAPSHOT_SIMULATOR.start();
        return PLATFORM_SNAPSHOT_SIMULATOR;
    }

    public static RedisTestNamespace redisNamespace() {
        return REDIS_NAMESPACE;
    }

    public static void registerProperties(DynamicPropertyRegistry registry) {
        registerProperties(registry, operationsBffSimulator().uri());
    }

    public static void registerProperties(DynamicPropertyRegistry registry, URI operationsBffUri) {
        CLIENT_API_OWNER_SIMULATOR.start();
        CREDENTIAL_SIMULATOR.start();
        registry.add("gateway.platform-credential.base-uri", () -> CREDENTIAL_SIMULATOR.uri().toString());
        JwksSimulator jwksSimulator = jwksSimulator();
        URI bffUri = Objects.requireNonNull(operationsBffUri, "Operations BFF 测试 URI 不可为空");
        registry.add("spring.cloud.discovery.client.simple.instances.operations-console-bff[0].uri", bffUri::toString);
        registry.add("spring.cloud.discovery.client.simple.instances." + GatewaySnapshotFixture.CLIENT_API_TARGET
            + "[0].uri", () -> clientApiOwnerSimulator().uri().toString());
        registry.add("spring.cloud.discovery.client.simple.instances." + GatewaySnapshotFixture.OPERATIONS_TRANSPORT_TARGET
            + "[0].uri", () -> clientApiOwnerSimulator().uri().toString());
        registry.add("spring.cloud.discovery.client.simple.instances." + FIRST_DYNAMIC_SERVICE + "[0].uri",
            bffUri::toString);
        registry.add("spring.cloud.discovery.client.simple.instances." + SECOND_DYNAMIC_SERVICE + "[0].uri",
            () -> clientApiOwnerSimulator().uri().toString());
        registry.add("gateway.platform-snapshot.base-uri", () -> platformSnapshotSimulator().uri().toString());
        registry.add("gateway.platform-jwks.base-uri", () -> jwksSimulator.uri().toString());
        registry.add("gateway.operations.guard.redis.namespace", REDIS_NAMESPACE::value);
    }
}
