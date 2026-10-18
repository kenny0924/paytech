package com.paytech.gateway.routing;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.gateway.guard.policy.ConcurrencyRule;
import com.paytech.gateway.guard.policy.GuardDimension;
import com.paytech.gateway.guard.policy.GuardProfile;
import com.paytech.gateway.guard.policy.RateLimitRule;
import com.paytech.gateway.web.GatewayPaths;

/** Operations 首期允许公开的固定路由目录。 */
public final class OperationsRouteCatalog {
    private static final int AUTH_BODY_LIMIT_BYTES = 16_384;
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Duration BODY_RECEIVE_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);
    private static final Duration STATE_TTL = Duration.ofSeconds(60);
    private final List<OperationsRouteDefinition> routes;

    public OperationsRouteCatalog(Duration responseTimeout) {
        this(List.of(
            route(OperationsRouteCode.AUTH_LOGIN, GatewayPaths.OPERATIONS_AUTH_PREFIX + "/login", "/auth/v1/login",
                OperationsAccessRequirement.ANONYMOUS, RefreshCookieRequirement.NOT_REQUIRED,
                RefreshCookieResponsePolicy.NONE, Set.of(HttpHeaders.CONTENT_TYPE),
                Set.of(HttpHeaders.ORIGIN, HttpHeaders.CONTENT_TYPE), profile(300, 300, 5, 5, 20, 2), null,
                responseTimeout),
            route(OperationsRouteCode.AUTH_REFRESH, GatewayPaths.OPERATIONS_AUTH_PREFIX + "/refresh",
                "/auth/v1/refresh", OperationsAccessRequirement.ANONYMOUS, RefreshCookieRequirement.NOT_REQUIRED,
                RefreshCookieResponsePolicy.NONE, Set.of(HttpHeaders.CONTENT_TYPE),
                Set.of(HttpHeaders.ORIGIN, HttpHeaders.CONTENT_TYPE), profile(600, 600, 10, 30, 50, 3), null,
                responseTimeout),
            route(OperationsRouteCode.AUTH_LOGOUT, GatewayPaths.OPERATIONS_AUTH_PREFIX + "/logout", "/auth/v1/logout",
                OperationsAccessRequirement.AUTHENTICATED, RefreshCookieRequirement.NOT_REQUIRED,
                RefreshCookieResponsePolicy.NONE, Set.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE),
                Set.of(HttpHeaders.ORIGIN, HttpHeaders.CONTENT_TYPE), profile(300, 300, 10, 30, 50, 3),
                subjectProfile(10, 10, 2), responseTimeout)));
    }

    OperationsRouteCatalog(List<OperationsRouteDefinition> definitions) {
        routes = List.copyOf(definitions);
        validateUniqueRoutes();
    }

    private static OperationsRouteDefinition route(OperationsRouteCode code, String externalPath, String bffPath,
        OperationsAccessRequirement accessRequirement, RefreshCookieRequirement refreshCookieRequirement,
        RefreshCookieResponsePolicy refreshCookieResponsePolicy, Set<String> corsRequestHeaders,
        Set<String> forwardedRequestHeaders, GuardProfile ingressGuard, GuardProfile authenticatedGuard,
        Duration responseTimeout) {
        return new OperationsRouteDefinition(code, HttpMethod.POST, externalPath, bffPath,
            OperationsQueryPolicy.REJECT_ALL, accessRequirement, refreshCookieRequirement, refreshCookieResponsePolicy,
            corsRequestHeaders, forwardedRequestHeaders, AUTH_BODY_LIMIT_BYTES, BODY_RECEIVE_TIMEOUT, ingressGuard,
            authenticatedGuard, responseTimeout);
    }

    private static GuardProfile profile(long routeCapacity, long routeRefill, long ipCapacity, long ipRefill,
        int routeConcurrency, int ipConcurrency) {
        return new GuardProfile(
            List.of(new RateLimitRule(GuardDimension.SHARED_ROUTE, routeCapacity, routeRefill, ONE_MINUTE),
                new RateLimitRule(GuardDimension.CLIENT_IP, ipCapacity, ipRefill, ONE_MINUTE)),
            List.of(new ConcurrencyRule(GuardDimension.SHARED_ROUTE, routeConcurrency),
                new ConcurrencyRule(GuardDimension.CLIENT_IP, ipConcurrency)),
            LEASE_DURATION, STATE_TTL);
    }

    private static GuardProfile subjectProfile(long capacity, long refillTokens, int concurrencyLimit) {
        return new GuardProfile(
            List.of(new RateLimitRule(GuardDimension.AUTHENTICATED_SUBJECT, capacity, refillTokens, ONE_MINUTE)),
            List.of(new ConcurrencyRule(GuardDimension.AUTHENTICATED_SUBJECT, concurrencyLimit)), LEASE_DURATION,
            STATE_TTL);
    }

    public List<OperationsRouteDefinition> all() {
        return routes;
    }

    public Optional<OperationsRouteDefinition> find(HttpMethod method, String path) {
        return routes.stream().filter(route -> route.method() == method && route.externalPath().equals(path))
            .findFirst();
    }

    private void validateUniqueRoutes() {
        Set<String> codes = new HashSet<>();
        Set<String> externalEndpoints = new HashSet<>();
        Set<String> bffPaths = new HashSet<>();
        for (OperationsRouteDefinition route : routes) {
            if (!codes.add(route.code().getCode())) {
                throw new IllegalArgumentException("Operations 路由编码重复: " + route.code().getCode());
            }
            String externalEndpoint = route.method().name() + ' ' + route.externalPath();
            String bffEndpoint = route.method().name() + ' ' + route.bffPath();
            if (!externalEndpoints.add(externalEndpoint) || !bffPaths.add(bffEndpoint)) {
                throw new IllegalArgumentException("Operations 路由 Method/Path 重复: " + externalEndpoint);
            }
        }
    }
}
