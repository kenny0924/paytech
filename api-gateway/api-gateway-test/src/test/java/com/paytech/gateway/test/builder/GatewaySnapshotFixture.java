package com.paytech.gateway.test.builder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.paytech.platform.support.api.enums.GatewayRouteChannel;
import com.paytech.gateway.web.GatewayPaths;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayGuardRuleSnapshot;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayPublicSnapshotResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayRouteSnapshot;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayTargetSnapshot;

/** 与 Platform 受控初始数据等价的 Gateway Snapshot 测试夹具。 */
public final class GatewaySnapshotFixture {
    public static final Duration TEST_ROUTE_RESPONSE_TIMEOUT = Duration.ofSeconds(1);
    public static final String CLIENT_API_TARGET = "client-api-owner-test";
    public static final String OPERATIONS_TRANSPORT_TARGET = "operations-transport-owner-test";
    public static final String CLIENT_API_PATH = "/test/client-api";
    public static final String CLIENT_API_DOWNSTREAM_PATH = "/client/test";
    private static final String TARGET = "operations-console-bff";

    private GatewaySnapshotFixture() {}

    public static GatewayPublicSnapshotResponseBody clientApiSnapshot() {
        List<GatewayGuardRuleSnapshot> guards = new ArrayList<>(
            withAuthenticatedPrincipal(ingress(300, 300, 10, 30, 50, 3)));
        guards.add(rate("TENANT", 100, 100));
        guards.add(concurrency("TENANT", 10));
        return clientApiSnapshot(List.copyOf(guards));
    }

    /** Route 之前的短路测试也使用 Client API 的完整四维 Guard 合同。 */
    public static GatewayPublicSnapshotResponseBody clientApiRoutingSnapshot() {
        return clientApiSnapshot();
    }

    private static GatewayPublicSnapshotResponseBody clientApiSnapshot(List<GatewayGuardRuleSnapshot> guards) {
        return new GatewayPublicSnapshotResponseBody(1,
            List.of(new GatewayTargetSnapshot(CLIENT_API_TARGET, "CLIENT_API", CLIENT_API_TARGET)),
            List.of(new GatewayRouteSnapshot("client-api-test-post", "CLIENT_API", "POST", CLIENT_API_PATH,
                CLIENT_API_DOWNSTREAM_PATH, CLIENT_API_TARGET, "CLIENT_API_CREDENTIAL", "API_SCOPE", null,
                "orders.write", "DROP", "NONE", List.of(), List.of("content-type", "idempotency-key"),
                List.of(), 16_384, 5_000, 1_000, List.copyOf(guards))));
    }

    /** 沿用真实 Operations 安全链，专门验证第二个部署 transport Target。 */
    public static GatewayPublicSnapshotResponseBody operationsTransportSnapshot() {
        GatewayRouteSnapshot route = operationsSnapshot().getRoutes().getFirst();
        return new GatewayPublicSnapshotResponseBody(1,
            List.of(new GatewayTargetSnapshot(OPERATIONS_TRANSPORT_TARGET, "OPERATIONS", OPERATIONS_TRANSPORT_TARGET)),
            List.of(new GatewayRouteSnapshot(route.getRouteCode(), route.getChannel(), route.getHttpMethod(),
                route.getExternalPathTemplate(), route.getDownstreamPathTemplate(), OPERATIONS_TRANSPORT_TARGET,
                route.getSecurityProfileCode(), route.getAuthorizationMode(), route.getPermissionCode(),
                route.getRequiredScopeCode(), route.getRequestCookieMode(), route.getResponseCookieMode(),
                route.getCorsRequestHeaderNames(), route.getForwardedRequestHeaderNames(), route.getQueryParameterNames(),
                route.getBodyLimitBytes(), route.getBodyReceiveTimeoutMillis(), route.getResponseTimeoutMillis(), route.getGuardRules())));
    }

    public static GatewayPublicSnapshotResponseBody operationsSnapshot() {
        return operationsSnapshot(TEST_ROUTE_RESPONSE_TIMEOUT);
    }

    public static GatewayPublicSnapshotResponseBody operationsSnapshot(Duration responseTimeout) {
        return snapshot(List.of(
            route("operations-auth-login", "POST", GatewayPaths.OPERATIONS_AUTH_PREFIX + "/login", "/auth/v1/login",
                "PUBLIC", "PUBLIC", "DROP", "NONE", List.of("content-type"), List.of("origin", "content-type"),
                List.of(), ingress(300, 300, 5, 5, 20, 2), responseTimeout),
            route("operations-auth-refresh", "POST", GatewayPaths.OPERATIONS_AUTH_PREFIX + "/refresh",
                "/auth/v1/refresh", "PUBLIC", "PUBLIC", "DROP", "NONE", List.of("content-type"),
                List.of("origin", "content-type"), List.of(), ingress(600, 600, 10, 30, 50, 3), responseTimeout),
            route("operations-auth-logout", "POST", GatewayPaths.OPERATIONS_AUTH_PREFIX + "/logout", "/auth/v1/logout",
                "OPERATIONS_JWT", "HUMAN_AUTHENTICATED", "DROP", "NONE", List.of("authorization", "content-type"),
                List.of("origin", "content-type"), List.of(),
                withAuthenticatedPrincipal(ingress(300, 300, 10, 30, 50, 3)), responseTimeout)));
    }

