package com.paytech.gateway.auto.routing;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;

import com.paytech.gateway.guard.policy.ConcurrencyRule;
import com.paytech.gateway.guard.policy.GuardDimension;
import com.paytech.gateway.guard.policy.GuardProfile;
import com.paytech.gateway.guard.policy.RateLimitRule;
import com.paytech.gateway.routing.*;
import com.paytech.gateway.test.OperationsTestPaths;

/** Operations 静态路由契约的输入校验自动化测试。 */
class OperationsRouteDefinitionContractAutoTest {
    private static OperationsRouteDefinition route(OperationsRouteCode code, String externalPath, String bffPath,
        OperationsAccessRequirement accessRequirement, GuardProfile authenticatedGuard) {
        return new OperationsRouteDefinition(code, HttpMethod.POST, externalPath, bffPath,
            OperationsQueryPolicy.REJECT_ALL, accessRequirement, RefreshCookieRequirement.NOT_REQUIRED,
            RefreshCookieResponsePolicy.ISSUE_ONLY, Set.of("Content-Type"), Set.of("Origin"), 1, Duration.ofSeconds(1),
            guardProfile(), authenticatedGuard, Duration.ofSeconds(1));
    }

    private static OperationsRouteDefinition routeWithBodyAndResponseTimeout(int bodyLimit, Duration responseTimeout) {
        return new OperationsRouteDefinition(OperationsRouteCode.AUTH_LOGIN, HttpMethod.POST, OperationsTestPaths.LOGIN,
            "/auth/v1/login", OperationsQueryPolicy.REJECT_ALL, OperationsAccessRequirement.ANONYMOUS,
            RefreshCookieRequirement.NOT_REQUIRED, RefreshCookieResponsePolicy.ISSUE_ONLY, Set.of("Content-Type"),
            Set.of("Origin"), bodyLimit, Duration.ofSeconds(1), guardProfile(), null, responseTimeout);
    }

