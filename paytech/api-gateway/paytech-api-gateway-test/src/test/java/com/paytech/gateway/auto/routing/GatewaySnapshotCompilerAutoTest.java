package com.paytech.gateway.auto.routing;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.gateway.routing.snapshot.GatewaySnapshotCompiler;
import com.paytech.gateway.routing.snapshot.InvalidGatewaySnapshotException;
import com.paytech.gateway.test.OperationsTestPaths;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayGuardRuleSnapshot;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayPublicSnapshotResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayRouteSnapshot;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayTargetSnapshot;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

class GatewaySnapshotCompilerAutoTest {
    private final GatewaySnapshotCompiler compiler = new GatewaySnapshotCompiler();

    /** 数据库新增逻辑目标和服务发现名后，合法新路径不需要 Gateway 预先登记。 */
    @Test
    void test_databaseTargetAndNewCapabilityCompileWithoutBusinessRegistry() {
        GatewayRouteSnapshot configured = route("database-owner");
        GatewayPublicSnapshotResponseBody source = new GatewayPublicSnapshotResponseBody(1,
            List.of(new GatewayTargetSnapshot("database-owner", "OPERATIONS", "discovered-service")),
            List.of(new GatewayRouteSnapshot(configured.getRouteCode(), configured.getChannel(), configured.getHttpMethod(),
                configured.getExternalPathTemplate(), "/decision-dictionaries/v1/rejection-reasons", "database-owner",
                configured.getSecurityProfileCode(), configured.getAuthorizationMode(), null, null, "DROP", "NONE",
                configured.getCorsRequestHeaderNames(), configured.getForwardedRequestHeaderNames(), List.of(),
                configured.getBodyLimitBytes(), configured.getBodyReceiveTimeoutMillis(), configured.getResponseTimeoutMillis(),
                configured.getGuardRules())));

        var compiled = assertDoesNotThrow(() -> compiler.compile(source));

        assertEquals("database-owner", compiled.catalog().all().getFirst().targetServiceCode());
        assertEquals("/decision-dictionaries/v1/rejection-reasons",
            compiled.catalog().all().getFirst().downstreamPath());
    }

    /** 快照身份解析与转发使用同一不可变目标，不依赖逻辑 Code 等于服务名。 */
    @Test
    void test_targetIndexPinsDiscoveryIdentity() {
        var source = new GatewayPublicSnapshotResponseBody(1,
            List.of(new GatewayTargetSnapshot("database-owner", "OPERATIONS", "discovered-service")),
            List.of(route("database-owner")));
        var compiled = compiler.compile(source);
        var target = compiled.targets().get("database-owner");

        assertSame(target, compiled.catalog().all().getFirst().target());
        assertEquals(URI.create("lb://discovered-service"), target.routingUri());
        assertThrows(UnsupportedOperationException.class, () -> compiled.targets().clear());
    }