    public static GatewayPublicSnapshotResponseBody operationsSnapshotWithSessionQuery() {
        return operationsSnapshotWithSessionQuery(TEST_ROUTE_RESPONSE_TIMEOUT);
    }

    public static GatewayPublicSnapshotResponseBody operationsSnapshotWithSessionQuery(Duration responseTimeout) {
        List<GatewayRouteSnapshot> routes = new ArrayList<>(operationsSnapshot(responseTimeout).getRoutes());
        routes.add(route("operations-auth-session", "GET", GatewayPaths.OPERATIONS_AUTH_PREFIX + "/session",
            "/auth/v1/session", "PUBLIC", "PUBLIC", "DROP", "NONE", List.of(), List.of("origin"), List.of("detail"),
            List.of(rate("SHARED_ROUTE", 100, 100), concurrency("SHARED_ROUTE", 10)), responseTimeout));
        return snapshot(routes);
    }

    public static GatewayPublicSnapshotResponseBody operationsSnapshotWithVariableRoute() {
        return operationsSnapshotWithVariableRoute(TEST_ROUTE_RESPONSE_TIMEOUT);
    }

    public static GatewayPublicSnapshotResponseBody operationsSnapshotWithVariableRoute(Duration responseTimeout) {
        List<GatewayRouteSnapshot> routes = new ArrayList<>(operationsSnapshot(responseTimeout).getRoutes());
        routes.add(route("operations-client-detail", "GET", "/edge/clients/{clientId}", "/auth/clients/{clientId}",
            "PUBLIC", "PUBLIC", "DROP", "NONE", List.of(), List.of("origin"), List.of(),
            List.of(rate("SHARED_ROUTE", 100, 100), concurrency("SHARED_ROUTE", 10)), responseTimeout));
        return snapshot(routes);
    }

    public static GatewayPublicSnapshotResponseBody emptySnapshot() {
        return new GatewayPublicSnapshotResponseBody(1, List.of(), List.of());
    }

    public static GatewayPublicSnapshotResponseBody operationsSnapshotWithContextQuery() {
        List<GatewayRouteSnapshot> routes = new ArrayList<>(operationsSnapshot().getRoutes());
        routes.add(route("operations-auth-context", "GET", GatewayPaths.OPERATIONS_AUTH_PREFIX + "/context",
            "/auth/v1/context", "OPERATIONS_JWT", "HUMAN_AUTHENTICATED", "DROP", "NONE", List.of("authorization"),
            List.of("origin"), List.of(), withAuthenticatedPrincipal(ingress(300, 300, 10, 30, 50, 3)),
            TEST_ROUTE_RESPONSE_TIMEOUT));
        return snapshot(routes);
    }

    private static GatewayPublicSnapshotResponseBody snapshot(List<GatewayRouteSnapshot> routes) {
        return new GatewayPublicSnapshotResponseBody(1, List.of(new GatewayTargetSnapshot(TARGET, "OPERATIONS", TARGET)), routes);
    }

    private static GatewayRouteSnapshot route(String code, String method, String externalPath, String downstreamPath,
        String profile, String mode, String requestCookie, String responseCookie, List<String> corsHeaders,
        List<String> forwardedHeaders, List<String> queries, List<GatewayGuardRuleSnapshot> guards,
        Duration responseTimeout) {
        return new GatewayRouteSnapshot(code, "OPERATIONS", method, externalPath, downstreamPath, TARGET, profile, mode,
            null, null, requestCookie, responseCookie, corsHeaders, forwardedHeaders, queries,
            method.equals("GET") ? 0 : 16_384, 5_000, Math.toIntExact(responseTimeout.toMillis()), guards);
    }

    private static List<GatewayGuardRuleSnapshot> ingress(long routeCapacity, long routeRefill, long ipCapacity,
        long ipRefill, long routeConcurrency, long ipConcurrency) {
        return List.of(rate("SHARED_ROUTE", routeCapacity, routeRefill), rate("CLIENT_IP", ipCapacity, ipRefill),
            concurrency("SHARED_ROUTE", routeConcurrency), concurrency("CLIENT_IP", ipConcurrency));
    }

    private static List<GatewayGuardRuleSnapshot> withAuthenticatedPrincipal(List<GatewayGuardRuleSnapshot> ingress) {
        List<GatewayGuardRuleSnapshot> guards = new ArrayList<>(ingress);
        guards.add(rate("AUTHENTICATED_PRINCIPAL", 10, 10));
        guards.add(concurrency("AUTHENTICATED_PRINCIPAL", 2));
        return List.copyOf(guards);
    }

    private static GatewayGuardRuleSnapshot rate(String dimension, long capacity, long refill) {
        return new GatewayGuardRuleSnapshot("RATE_LIMIT", dimension, capacity, refill, 60_000L, null);
    }

    private static GatewayGuardRuleSnapshot concurrency(String dimension, long limit) {
        return new GatewayGuardRuleSnapshot("CONCURRENCY", dimension, null, null, null, limit);
    }
}