    private static GuardProfile guardProfile() {
        return new GuardProfile(List.of(new RateLimitRule(GuardDimension.SHARED_ROUTE, 1, 1, Duration.ofSeconds(1))),
            List.of(new ConcurrencyRule(GuardDimension.SHARED_ROUTE, 1)), Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    private static Stream<Arguments> invalidPaths() {
        return Stream
            .of(null, "", "auth/v1/login", "/auth/v1/login?next=1", "/auth/v1/login#fragment", "//auth/v1/login")
            .map(Arguments::of);
    }

    private static Stream<Arguments> invalidHeaderSets() {
        Set<String> blank = new HashSet<>();
        blank.add(" ");
        Set<String> nullHeader = new HashSet<>();
        nullHeader.add(null);
        return Stream.of(Arguments.of((Set<String>)null, NullPointerException.class),
            Arguments.of(blank, IllegalArgumentException.class), Arguments.of(nullHeader, NullPointerException.class));
    }

    private static Stream<Arguments> invalidDurations() {
        return Stream.of(null, Duration.ZERO, Duration.ofSeconds(-1)).map(Arguments::of);
    }

    private static Stream<Arguments> invalidAuthenticatedGuardContracts() {
        return Stream.of(Arguments.of(OperationsAccessRequirement.AUTHENTICATED, null),
            Arguments.of(OperationsAccessRequirement.ANONYMOUS, guardProfile()));
    }

    @ParameterizedTest
    @MethodSource("invalidPaths")
    void test_routeRejectsMalformedExternalPath(String externalPath) {
        Assertions.assertThrows(IllegalArgumentException.class, () -> route(OperationsRouteCode.AUTH_LOGIN,
            externalPath, "/auth/v1/login", OperationsAccessRequirement.ANONYMOUS, null));
    }

    @ParameterizedTest
    @MethodSource("invalidHeaderSets")
    void test_routeRejectsMissingOrBlankHeaderContracts(Set<String> headers,
        Class<? extends RuntimeException> expectedException) {
        Assertions.assertThrows(expectedException,
            () -> new OperationsRouteDefinition(OperationsRouteCode.AUTH_LOGIN, HttpMethod.POST,
                OperationsTestPaths.LOGIN, "/auth/v1/login", OperationsQueryPolicy.REJECT_ALL,
                OperationsAccessRequirement.ANONYMOUS, RefreshCookieRequirement.NOT_REQUIRED,
                RefreshCookieResponsePolicy.ISSUE_ONLY, headers, Set.of("Origin"), 1, Duration.ofSeconds(1),
                guardProfile(), null, Duration.ofSeconds(1)));
    }

    @org.junit.jupiter.api.Test
    void test_routeReportsAccessTokenRequirementForItsAccessContract() {
        OperationsRouteDefinition anonymous = route(OperationsRouteCode.AUTH_LOGIN, OperationsTestPaths.LOGIN,
            "/auth/v1/login", OperationsAccessRequirement.ANONYMOUS, null);
        OperationsRouteDefinition authenticated = route(OperationsRouteCode.AUTH_LOGOUT, OperationsTestPaths.LOGOUT,
            "/auth/v1/logout", OperationsAccessRequirement.AUTHENTICATED, guardProfile());

        Assertions.assertAll(() -> Assertions.assertFalse(anonymous.accessTokenRequired()),
            () -> Assertions.assertTrue(authenticated.accessTokenRequired()));
    }

    @ParameterizedTest
    @MethodSource("invalidDurations")
    void test_routeRejectsNonPositiveReceiveTimeout(Duration receiveTimeout) {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new OperationsRouteDefinition(OperationsRouteCode.AUTH_LOGIN, HttpMethod.POST,
                OperationsTestPaths.LOGIN, "/auth/v1/login", OperationsQueryPolicy.REJECT_ALL,
                OperationsAccessRequirement.ANONYMOUS, RefreshCookieRequirement.NOT_REQUIRED,
                RefreshCookieResponsePolicy.ISSUE_ONLY, Set.of("Content-Type"), Set.of("Origin"), 1, receiveTimeout,
                guardProfile(), null, Duration.ofSeconds(1)));
    }

    @ParameterizedTest
    @MethodSource("invalidAuthenticatedGuardContracts")
    void test_routeRequiresSubjectGuardOnlyForAuthenticatedRoute(OperationsAccessRequirement accessRequirement,
        GuardProfile authenticatedGuard) {
        Assertions.assertThrows(IllegalArgumentException.class, () -> route(OperationsRouteCode.AUTH_LOGIN,
            OperationsTestPaths.LOGIN, "/auth/v1/login", accessRequirement, authenticatedGuard));
    }

    @org.junit.jupiter.api.Test
    void test_routeCopiesHeaderContractsAndRejectsNonPositiveBodyLimitAndResponseTimeout() {
        Set<String> corsHeaders = new HashSet<>(Set.of("Content-Type"));
        Set<String> forwardedHeaders = new HashSet<>(Set.of("Origin"));
        OperationsRouteDefinition route =
            new OperationsRouteDefinition(OperationsRouteCode.AUTH_LOGIN, HttpMethod.POST, OperationsTestPaths.LOGIN,
                "/auth/v1/login", OperationsQueryPolicy.REJECT_ALL, OperationsAccessRequirement.ANONYMOUS,
                RefreshCookieRequirement.NOT_REQUIRED, RefreshCookieResponsePolicy.ISSUE_ONLY, corsHeaders,
                forwardedHeaders, 1, Duration.ofSeconds(1), guardProfile(), null, Duration.ofSeconds(1));
        corsHeaders.add("X-CSRF-Token");
        forwardedHeaders.add("Content-Type");

        Assertions.assertAll(() -> Assertions.assertEquals(Set.of("Content-Type"), route.corsRequestHeaders()),
            () -> Assertions.assertEquals(Set.of("Origin"), route.forwardedRequestHeaders()),
            () -> Assertions.assertThrows(UnsupportedOperationException.class,
                () -> route.corsRequestHeaders().add("X-CSRF-Token")),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> routeWithBodyAndResponseTimeout(0, Duration.ofSeconds(1))),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> routeWithBodyAndResponseTimeout(1, Duration.ZERO)),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> routeWithBodyAndResponseTimeout(1, Duration.ofSeconds(-1))));
    }

    @org.junit.jupiter.api.Test
    void test_routeRejectsMissingRefreshCookieResponsePolicy() {
        Assertions.assertThrows(NullPointerException.class,
            () -> new OperationsRouteDefinition(OperationsRouteCode.AUTH_LOGIN, HttpMethod.POST,
                OperationsTestPaths.LOGIN, "/auth/v1/login", OperationsQueryPolicy.REJECT_ALL,
                OperationsAccessRequirement.ANONYMOUS, RefreshCookieRequirement.NOT_REQUIRED, null,
                Set.of("Content-Type"), Set.of("Origin"), 1, Duration.ofSeconds(1), guardProfile(), null,
                Duration.ofSeconds(1)));
    }

    @org.junit.jupiter.api.Test
    void test_catalogOwnsEachRouteRefreshCookieResponsePolicy() {
        Map<OperationsRouteCode, RefreshCookieResponsePolicy> policies =
            new OperationsRouteCatalog(Duration.ofSeconds(1)).all().stream().collect(java.util.stream.Collectors
                .toMap(OperationsRouteDefinition::code, OperationsRouteDefinition::refreshCookieResponsePolicy));

        Assertions.assertEquals(
            Map.of(OperationsRouteCode.AUTH_LOGIN, RefreshCookieResponsePolicy.NONE, OperationsRouteCode.AUTH_REFRESH,
                RefreshCookieResponsePolicy.NONE, OperationsRouteCode.AUTH_LOGOUT, RefreshCookieResponsePolicy.NONE),
            policies);
    }

}
