package com.paytech.gateway.auto.routing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.gateway.routing.ClientRoutingProperties;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotCompiler;
import com.paytech.gateway.routing.snapshot.InvalidGatewaySnapshotException;
import com.paytech.platform.support.api.gatewayconfiguration.response.*;

/** CLIENT Account Context 无 Tenant；编译器仍必须强制 IP 与认证主体的基础保护。 */
class ClientRoutingContractAutoTest {
    @ParameterizedTest
    @ValueSource(strings = {"CLIENT_IP", "AUTHENTICATED_PRINCIPAL"})
    void test_clientJwtWithoutRequiredGuardRejectsSnapshot(String omitted) {
        assertThrows(InvalidGatewaySnapshotException.class, () -> new GatewaySnapshotCompiler().compile(snapshot(omitted)));
    }

    @Test
    void test_accountContextDoesNotRequireTenantGuard() {
        assertEquals(1, new GatewaySnapshotCompiler().compile(snapshot(null)).catalog().all().size());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "https:/", "http://client.test", "https://client.test/", "https://client.test/path",
        "https://user@client.test", "https://client.test?query", "https://client.test#fragment", "https://client.test:65536"})
    void test_enabledClientRequiresExactHttpsOrigin(String origin) {
        assertThrows(IllegalArgumentException.class, () -> new ClientRoutingProperties(true, origin));
    }

    @Test
    void test_disabledClientHasNoTrustedOrigin() {
        assertNull(new ClientRoutingProperties(false, null).trustedOrigin());
        assertNull(new ClientRoutingProperties(false, "https://client.test").trustedOrigin());
        assertEquals("https://client.test", new ClientRoutingProperties(true, "https://client.test").trustedOrigin());
    }

    private GatewayPublicSnapshotResponseBody snapshot(String omitted) {
        var guards = new ArrayList<GatewayGuardRuleSnapshot>();
        for (String dimension : List.of("SHARED_ROUTE", "CLIENT_IP", "AUTHENTICATED_PRINCIPAL")) {
            if (dimension.equals(omitted)) continue;
            guards.add(new GatewayGuardRuleSnapshot("RATE_LIMIT", dimension, 10L, 10L, 60_000L, null));
            guards.add(new GatewayGuardRuleSnapshot("CONCURRENCY", dimension, null, null, null, 2L));
        }
        return new GatewayPublicSnapshotResponseBody(1, List.of(new GatewayTargetSnapshot("client-console-bff", "CLIENT", "client-console-bff")),
            List.of(new GatewayRouteSnapshot("client-context", "CLIENT", "GET", "/api/client/auth/v1/context",
                "/client-auth/v1/context", "client-console-bff", "CLIENT_JWT", "HUMAN_AUTHENTICATED", null, null,
                "DROP", "NONE", List.of(), List.of(), List.of(), 0, 5000, 1000, guards)));
    }
}
