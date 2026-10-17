package com.paytech.gateway.auto.routing;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.gateway.routing.runtime.GatewayRuntimeCatalog;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.routing.runtime.GatewayRuntimeTarget;
import com.paytech.platform.support.api.enums.GatewayIdempotencyMode;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

class GatewayRuntimeCatalogAutoTest {

    @Test
    void findsOnlyExactMethodAndPathInImmutableCatalog() {
        GatewayRuntimeRoute getRoute = route(HttpMethod.GET, "/api/example");
        GatewayRuntimeRoute postRoute = route(HttpMethod.POST, "/api/example");
        GatewayRuntimeCatalog catalog = new GatewayRuntimeCatalog(List.of(getRoute, postRoute));

        assertTrue(catalog.find(GatewayRouteChannel.OPERATIONS, HttpMethod.GET, "/api/example").isPresent());
        assertTrue(catalog.find(GatewayRouteChannel.OPERATIONS, HttpMethod.POST, "/api/example").isPresent());
        assertFalse(catalog.find(GatewayRouteChannel.OPERATIONS, HttpMethod.DELETE, "/api/example").isPresent());
        assertFalse(new GatewayRuntimeCatalog(List.of())
            .find(GatewayRouteChannel.OPERATIONS, HttpMethod.GET, "/api/example").isPresent());
    }

    @Test
    void fixedRouteWinsBeforeSingleSegmentVariableAndVariablesAreSafelyRendered() {
        GatewayRuntimeRoute variable = route(HttpMethod.GET, "/api/clients/{clientId}", "/clients/{clientId}");
        GatewayRuntimeRoute fixed = route(HttpMethod.GET, "/api/clients/current", "/clients/current");
        GatewayRuntimeCatalog catalog = new GatewayRuntimeCatalog(List.of(variable, fixed));

        assertEquals("/clients/current",
            catalog.match(GatewayRouteChannel.OPERATIONS, HttpMethod.GET, "/api/clients/current").orElseThrow()
                .downstreamPath());
        assertEquals("/clients/%E2%82%AC%25value",
            catalog.match(GatewayRouteChannel.OPERATIONS, HttpMethod.GET, "/api/clients/%E2%82%AC%25value")
                .orElseThrow().downstreamPath());
        assertFalse(catalog.match(GatewayRouteChannel.OPERATIONS, HttpMethod.GET, "/api/clients/a%2Fb").isPresent());
        assertFalse(catalog.match(GatewayRouteChannel.OPERATIONS, HttpMethod.GET, "/api/clients/%2E%2E").isPresent());
        assertFalse(catalog.match(GatewayRouteChannel.OPERATIONS, HttpMethod.GET, "/api/clients/two/segments")
            .isPresent());
    }

    @Test
    void rejectsExactDuplicateInTheSameChannel() {
        GatewayRuntimeRoute first = route(HttpMethod.GET, "/api/shared");
        GatewayRuntimeRoute duplicate = new GatewayRuntimeRoute("operations-get-duplicate",
            GatewayRouteChannel.OPERATIONS, HttpMethod.GET, "/api/shared", "/api/shared", new GatewayRuntimeTarget("operations-console-bff", GatewayRouteChannel.OPERATIONS, "operations-console-bff"),
            "PUBLIC", "PUBLIC", null, null, "DROP", "NONE", Set.of(), Set.of(), Set.of(), 0,
            Duration.ofSeconds(1), Duration.ofSeconds(1), null, null, GatewayIdempotencyMode.NONE);

        assertThrows(IllegalArgumentException.class, () -> new GatewayRuntimeCatalog(List.of(first, duplicate)));
    }

    @Test
    void allowsTheSameMethodAndPathInDifferentChannelsAndMatchesOnlyTheRequestedChannel() {
        GatewayRuntimeRoute operations = route(HttpMethod.GET, "/api/shared");
        GatewayRuntimeRoute clientApi = new GatewayRuntimeRoute("client-api-get", GatewayRouteChannel.CLIENT_API, HttpMethod.GET,
            "/api/shared", "/api/shared", new GatewayRuntimeTarget("client-api-bff", GatewayRouteChannel.CLIENT_API, "client-api-bff"), "CLIENT_API_CREDENTIAL", "API_SCOPE", null,
            "orders.read", "DROP", "NONE", Set.of(), Set.of(), Set.of(), 0, Duration.ofSeconds(1),
            Duration.ofSeconds(1), null, null, GatewayIdempotencyMode.NONE);

        GatewayRuntimeCatalog catalog = new GatewayRuntimeCatalog(List.of(operations, clientApi));

        assertEquals(2, catalog.all().size());
        assertSame(operations, catalog.find(GatewayRouteChannel.OPERATIONS, HttpMethod.GET, "/api/shared").orElseThrow());
        assertSame(clientApi, catalog.find(GatewayRouteChannel.CLIENT_API, HttpMethod.GET, "/api/shared").orElseThrow());
        assertTrue(catalog.find(GatewayRouteChannel.CLIENT, HttpMethod.GET, "/api/shared").isEmpty());
        assertEquals(1, catalog.matchesPath(GatewayRouteChannel.OPERATIONS, "/api/shared").size());
        assertEquals(1, catalog.matchesPath(GatewayRouteChannel.CLIENT_API, "/api/shared").size());
        assertTrue(catalog.matchesPath(GatewayRouteChannel.CLIENT, "/api/shared").isEmpty());
    }

    private GatewayRuntimeRoute route(HttpMethod method, String path) {
        return route(method, path, path);
    }

    private GatewayRuntimeRoute route(HttpMethod method, String externalPath, String downstreamPath) {
        return new GatewayRuntimeRoute("example-" + method.name().toLowerCase(), GatewayRouteChannel.OPERATIONS, method, externalPath,
            downstreamPath, new GatewayRuntimeTarget("operations-console-bff", GatewayRouteChannel.OPERATIONS, "operations-console-bff"), "PUBLIC", "PUBLIC", null, null, "DROP", "NONE", Set.of(),
            Set.of(), Set.of(), 0, Duration.ofSeconds(1), Duration.ofSeconds(1), null, null, GatewayIdempotencyMode.NONE);
    }
}
