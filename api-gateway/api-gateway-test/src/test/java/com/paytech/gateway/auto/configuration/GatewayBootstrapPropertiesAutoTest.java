package com.paytech.gateway.auto.configuration;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;

import com.paytech.gateway.guard.OperationsGuardTimingInvariant;
import com.paytech.gateway.guard.policy.GuardProfile;
import com.paytech.gateway.routing.OperationsRouteCatalog;
import com.paytech.gateway.routing.OperationsRouteCode;
import com.paytech.gateway.routing.OperationsRouteDefinition;
import com.paytech.gateway.test.GatewayAutoTestBase;

class GatewayBootstrapPropertiesAutoTest extends GatewayAutoTestBase {
    @Autowired
    ApplicationContext applicationContext;
    @Autowired
    OperationsGuardTimingInvariant guardTimingInvariant;
    @Autowired
    OperationsRouteCatalog routeCatalog;

    @Test
    void test_reactiveApplicationStartsWithoutServletStack() throws ClassNotFoundException {
        Assertions.assertEquals(WebApplicationType.REACTIVE, WebApplicationType.deduce());
        Assertions.assertThrows(ClassNotFoundException.class,
            () -> Class.forName("org.springframework.web.servlet.DispatcherServlet"));
        Assertions.assertNotNull(applicationContext.getBean(ReactiveRedisConnectionFactory.class));
    }

    @Test
    void test_productionConfigurationRequiresExplicitGuardRedisNamespace() throws IOException {
        List<PropertySource<?>> propertySources =
            new YamlPropertySourceLoader().load("gateway-production", new ClassPathResource("application.yml"));

        List<Object> configuredValues =
            propertySources.stream().map(source -> source.getProperty("gateway.operations.guard.redis.namespace"))
                .filter(Objects::nonNull).toList();

        Assertions.assertEquals(List.of("${OPERATIONS_GUARD_REDIS_NAMESPACE}"), configuredValues);
    }

    @Test
    void test_productionConfigurationBoundsBothRedisTimeoutsAtTwoSeconds() throws IOException {
        List<PropertySource<?>> propertySources =
            new YamlPropertySourceLoader().load("gateway-production", new ClassPathResource("application.yml"));

        Assertions.assertAll(
            () -> Assertions.assertEquals(List.of(Duration.ofSeconds(2)),
                configuredDurations(propertySources, "spring.data.redis.connect-timeout")),
            () -> Assertions.assertEquals(List.of(Duration.ofSeconds(2)),
                configuredDurations(propertySources, "spring.data.redis.timeout")));
    }

    @Test
    void test_productionConfigurationBindsOnlyAccessLogHmac() throws IOException {
        List<PropertySource<?>> propertySources =
            new YamlPropertySourceLoader().load("gateway-production", new ClassPathResource("application.yml"));

        Assertions.assertAll(
            () -> Assertions.assertEquals(List.of(),
                configuredValues(propertySources, "gateway.operations.hmac.redis.current.version")),
            () -> Assertions.assertEquals(List.of(),
                configuredValues(propertySources, "gateway.operations.hmac.redis.current.encoded-material")),
            () -> Assertions.assertEquals(List.of(),
                configuredValues(propertySources, "gateway.operations.hmac.redis.previous.version")),
            () -> Assertions.assertEquals(List.of(),
                configuredValues(propertySources, "gateway.operations.hmac.redis.previous.encoded-material")),
            () -> Assertions.assertEquals(List.of("${OPERATIONS_ACCESS_LOG_HMAC_VERSION}"),
                configuredValues(propertySources, "gateway.operations.hmac.access-log.version")),
            () -> Assertions.assertEquals(List.of("${OPERATIONS_ACCESS_LOG_HMAC_MATERIAL}"),
                configuredValues(propertySources, "gateway.operations.hmac.access-log.encoded-material")));
    }