    /** 保留 test 已部署的 64 KiB 规则保存能力，超过原安全上限仍拒绝。 */
    @Test
    void test_preservesDeployedRuleBodyCeiling() {
        var source = route("operations-console-bff");
        for (long size : List.of(65_536L, 65_537L)) {
            var configured = new GatewayRouteSnapshot(source.getRouteCode(), source.getChannel(), source.getHttpMethod(),
                source.getExternalPathTemplate(), source.getDownstreamPathTemplate(), source.getTargetServiceCode(),
                source.getSecurityProfileCode(), source.getAuthorizationMode(), null, null, "DROP", "NONE",
                source.getCorsRequestHeaderNames(), source.getForwardedRequestHeaderNames(), List.of(), size,
                source.getBodyReceiveTimeoutMillis(), source.getResponseTimeoutMillis(), source.getGuardRules());
            if (size == 65_536L) {
                assertEquals(size, compiler.compile(snapshot(List.of(configured)))
                    .catalog().all().getFirst().bodyLimitBytes());
            } else {
                assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(snapshot(List.of(configured))));
            }
        }
    }

    @Test
    void compilesFullyEmptySnapshotAsAValidCatalog() {
        GatewayPublicSnapshotResponseBody snapshot = new GatewayPublicSnapshotResponseBody(1, List.of(), List.of());

        var compiled = compiler.compile(snapshot);

        assertEquals(0, compiled.targetCount());
        assertEquals(0, compiled.catalog().all().size());
    }

    @Test
    void rejectsRouteWhenTheSnapshotContainsNoTargets() {
        GatewayPublicSnapshotResponseBody snapshot =
            new GatewayPublicSnapshotResponseBody(1, List.of(), List.of(route("operations-console-bff")));

        assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(snapshot));
    }

    @Test
    void compilesCurrentLoginRefreshLogoutSnapshot() {
        var compiled = compiler.compile(GatewaySnapshotFixture.operationsSnapshot());

        assertEquals(3, compiled.catalog().all().size());
        assertEquals("OPERATIONS_JWT",
            compiled.catalog().find(GatewayRouteChannel.OPERATIONS, org.springframework.http.HttpMethod.POST,
                OperationsTestPaths.LOGOUT).orElseThrow()
                .securityProfileCode());
    }

    @Test
    void preservesTheSnapshotRouteTimeoutInsteadOfReplacingItWithAStaticDefault() {
        Duration configured = Duration.ofMillis(275);

        var compiled = compiler.compile(GatewaySnapshotFixture.operationsSnapshot(configured));

        assertTrue(compiled.catalog().all().stream().allMatch(route -> configured.equals(route.responseTimeout())));
    }

    /** URI、端口、大小写和非法标签不能伪装成服务发现名称。 */
    @ParameterizedTest
    @ValueSource(strings = {"", "http://owner", "lb://owner", "owner:80", "user@owner", "owner/path",
        "owner?x=y", "owner#fragment", "OWNER", "owner_name", "-owner", "owner-", "owner..test", "owner%2fpath"})
    void test_invalidDiscoveryIdentityRejectsWholeSnapshot(String discovery) {
        GatewayPublicSnapshotResponseBody snapshot = new GatewayPublicSnapshotResponseBody(1,
            List.of(new GatewayTargetSnapshot("database-owner", "OPERATIONS", discovery)),
            List.of(route("database-owner")));
        assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(snapshot));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/v1/context", "/authorization/v1/navigation", "/iam/v1/accounts", "/audit/v1/events",
        "/gateway-admin/v1/routes"})
    void test_approvedOperationsCapabilityPathIsAccepted(String downstreamPath) {
        var catalog = compiler
            .compile(snapshot(
                List.of(route("operations-capability", "GET", "/api/operations" + downstreamPath, downstreamPath))))
            .catalog();

        assertEquals(downstreamPath, catalog.all().getFirst().downstreamPath());
    }

    @ParameterizedTest
    @ValueSource(
        strings = {"/actuator/health", "/internal/admin", "/iam-extra/accounts", "/authorization-evil/navigation",
            "/gateway-admin-evil/v1/routes"})
    void test_explicitlyConfiguredDownstreamPathsDoNotRequireCodeRoots(String downstreamPath) {
        assertEquals(downstreamPath, compiler.compile(snapshot(List.of(
            route("operations-configured", "GET", "/api/operations/example", downstreamPath))))
            .catalog().all().getFirst().downstreamPath());
    }

    @ParameterizedTest
    @ValueSource(strings = {"X-Gateway-Realm", "x-gateway-context-type", "X-Gateway-Tenant-Id", "X-Gateway-Tenant-Type",
        "X-Gateway-Tenant-Member-Id"})
    void test_trustedContextCannotBeSuppliedBySnapshotHeaderAllowlists(String header) {
        GatewayRouteSnapshot ordinary =
            route("operations-capability", "GET", "/api/operations/iam/accounts", "/iam/v1/accounts");

        assertThrows(InvalidGatewaySnapshotException.class,
            () -> compiler.compile(snapshot(List.of(withHeaders(ordinary, List.of(header), List.of())))));
        assertThrows(InvalidGatewaySnapshotException.class,
            () -> compiler.compile(snapshot(List.of(withHeaders(ordinary, List.of(), List.of(header))))));
    }

    @Test
    void compilesEquivalentSingleSegmentVariablesAndAllowsFixedPriorityRoute() {
        GatewayRouteSnapshot variable =
            route("operations-client-detail", "GET", "/api/operations/clients/{clientId}", "/auth/clients/{clientId}");
        GatewayRouteSnapshot fixed =
            route("operations-client-current", "GET", "/api/operations/clients/current", "/auth/clients/current");

        var catalog = compiler.compile(snapshot(List.of(variable, fixed))).catalog();

        assertEquals("operations-client-current",
            catalog.find(GatewayRouteChannel.OPERATIONS, org.springframework.http.HttpMethod.GET,
                "/api/operations/clients/current").orElseThrow()
                .routeCode());
        assertEquals("operations-client-detail",
            catalog.find(GatewayRouteChannel.OPERATIONS, org.springframework.http.HttpMethod.GET,
                "/api/operations/clients/client-1001").orElseThrow()
                .routeCode());
    }

    @Test
    void rejectsMismatchedOrAmbiguousPathVariables() {
        GatewayRouteSnapshot mismatched =
            route("operations-client-detail", "GET", "/api/operations/clients/{clientId}", "/auth/clients/{id}");
        GatewayRouteSnapshot first = route("operations-client-detail", "GET",
            "/api/operations/clients/{clientId}/profile", "/auth/clients/{clientId}/profile");
        GatewayRouteSnapshot second = route("operations-profile-detail", "GET",
            "/api/operations/clients/current/{profileId}", "/auth/clients/current/{profileId}");

        assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(snapshot(List.of(mismatched))));
        assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(snapshot(List.of(first, second))));
    }

    @Test
    void test_differentVariableCountsRejectIntersectingTemplatesInEitherOrder() {
        GatewayRouteSnapshot history = route("items-history", "GET", "/items/{id}/history", "/auth/items/{id}/history");
        GatewayRouteSnapshot action = route("items-action", "GET", "/items/{id}/{action}", "/auth/items/{id}/{action}");

        assertAll(
            () -> assertThrows(InvalidGatewaySnapshotException.class,
                () -> compiler.compile(snapshot(List.of(history, action)))),
            () -> assertThrows(InvalidGatewaySnapshotException.class,
                () -> compiler.compile(snapshot(List.of(action, history)))));
    }

    @ParameterizedTest
    @ValueSource(ints = {64, 65, 4_096})
    void test_disjointMixedTemplatesRetainFixedPriorityAndExactMethodAtCatalogBoundaries(int size) {
        List<GatewayRouteSnapshot> routes = new ArrayList<>();
        for (int index = 0; index < size - 3; index++) {
            String path = "/group-" + index + (index % 2 == 0 ? "/{id}/history" : "/{id}/{action}");
            routes.add(route("group-" + index, "GET", path, "/auth" + path));
        }
        routes.add(route("fixed-priority", "GET", "/group-0/current/history", "/auth/group-0/current/history"));
        routes.add(route("different-method", "POST", "/group-0/{id}/{action}", "/auth/group-0/{id}/{action}"));
        routes.add(route("different-length", "GET", "/group-0/{id}", "/auth/group-0/{id}"));

        var catalog = compiler.compile(snapshot(routes)).catalog();

        assertEquals(size, catalog.all().size());
        assertEquals("fixed-priority", catalog.find(GatewayRouteChannel.OPERATIONS, org.springframework.http.HttpMethod.GET,
            "/group-0/current/history").orElseThrow().routeCode());
        assertEquals("group-0", catalog.find(GatewayRouteChannel.OPERATIONS, org.springframework.http.HttpMethod.GET,
            "/group-0/other/history").orElseThrow().routeCode());
        assertEquals("different-method", catalog.find(GatewayRouteChannel.OPERATIONS, org.springframework.http.HttpMethod.POST,
            "/group-0/current/history").orElseThrow().routeCode());

        routes.set(size - 3, route("intersecting-template", "GET", "/group-0/{id}/{action}",
            "/auth/group-0/{id}/{action}"));
        assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(snapshot(routes)));
    }

    @Test
    void rejectsDuplicateRouteCodesEvenWhenEndpointsDiffer() {
        GatewayRouteSnapshot first =
            route("operations-client-detail", "GET", "/api/operations/clients/one", "/auth/clients/one");
        GatewayRouteSnapshot second =
            route("operations-client-detail", "GET", "/api/operations/clients/two", "/auth/clients/two");

        assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(snapshot(List.of(first, second))));
    }

    @Test
    void allowsIdenticalAndOverlappingTemplatesInDifferentChannels() {
        GatewaySnapshotCompiler channelAwareCompiler = new GatewaySnapshotCompiler();
        GatewayRouteSnapshot operations = route("operations-shared", "GET", "/api/shared/{id}/history",
            "/auth/shared/{id}/history");
        GatewayRouteSnapshot clientApi = clientApiRoute("client-api-shared", "/api/shared/{id}/{action}",
            "/client/shared/{id}/{action}");
        GatewayRouteSnapshot operationsExact =
            route("operations-exact", "GET", "/api/exact", "/auth/exact");
        GatewayRouteSnapshot clientApiExact = clientApiRoute("client-api-exact", "/api/exact", "/client/exact");
        GatewayPublicSnapshotResponseBody snapshot = new GatewayPublicSnapshotResponseBody(1,
            List.of(new GatewayTargetSnapshot("operations-console-bff", "OPERATIONS", "operations-console-bff"),
                new GatewayTargetSnapshot("client-api-bff", "CLIENT_API", "client-api-bff")),
            List.of(operations, clientApi, operationsExact, clientApiExact));

        var catalog = channelAwareCompiler.compile(snapshot).catalog();

        assertEquals("operations-shared", catalog.find(GatewayRouteChannel.OPERATIONS,
            org.springframework.http.HttpMethod.GET, "/api/shared/example/history").orElseThrow().routeCode());
        assertEquals("client-api-shared", catalog.find(GatewayRouteChannel.CLIENT_API,
            org.springframework.http.HttpMethod.GET, "/api/shared/example/history").orElseThrow().routeCode());
        assertTrue(catalog.find(GatewayRouteChannel.CLIENT, org.springframework.http.HttpMethod.GET,
            "/api/shared/example/history").isEmpty());
        assertEquals("operations-exact", catalog.find(GatewayRouteChannel.OPERATIONS,
            org.springframework.http.HttpMethod.GET, "/api/exact").orElseThrow().routeCode());
        assertEquals("client-api-exact", catalog.find(GatewayRouteChannel.CLIENT_API,
            org.springframework.http.HttpMethod.GET, "/api/exact").orElseThrow().routeCode());
    }

    @Test
    void compilesClientApiCredentialAndTenantAsDistinctAuthenticatedGuardDimensions() {
        var compiled = new GatewaySnapshotCompiler()
            .compile(GatewaySnapshotFixture.clientApiSnapshot()).catalog().all().getFirst();

        assertEquals(List.of(com.paytech.gateway.guard.policy.GuardDimension.AUTHENTICATED_SUBJECT,
            com.paytech.gateway.guard.policy.GuardDimension.TENANT),
            compiled.authenticatedGuard().rateLimits().stream().map(rule -> rule.dimension()).toList());
        assertEquals(List.of(com.paytech.gateway.guard.policy.GuardDimension.AUTHENTICATED_SUBJECT,
            com.paytech.gateway.guard.policy.GuardDimension.TENANT),
            compiled.authenticatedGuard().concurrencyLimits().stream().map(rule -> rule.dimension()).toList());
    }

    @Test
    void rejectsClientApiGuardWhenAnyRequiredDimensionIsMissingOrUnpaired() {
        GatewayPublicSnapshotResponseBody source = GatewaySnapshotFixture.clientApiSnapshot();
        GatewayRouteSnapshot route = source.getRoutes().getFirst();
        GatewaySnapshotCompiler clientCompiler = new GatewaySnapshotCompiler();

        for (String dimension : List.of("SHARED_ROUTE", "CLIENT_IP", "AUTHENTICATED_PRINCIPAL", "TENANT")) {
            for (String removedRuleType : List.of("ALL", "RATE_LIMIT", "CONCURRENCY")) {
                List<GatewayGuardRuleSnapshot> rules = route.getGuardRules().stream()
                    .filter(rule -> !rule.getDimension().equals(dimension)
                        || (!removedRuleType.equals("ALL") && !rule.getRuleType().equals(removedRuleType)))
                    .toList();
                GatewayPublicSnapshotResponseBody invalid = new GatewayPublicSnapshotResponseBody(source.getSchemaVersion(), source.getTargets(),
                    List.of(withGuards(route, rules)));
                assertThrows(InvalidGatewaySnapshotException.class, () -> clientCompiler.compile(invalid),
                    dimension + " without " + removedRuleType);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPTIONS", "TRACE", "CONNECT", "get"})
    void rejectsMethodsThatCannotBeForwarded(String method) {
        assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(snapshot(
            List.of(route("operations-client-detail", method, "/api/operations/clients/one", "/auth/clients/one")))));
    }

    @Test
    void rejectsCatalogAndChildCollectionsBeyondStaticCardinalityLimits() {
        List<GatewayRouteSnapshot> routes = new ArrayList<>();
        for (int index = 0; index < 4_097; index++) {
            routes.add(
                route("operations-route-" + index, "GET", "/api/operations/routes/" + index, "/auth/routes/" + index));
        }
        GatewayRouteSnapshot headerOverflow =
            withHeaders(route("operations-header-overflow", "GET", "/api/operations/headers", "/auth/headers"),
                names("x-cors-", 65), List.of());
        GatewayRouteSnapshot queryOverflow = withQueries(
            route("operations-query-overflow", "GET", "/api/operations/queries", "/auth/queries"), names("query", 33));

        assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(snapshot(routes)));
        assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(snapshot(List.of(headerOverflow))));
        assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(snapshot(List.of(queryOverflow))));
    }

    @Test
    void rejectsOverlongCodesAndUnsafePathSyntax() {
        GatewayRouteSnapshot longCode =
            route("r".repeat(129), "GET", "/api/operations/clients/one", "/auth/clients/one");
        GatewayRouteSnapshot wildcard =
            route("operations-wildcard", "GET", "/api/operations/clients/*", "/auth/clients/*");
        GatewayRouteSnapshot encodedSlash =
            route("operations-encoded-slash", "GET", "/api/operations/clients/%2F", "/auth/clients/%2F");
        GatewayRouteSnapshot regexAnchor =
            route("operations-regex-anchor", "GET", "/api/operations/clients/$", "/auth/clients/$");

        assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(snapshot(List.of(longCode))));
        assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(snapshot(List.of(wildcard))));
        assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(snapshot(List.of(encodedSlash))));
        assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(snapshot(List.of(regexAnchor))));
    }

    private GatewayRouteSnapshot route(String target) {
        return new GatewayRouteSnapshot("operations-auth-login", "OPERATIONS", "POST", OperationsTestPaths.LOGIN,
            "/auth/v1/login", target, "PUBLIC", "PUBLIC", null, null, "DROP", "NONE", List.of("Content-Type"),
            List.of("Content-Type"), List.of(), 16_384, 5_000, 10_000,
            List.of(new GatewayGuardRuleSnapshot("RATE_LIMIT", "SHARED_ROUTE", 300L, 300L, 60_000L, null),
                new GatewayGuardRuleSnapshot("CONCURRENCY", "SHARED_ROUTE", null, null, null, 20L)));
    }

    private GatewayPublicSnapshotResponseBody snapshot(List<GatewayRouteSnapshot> routes) {
        return new GatewayPublicSnapshotResponseBody(1,
            List.of(new GatewayTargetSnapshot("operations-console-bff", "OPERATIONS", "operations-console-bff")),
            routes);
    }

    private GatewayRouteSnapshot route(String code, String method, String externalPath, String downstreamPath) {
        return new GatewayRouteSnapshot(code, "OPERATIONS", method, externalPath, downstreamPath,
            "operations-console-bff", "PUBLIC", "PUBLIC", null, null, "DROP", "NONE", List.of(), List.of(), List.of(),
            0, 5_000, 10_000,
            List.of(new GatewayGuardRuleSnapshot("RATE_LIMIT", "SHARED_ROUTE", 300L, 300L, 60_000L, null),
                new GatewayGuardRuleSnapshot("CONCURRENCY", "SHARED_ROUTE", null, null, null, 20L)));
    }

    private GatewayRouteSnapshot clientApiRoute(String code, String externalPath, String downstreamPath) {
        return new GatewayRouteSnapshot(code, "CLIENT_API", "GET", externalPath, downstreamPath, "client-api-bff",
            "CLIENT_API_CREDENTIAL", "API_SCOPE", null, "orders.read", "DROP", "NONE", List.of(), List.of(),
            List.of(), 0, 5_000, 10_000,
            clientApiGuards());
    }

    private List<GatewayGuardRuleSnapshot> clientApiGuards() {
        return List.of(new GatewayGuardRuleSnapshot("RATE_LIMIT", "SHARED_ROUTE", 300L, 300L, 60_000L, null),
            new GatewayGuardRuleSnapshot("CONCURRENCY", "SHARED_ROUTE", null, null, null, 20L),
            new GatewayGuardRuleSnapshot("RATE_LIMIT", "CLIENT_IP", 10L, 10L, 60_000L, null),
            new GatewayGuardRuleSnapshot("CONCURRENCY", "CLIENT_IP", null, null, null, 3L),
            new GatewayGuardRuleSnapshot("RATE_LIMIT", "AUTHENTICATED_PRINCIPAL", 10L, 10L, 60_000L, null),
            new GatewayGuardRuleSnapshot("CONCURRENCY", "AUTHENTICATED_PRINCIPAL", null, null, null, 2L),
            new GatewayGuardRuleSnapshot("RATE_LIMIT", "TENANT", 100L, 100L, 60_000L, null),
            new GatewayGuardRuleSnapshot("CONCURRENCY", "TENANT", null, null, null, 10L));
    }

    private GatewayRouteSnapshot withGuards(GatewayRouteSnapshot route, List<GatewayGuardRuleSnapshot> rules) {
        return new GatewayRouteSnapshot(route.getRouteCode(), route.getChannel(), route.getHttpMethod(),
            route.getExternalPathTemplate(), route.getDownstreamPathTemplate(), route.getTargetServiceCode(),
            route.getSecurityProfileCode(), route.getAuthorizationMode(), route.getPermissionCode(), route.getRequiredScopeCode(),
            route.getRequestCookieMode(), route.getResponseCookieMode(), route.getCorsRequestHeaderNames(),
            route.getForwardedRequestHeaderNames(), route.getQueryParameterNames(), route.getBodyLimitBytes(),
            route.getBodyReceiveTimeoutMillis(), route.getResponseTimeoutMillis(), rules);
    }

    private GatewayRouteSnapshot withHeaders(GatewayRouteSnapshot route, List<String> cors, List<String> forwarded) {
        return new GatewayRouteSnapshot(route.getRouteCode(), route.getChannel(), route.getHttpMethod(),
            route.getExternalPathTemplate(), route.getDownstreamPathTemplate(), route.getTargetServiceCode(),
            route.getSecurityProfileCode(), route.getAuthorizationMode(), route.getPermissionCode(), route.getRequiredScopeCode(),
            route.getRequestCookieMode(), route.getResponseCookieMode(), cors, forwarded, route.getQueryParameterNames(),
            route.getBodyLimitBytes(), route.getBodyReceiveTimeoutMillis(), route.getResponseTimeoutMillis(),
            route.getGuardRules());
    }

    private GatewayRouteSnapshot withQueries(GatewayRouteSnapshot route, List<String> queries) {
        return new GatewayRouteSnapshot(route.getRouteCode(), route.getChannel(), route.getHttpMethod(),
            route.getExternalPathTemplate(), route.getDownstreamPathTemplate(), route.getTargetServiceCode(),
            route.getSecurityProfileCode(), route.getAuthorizationMode(), route.getPermissionCode(), route.getRequiredScopeCode(),
            route.getRequestCookieMode(), route.getResponseCookieMode(), route.getCorsRequestHeaderNames(),
            route.getForwardedRequestHeaderNames(), queries, route.getBodyLimitBytes(), route.getBodyReceiveTimeoutMillis(),
            route.getResponseTimeoutMillis(), route.getGuardRules());
    }

    private List<String> names(String prefix, int count) {
        List<String> names = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            names.add(prefix + index);
        }
        return List.copyOf(names);
    }
}