    @Test
    void test_productionTransportRequiresExplicitPrivateBindAndContainsNoApplicationTls() throws IOException {
        List<PropertySource<?>> propertySources =
            new YamlPropertySourceLoader().load("gateway-production", new ClassPathResource("application.yml"));

        Assertions.assertAll(
            () -> Assertions.assertEquals(List.of("${GATEWAY_BIND_ADDRESS}"),
                configuredValues(propertySources, "server.address")),
            () -> Assertions.assertEquals(List.of(), configuredValues(propertySources, "server.ssl.enabled")),
            () -> Assertions.assertEquals(List.of(), configuredValues(propertySources, "server.ssl.certificate")),
            () -> Assertions.assertEquals(List.of(),
                configuredValues(propertySources, "server.ssl.certificate-private-key")),
            () -> Assertions.assertEquals(List.of(), configuredValues(propertySources, "gateway.transport.mode")),
            () -> Assertions.assertEquals(List.of(),
                configuredValues(propertySources, "gateway.operations.security.trust-anchor")),
            () -> Assertions.assertEquals(List.of(Boolean.FALSE),
                configuredValues(propertySources, "server.http2.enabled")));
    }

    @Test
    void test_guardTimingInvariantFreezesTheApprovedPhaseBudgets() {
        OperationsRouteDefinition login = route(OperationsRouteCode.AUTH_LOGIN);
        OperationsRouteDefinition logout = route(OperationsRouteCode.AUTH_LOGOUT);

        Assertions.assertAll(
            () -> Assertions.assertEquals(Duration.ofSeconds(17), guardTimingInvariant.postIngressAcquireBudget(login)),
            () -> Assertions.assertEquals(Duration.ofSeconds(26),
                guardTimingInvariant.postIngressAcquireBudget(logout)),
            () -> Assertions.assertEquals(Duration.ofSeconds(17),
                guardTimingInvariant.postAuthenticatedAcquireBudget(logout)));
    }

    @Test
    void test_guardTimingInvariantRejectsIngressLeaseEqualToLogoutWorstPathAndNonExactStateTtl() {
        OperationsRouteDefinition logout = route(OperationsRouteCode.AUTH_LOGOUT);
        GuardProfile exactBudgetLease =
            copyProfile(logout.ingressGuard(), Duration.ofSeconds(26), Duration.ofSeconds(60));
        GuardProfile invalidStateTtl =
            copyProfile(logout.ingressGuard(), Duration.ofSeconds(30), Duration.ofSeconds(61));

        Assertions.assertAll(
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> new OperationsGuardTimingInvariant(List.of(copyRoute(logout, exactBudgetLease)))),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> new OperationsGuardTimingInvariant(List.of(copyRoute(logout, invalidStateTtl)))));
    }

    private OperationsRouteDefinition route(OperationsRouteCode routeCode) {
        return routeCatalog.all().stream().filter(route -> route.code() == routeCode).findFirst().orElseThrow();
    }

    private GuardProfile copyProfile(GuardProfile source, Duration leaseDuration, Duration stateTtl) {
        return new GuardProfile(source.rateLimits(), source.concurrencyLimits(), leaseDuration, stateTtl);
    }

    private OperationsRouteDefinition copyRoute(OperationsRouteDefinition source, GuardProfile ingressGuard) {
        return new OperationsRouteDefinition(source.code(), source.method(), source.externalPath(), source.bffPath(),
            source.queryPolicy(), source.accessRequirement(), source.refreshCookieRequirement(),
            source.refreshCookieResponsePolicy(), source.corsRequestHeaders(), source.forwardedRequestHeaders(),
            source.bodyLimitBytes(), source.bodyReceiveTimeout(), ingressGuard, source.authenticatedGuard(),
            source.responseTimeout());
    }

    private List<Duration> configuredDurations(List<PropertySource<?>> propertySources, String propertyName) {
        return configuredValues(propertySources, propertyName).stream().map(Object::toString)
            .map(DurationStyle::detectAndParse).toList();
    }

    private List<Object> configuredValues(List<PropertySource<?>> propertySources, String propertyName) {
        return propertySources.stream().map(source -> source.getProperty(propertyName)).filter(Objects::nonNull)
            .toList();
    }
}
