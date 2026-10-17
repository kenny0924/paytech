package com.paytech.gateway.auto.architecture;

import java.io.IOException;
import java.lang.annotation.*;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.route.InMemoryRouteDefinitionRepository;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.gateway.ApiGatewayApplication;
import com.paytech.gateway.guard.GuardDependencyUnavailableException;
import com.paytech.gateway.guard.concurrency.*;
import com.paytech.gateway.guard.policy.*;
import com.paytech.gateway.guard.ratelimit.RateLimitCommand;
import com.paytech.gateway.guard.ratelimit.RateLimitCommandFactory;
import com.paytech.gateway.guard.ratelimit.RateLimitDecision;
import com.paytech.gateway.guard.redis.ReactiveRedisScriptExecutor;
import com.paytech.gateway.routing.OperationsRouteCatalog;
import com.paytech.gateway.routing.OperationsRouteCode;
import com.paytech.gateway.routing.OperationsRoutingProperties;
import com.paytech.gateway.security.GatewayHmacProperties;
import com.paytech.gateway.security.OperationsSecurityProperties;
import com.paytech.gateway.security.VersionedHmacDigester;
import com.paytech.gateway.security.VersionedHmacDigester.VersionedDigest;
import com.paytech.gateway.web.AuthenticatedOperationsIdentityGlobalFilter;
import com.paytech.gateway.web.GatewayErrorWriter;
import com.paytech.gateway.web.OwnerProblemResponseGlobalFilter;
import com.paytech.gateway.web.OwnerProblemValidator;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;

import reactor.test.StepVerifier;

/** Gateway 生产包、运行路由与动态发布边界测试。 */
class GatewayArchitectureAutoTest extends GatewayAutoTestBase {
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    private static final String VALID_CONSOLE_ORIGIN = "https://operations-console.test";
    private static final Duration VALID_RESPONSE_TIMEOUT = Duration.ofSeconds(1);
    private static final List<String> EXPECTED_ROUTE_IDS =
        List.of("operations-auth-login", "operations-auth-refresh", "operations-auth-logout");

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private OperationsRouteCatalog routeCatalog;

    @Autowired
    private ConcurrencyCommandFactory concurrencyCommandFactory;

    @Autowired
    private RateLimitCommandFactory rateLimitCommandFactory;

    @Autowired
    private ReactiveRedisScriptExecutor scriptExecutor;

    @Test
    void test_productionCodeSourceContainsOneReactiveApplicationAndNoMvcOrDataLayers() {
        List<Class<?>> productionClasses = productionClasses();

        Assertions.assertAll(() -> Assertions.assertEquals(WebApplicationType.REACTIVE, WebApplicationType.deduce()),
            () -> Assertions.assertEquals(List.of(ApiGatewayApplication.class),
                productionClasses.stream().filter(type -> type.isAnnotationPresent(SpringBootApplication.class))
                    .toList()),
            () -> Assertions.assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.springframework.web.servlet.DispatcherServlet")),
            () -> Assertions.assertDoesNotThrow(() -> Class.forName(ApiProblemProfile.class.getName())),
            () -> Assertions.assertTrue(productionClasses.contains(OwnerProblemResponseGlobalFilter.class)),
            () -> Assertions.assertTrue(productionClasses.contains(OwnerProblemValidator.class)),
            () -> Assertions.assertTrue(productionClasses.stream().noneMatch(this::isMvcController),
                () -> "Gateway 生产包不得包含 MVC Controller: "
                    + productionClasses.stream().filter(this::isMvcController).map(Class::getName).toList()),
            () -> Assertions.assertTrue(productionClasses.stream().noneMatch(this::isDataLayerType),
                () -> "Gateway 生产包不得包含 Repository/Mapper/database 层: "
                    + productionClasses.stream().filter(this::isDataLayerType).map(Class::getName).toList()));
    }

    @Test
    void test_ownerProblemConsumerHasOneFilterValidatorAndExistingLocalWriter() {
        Assertions.assertAll(
            () -> Assertions.assertEquals(1,
                applicationContext.getBeansOfType(OwnerProblemResponseGlobalFilter.class).size()),
            () -> Assertions.assertEquals(1, applicationContext.getBeansOfType(OwnerProblemValidator.class).size()),
            () -> Assertions.assertEquals(1, applicationContext.getBeansOfType(GatewayErrorWriter.class).size()),
            () -> Assertions.assertTrue(applicationContext
                .getBeansOfType(org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory.class)
                .isEmpty()));
    }

    @Test
    void test_dataLayerStereotypesAreDetectedDirectlyAndThroughMetaAnnotations() {
        Assertions.assertAll(() -> Assertions.assertTrue(isDataLayerType(DirectDataStore.class)),
            () -> Assertions.assertTrue(isDataLayerType(MetaDataStore.class)),
            () -> Assertions.assertFalse(isDataLayerType(PlainStore.class)));
    }

    @Test
    void test_dynamicRouteWriterOwnershipUsesBeanDefinitionProvenance() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("gatewayConfiguration", new RootBeanDefinition(ApiGatewayApplication.class));
        beanFactory.registerBeanDefinition("frameworkConfiguration",
            new RootBeanDefinition(GatewayAutoConfiguration.class));

        RootBeanDefinition applicationFactoryWriter = new RootBeanDefinition(InMemoryRouteDefinitionRepository.class);
        applicationFactoryWriter.setFactoryBeanName("gatewayConfiguration");
        applicationFactoryWriter.setFactoryMethodName("applicationOwnedRouteDefinitionWriter");
        beanFactory.registerBeanDefinition("applicationFactoryWriter", applicationFactoryWriter);

        RootBeanDefinition applicationResourceWriter = new RootBeanDefinition(InMemoryRouteDefinitionRepository.class);
        applicationResourceWriter
            .setResourceDescription("class path resource [com/paytech/gateway/ApiGatewayApplication.class]");
        beanFactory.registerBeanDefinition("applicationResourceWriter", applicationResourceWriter);
        RootBeanDefinition frameworkWriter = new RootBeanDefinition(InMemoryRouteDefinitionRepository.class);
        frameworkWriter.setFactoryBeanName("frameworkConfiguration");
        frameworkWriter.setFactoryMethodName("inMemoryRouteDefinitionRepository");
        beanFactory.registerBeanDefinition("frameworkWriter", frameworkWriter);
        beanFactory.registerBeanDefinition("unprovenWriter",
            new RootBeanDefinition(InMemoryRouteDefinitionRepository.class));

        Assertions.assertAll(
            () -> Assertions.assertEquals(BeanOwnership.APPLICATION,
                beanOwnership("applicationFactoryWriter", beanFactory, new HashSet<>())),
            () -> Assertions.assertEquals(BeanOwnership.APPLICATION,
                beanOwnership("applicationResourceWriter", beanFactory, new HashSet<>())),
            () -> Assertions.assertEquals(BeanOwnership.FRAMEWORK,
                beanOwnership("frameworkWriter", beanFactory, new HashSet<>())),
            () -> Assertions.assertEquals(BeanOwnership.UNKNOWN,
                beanOwnership("unprovenWriter", beanFactory, new HashSet<>())));
    }

    @Test
    void test_runtimeUsesPinnedSnapshotWithoutStaticTransportCatalog() {
        List<String> catalogRouteIds = routeCatalog.all().stream().map(route -> route.code().getCode()).toList();
        RouteLocator runningRouteLocator =
            applicationContext.getBean("cachedCompositeRouteLocator", RouteLocator.class);
        List<String> runningRouteIds =
            runningRouteLocator.getRoutes().map(Route::getId).collectList().block(Duration.ofSeconds(5));

        Assertions.assertIterableEquals(EXPECTED_ROUTE_IDS, catalogRouteIds);
        Assertions.assertNotNull(runningRouteIds);
        Assertions.assertTrue(runningRouteIds.isEmpty(), "业务路由不能复制到独立 RouteLocator 缓存");
        Assertions.assertEquals(1, applicationContext.getBeansOfType(
            com.paytech.gateway.routing.GatewayRequestRouteHandlerMapping.class).size());
        Assertions.assertEquals(1, applicationContext.getBeansOfType(
            org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping.class).size());
        Assertions.assertTrue(applicationContext.getBeansOfType(
            org.springframework.cloud.gateway.discovery.DiscoveryClientRouteDefinitionLocator.class).isEmpty());
        Assertions.assertTrue(applicationContext.getBeansOfType(
            org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory.class).isEmpty());
    }

    @Test
    void test_applicationOwnsNoDynamicRouteWriterOrEventPublisher() {
        List<Class<?>> productionClasses = productionClasses();
        ConfigurableListableBeanFactory beanFactory = configurableBeanFactory();
        List<String> applicationWriters = Arrays
            .stream(beanFactory.getBeanNamesForType(RouteDefinitionWriter.class, true, false))
            .filter(beanName -> beanOwnership(beanName, beanFactory, new HashSet<>()) != BeanOwnership.FRAMEWORK)
            .map(beanName -> beanName + " ("
                + Optional.ofNullable(beanFactory.getType(beanName, false)).map(Class::getName).orElse("unknown") + ")")
            .toList();

        Assertions.assertAll(
            () -> Assertions.assertTrue(applicationWriters.isEmpty(),
                () -> "Gateway 应用不得拥有动态 RouteDefinitionWriter: " + applicationWriters),
            () -> Assertions.assertTrue(
                productionClasses.stream().noneMatch(RouteDefinitionWriter.class::isAssignableFrom),
                () -> "Gateway 生产类型不得实现 RouteDefinitionWriter: " + productionClasses.stream()
                    .filter(RouteDefinitionWriter.class::isAssignableFrom).map(Class::getName).toList()),
            () -> Assertions.assertTrue(productionClasses.stream().noneMatch(this::publishesApplicationEvents),
                () -> "Gateway 生产类不得注入动态路由事件发布入口: " + productionClasses.stream()
                    .filter(this::publishesApplicationEvents).map(Class::getName).toList()));
    }

    @Test
    void test_deploymentPropertiesFailClosedAcrossEveryReachableBoundary() {
        assertRoutingPropertyBoundaries();
        assertSecurityPropertyBoundaries();
    }

    @Test
    void test_guardValueObjectsRejectUnsafeAndAmbiguousInputs() {
        assertConcurrencyValueBoundaries();
        assertRateLimitValueBoundaries();
        assertHmacBoundaries();
        assertDecisionBoundaries();
    }

    @Test
    void test_guardCommandFactoriesRejectUntrustedOrAmbiguousRules() {
        GuardContext login = new GuardContext(OperationsRouteCode.AUTH_LOGIN, InetAddress.getLoopbackAddress(), null);
        assertConcurrencyFactoryBoundaries(login);
        assertRateLimitFactoryBoundaries(login);
    }

    @Test
    void test_authenticatedGuardIsGenericAndCannotReadJwtSecurityContext() {
        List<Class<?>> productionTypes = productionClasses();
        List<Class<?>> guardTypes = productionTypes.stream()
            .filter(type -> type.getName().startsWith("com.paytech.gateway.guard.")).toList();
        Set<String> jwtConsumers = productionTypes.stream().filter(this::referencesJwtAuthenticationToken)
            .map(Class::getName).collect(java.util.stream.Collectors.toSet());

        Assertions.assertAll(
            () -> Assertions.assertDoesNotThrow(() -> Class.forName("com.paytech.gateway.guard.GatewayGuardAdmission")),
            () -> Assertions.assertDoesNotThrow(
                () -> Class.forName("com.paytech.gateway.guard.GatewayIngressGuardWebFilter")),
            () -> Assertions.assertDoesNotThrow(
                () -> Class.forName("com.paytech.gateway.guard.GatewayAuthenticatedGuardGlobalFilter")),
            () -> Assertions.assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.paytech.gateway.guard.OperationsGuardAdmission")),
            () -> Assertions.assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.paytech.gateway.guard.OperationsIngressGuardWebFilter")),
            () -> Assertions.assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.paytech.gateway.guard.OperationsGuardGlobalFilter")),
            () -> Assertions.assertTrue(guardTypes.stream().noneMatch(this::referencesJwtAuthenticationToken),
                () -> "Guard 生产类型不得依赖 JwtAuthenticationToken: "
                    + guardTypes.stream().filter(this::referencesJwtAuthenticationToken).map(Class::getName).toList()),
            () -> Assertions.assertFalse(
                referencesJwtAuthenticationToken(AuthenticatedOperationsIdentityGlobalFilter.class)),
            () -> Assertions.assertEquals(Set.of(
                "com.paytech.gateway.security.OperationsChannelAuthorizationManager",
                "com.paytech.gateway.security.ClientChannelAuthorizationManager",
                "com.paytech.gateway.security.identity.GatewayAuthenticatedIdentityGlobalFilter"), jwtConsumers),
            () -> Assertions.assertTrue(Arrays
                .stream(com.paytech.gateway.security.identity.OperationsIdentity.class.getRecordComponents())
                .allMatch(component -> component.getType() == String.class)));
    }

    @Test
    void test_reactiveScriptExecutorRejectsInvalidInvocationAndReplyShapes() {
        Assertions.assertThrows(IllegalStateException.class,
            () -> scriptExecutor.loadUtf8Script("missing/coverage-script.lua"));
        ReactiveRedisScriptExecutor.LoadedScript script =
            scriptExecutor.loadUtf8Script("com/paytech/gateway/guard/concurrency/concurrency-release.lua");
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> scriptExecutor.execute(script, List.of(), List.of(UUID.randomUUID().toString()), 1));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> scriptExecutor.execute(script,
                List.of(GatewayAutoTestRuntime.redisNamespace().guardPrefix() + "coverage"),
                List.of(UUID.randomUUID().toString()), 0));

        StepVerifier
            .create(scriptExecutor.execute(script,
                List.of(GatewayAutoTestRuntime.redisNamespace().guardPrefix() + "coverage"),
                List.of(UUID.randomUUID().toString()), 2))
            .expectError(GuardDependencyUnavailableException.class).verify(Duration.ofSeconds(3));
    }

    private void assertRoutingPropertyBoundaries() {
        Assertions
            .assertDoesNotThrow(() -> new OperationsRoutingProperties(VALID_CONSOLE_ORIGIN, VALID_RESPONSE_TIMEOUT));

        for (String origin : List.of("https:opaque", "http://operations-console.test",
            "https://operations-console.test/path", "https://operations-console.test?query",
            "https://operations-console.test#fragment", "https://user@operations-console.test", "https://[invalid")) {
            assertInvalidRouting(origin, VALID_RESPONSE_TIMEOUT);
        }
        for (Duration timeout : Arrays.asList(null, Duration.ZERO, Duration.ofMillis(-1), Duration.ofSeconds(11))) {
            assertInvalidRouting(VALID_CONSOLE_ORIGIN, timeout);
        }
    }

    private void assertSecurityPropertyBoundaries() {
        assertInvalidSecurity(null, "audience");
        assertInvalidSecurity(" ", "audience");
        assertInvalidSecurity("issuer", null);
        assertInvalidSecurity("issuer", " ");
        Assertions.assertDoesNotThrow(() -> new OperationsSecurityProperties("issuer", "audience"));
    }

    private void assertConcurrencyValueBoundaries() {
        String key = "gateway:test:coverage:guard:v3:operations-auth-login:concurrency:shared-route:shared";
        String leaseKey = key + ":leases";
        long keyTtl = 2_000;
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ConcurrencyCommand(key, 1, 1_000, 3_000));
        for (String invalidKey : Arrays.asList(null, " ", "invalid key",
            "gateway:test:coverage:guard:v2:operations-auth-login:concurrency:shared-route:shared",
            "gateway:test:coverage:guard:v3:operations-auth-login:rate:shared-route:shared")) {
            Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ConcurrencyCommand(invalidKey, 1, 1_000, keyTtl));
        }
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ConcurrencyKeys(leaseKey, key + ":leases"));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ConcurrencyKeys(key + ":metadata", key + ":metadata"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ConcurrencyCommand(key, 0, 1_000, keyTtl));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ConcurrencyCommand(key, 1, 0, keyTtl));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ConcurrencyCommand(key, 1, MAX_SAFE_INTEGER / 2 + 1, keyTtl));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ConcurrencyCommand(key, 1, 1_000, 0));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ConcurrencyCommand(key, 1, 1_000, MAX_SAFE_INTEGER + 1));

        String leaseId = UUID.randomUUID().toString();
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ConcurrencyLease(leaseId, List.of()));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ConcurrencyLease(leaseId, List.of("invalid key")));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ConcurrencyLease(leaseId, List.of(key + ":metadata")));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ConcurrencyLease(leaseId,
            List.of("gateway:test:coverage:guard:v2:operations-auth-login:concurrency:shared-route:shared:leases")));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ConcurrencyLease(null, List.of(leaseKey)));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ConcurrencyLease("AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA", List.of(leaseKey)));
    }

    private void assertRateLimitValueBoundaries() {
        String key = "gateway:test:v1:coverage";
        long ttl = RateLimitCommand.deriveTtlMillis(1, 1, 1);
        for (String invalidKey : Arrays.asList(null, " ", "invalid\nkey", "x".repeat(1_025), "key\uD800")) {
            Assertions.assertThrows(IllegalArgumentException.class, () -> new RateLimitCommand(invalidKey, 1, 1, 1, 1));
        }
        Assertions.assertThrows(IllegalArgumentException.class, () -> RateLimitCommand.deriveTtlMillis(0, 1, 1));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> RateLimitCommand.deriveTtlMillis(MAX_SAFE_INTEGER + 1, 1, 1));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> RateLimitCommand.deriveTtlMillis(1, MAX_SAFE_INTEGER, 2));
        Assertions.assertEquals(60_000, RateLimitCommand.deriveTtlMillis(2, 3, 1));
    }

    private void assertHmacBoundaries() {
        GatewayHmacProperties.Secret access = secret("access-current", (byte)3);
        Assertions.assertThrows(IllegalArgumentException.class, () -> new GatewayHmacProperties(null));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new GatewayHmacProperties.Secret(null, encodedSecret((byte)4)));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new GatewayHmacProperties.Secret("invalid version", encodedSecret((byte)4)));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new GatewayHmacProperties.Secret("valid-version", null));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new GatewayHmacProperties.Secret("valid-version", " "));

        VersionedHmacDigester digester = new VersionedHmacDigester(new GatewayHmacProperties(access));
        Assertions.assertThrows(IllegalArgumentException.class, () -> digester.accessSubjectDigest(null));
        Assertions.assertThrows(IllegalArgumentException.class, () -> digester.accessSubjectDigest(""));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new VersionedDigest(null, "digest"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new VersionedDigest(" ", "digest"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new VersionedDigest("version", null));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new VersionedDigest("version", " "));
    }

    private void assertDecisionBoundaries() {
        String key = "gateway:test:coverage:guard:v3:operations-auth-login:concurrency:shared-route:shared:leases";
        ConcurrencyLease lease = new ConcurrencyLease(UUID.randomUUID().toString(), List.of(key));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ConcurrencyDecision(true, 0, Optional.empty(), Optional.empty()));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ConcurrencyDecision(false, 1, Optional.of(lease), Optional.of(GuardDimension.SHARED_ROUTE)));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ConcurrencyDecision(false, 1, Optional.empty(), Optional.empty()));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ConcurrencyDecision(true, 0, Optional.of(lease), Optional.of(GuardDimension.SHARED_ROUTE)));
        Assertions.assertThrows(IllegalArgumentException.class, () -> RateLimitDecision.allowed(1));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> RateLimitDecision.rejected(0, GuardDimension.SHARED_ROUTE));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new RateLimitDecision(false, 1, Optional.empty()));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new RateLimitDecision(true, 0, Optional.of(GuardDimension.SHARED_ROUTE)));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new GuardContext(OperationsRouteCode.AUTH_LOGIN, InetAddress.getLoopbackAddress(), " "));
    }

    private void assertConcurrencyFactoryBoundaries(GuardContext login) {
        Assertions.assertThrows(IllegalArgumentException.class, () -> concurrencyCommandFactory
            .create(profile(List.of(), List.of(new ConcurrencyRule(GuardDimension.AUTHENTICATED_SUBJECT, 1))), login));
        Assertions.assertThrows(NullPointerException.class, () -> concurrencyCommandFactory.create(null, login));
        Assertions.assertThrows(NullPointerException.class,
            () -> concurrencyCommandFactory.create(profile(List.of(), List.of()), null));
    }

    private void assertRateLimitFactoryBoundaries(GuardContext login) {
        Duration second = Duration.ofSeconds(1);
        Assertions.assertThrows(IllegalArgumentException.class, () -> rateLimitCommandFactory
            .create(policy(new RateLimitRule(GuardDimension.AUTHENTICATED_SUBJECT, 1, 1, second)), login));
        Assertions.assertThrows(IllegalArgumentException.class, () -> rateLimitCommandFactory
            .create(policy(new RateLimitRule(GuardDimension.SHARED_ROUTE, Long.MAX_VALUE, 1, second)), login));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> rateLimitCommandFactory.create(
                policy(new RateLimitRule(GuardDimension.SHARED_ROUTE, 1, 1, Duration.ofSeconds(Long.MAX_VALUE))),
                login));
        Assertions.assertThrows(NullPointerException.class, () -> rateLimitCommandFactory.create(null, login));
        Assertions.assertThrows(NullPointerException.class,
            () -> rateLimitCommandFactory.create(profile(List.of(), List.of()), null));
    }

    private void assertInvalidRouting(String origin, Duration timeout) {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new OperationsRoutingProperties(origin, timeout));
    }

    private void assertInvalidSecurity(String issuer, String audience) {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new OperationsSecurityProperties(issuer, audience));
    }

    private GuardProfile policy(RateLimitRule... rules) {
        return profile(List.of(rules), List.of());
    }

    private GuardProfile profile(List<RateLimitRule> rateLimits, List<ConcurrencyRule> concurrencyLimits) {
        return new GuardProfile(rateLimits, concurrencyLimits, Duration.ofSeconds(30), Duration.ofSeconds(60));
    }

    private GatewayHmacProperties.Secret secret(String version, byte fill) {
        return new GatewayHmacProperties.Secret(version, encodedSecret(fill));
    }

    private String encodedSecret(byte fill) {
        byte[] material = new byte[32];
        Arrays.fill(material, fill);
        return Base64.getEncoder().encodeToString(material);
    }

    private List<Class<?>> productionClasses() {
        try {
            Path codeSource =
                Path.of(ApiGatewayApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(codeSource)) {
                return classesFromDirectory(codeSource);
            }
            if (Files.isRegularFile(codeSource) && codeSource.getFileName().toString().endsWith(".jar")) {
                return classesFromJar(codeSource);
            }
            throw new IllegalStateException("Gateway 生产 CodeSource 既不是目录也不是 JAR: " + codeSource);
        } catch (IOException | URISyntaxException exception) {
            throw new IllegalStateException("无法扫描 Gateway 生产 CodeSource", exception);
        }
    }

    private List<Class<?>> classesFromDirectory(Path codeSource) throws IOException {
        Path packageRoot = codeSource.resolve("com/paytech/gateway");
        Assertions.assertTrue(Files.isDirectory(packageRoot), () -> "Gateway 生产包不存在: " + packageRoot);
        try (var paths = Files.walk(packageRoot)) {
            return paths.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".class"))
                .<Class<?>>map(path -> loadProductionClass(codeSource.relativize(path).toString()))
                .sorted(java.util.Comparator.comparing(Class::getName)).toList();
        }
    }

    private List<Class<?>> classesFromJar(Path codeSource) throws IOException {
        try (JarFile jar = new JarFile(codeSource.toFile())) {
            return jar.stream().filter(entry -> !entry.isDirectory()).map(java.util.jar.JarEntry::getName)
                .filter(name -> name.startsWith("com/paytech/gateway/") && name.endsWith(".class"))
                .<Class<?>>map(this::loadProductionClass).sorted(java.util.Comparator.comparing(Class::getName))
                .toList();
        }
    }

    private Class<?> loadProductionClass(String classFile) {
        String className =
            classFile.replace(java.io.File.separatorChar, '.').replace('/', '.').replaceFirst("\\.class$", "");
        try {
            return Class.forName(className, false, ApiGatewayApplication.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("无法加载 Gateway 生产类型: " + className, exception);
        }
    }

    private boolean referencesJwtAuthenticationToken(Class<?> type) {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (var stream = type.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("无法读取 Gateway 生产类型字节码: " + type.getName());
            }
            String classFile = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
            return classFile.contains(
                "org/springframework/security/oauth2/server/resource/authentication/JwtAuthenticationToken");
        } catch (IOException exception) {
            throw new IllegalStateException("无法检查 Gateway 生产类型字节码: " + type.getName(), exception);
        }
    }

    private boolean isMvcController(Class<?> type) {
        return type.isAnnotationPresent(Controller.class) || type.isAnnotationPresent(RestController.class);
    }

    private boolean isDataLayerType(Class<?> type) {
        String name = type.getName().toLowerCase(Locale.ROOT);
        String simpleName = type.getSimpleName();
        return name.contains(".repository.") || name.contains(".mapper.") || name.contains(".database.")
            || simpleName.endsWith("Repository") || simpleName.endsWith("RepositoryImpl")
            || simpleName.endsWith("Mapper") || hasDataLayerAnnotation(type);
    }

    private boolean hasDataLayerAnnotation(Class<?> type) {
        return Arrays.stream(type.getDeclaredAnnotations()).map(Annotation::annotationType)
            .anyMatch(annotationType -> isDataLayerAnnotation(annotationType, new HashSet<>()));
    }

    private boolean isDataLayerAnnotation(Class<? extends Annotation> annotationType, Set<String> visited) {
        String annotationName = annotationType.getName();
        if (Set.of("org.springframework.stereotype.Repository", "org.apache.ibatis.annotations.Mapper")
            .contains(annotationName)) {
            return true;
        }
        if (annotationName.startsWith("java.lang.annotation.") || !visited.add(annotationName)) {
            return false;
        }
        return Arrays.stream(annotationType.getDeclaredAnnotations()).map(Annotation::annotationType)
            .anyMatch(candidate -> isDataLayerAnnotation(candidate, visited));
    }

    private boolean publishesApplicationEvents(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
            .anyMatch(field -> field.getType() == ApplicationEventPublisher.class)
            || Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .anyMatch(parameter -> parameter == ApplicationEventPublisher.class)
            || Arrays.stream(type.getDeclaredMethods()).flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .anyMatch(parameter -> parameter == ApplicationEventPublisher.class);
    }

    private boolean isGatewayProductionType(Class<?> type) {
        if (type == null || !type.getName().startsWith("com.paytech.gateway.")) {
            return false;
        }
        return hasApplicationCodeSource(type);
    }

    private boolean hasApplicationCodeSource(Class<?> type) {
        var applicationCodeSource = ApiGatewayApplication.class.getProtectionDomain().getCodeSource();
        var candidateCodeSource = type.getProtectionDomain().getCodeSource();
        if (applicationCodeSource == null || candidateCodeSource == null) {
            return false;
        }
        try {
            return applicationCodeSource.getLocation().toURI().normalize()
                .equals(candidateCodeSource.getLocation().toURI().normalize());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("无法比较 Gateway 生产 CodeSource", exception);
        }
    }

    private ConfigurableListableBeanFactory configurableBeanFactory() {
        if (applicationContext instanceof ConfigurableApplicationContext configurableApplicationContext) {
            return configurableApplicationContext.getBeanFactory();
        }
        throw new IllegalStateException("Gateway ApplicationContext 不支持 BeanDefinition 检查");
    }

    private BeanOwnership beanOwnership(String beanName, ConfigurableListableBeanFactory beanFactory,
        Set<String> visitedBeans) {
        if (!visitedBeans.add(beanName)) {
            return BeanOwnership.UNKNOWN;
        }
        if (!beanFactory.containsBeanDefinition(beanName)) {
            return isGatewayProductionType(beanFactory.getType(beanName, false)) ? BeanOwnership.APPLICATION
                : BeanOwnership.UNKNOWN;
        }
        BeanDefinition definition = beanFactory.getBeanDefinition(beanName);
        BeanOwnership definitionOwnership = definitionOwnership(definition);
        if (definitionOwnership == BeanOwnership.APPLICATION) {
            return BeanOwnership.APPLICATION;
        }
        String factoryBeanName = definition.getFactoryBeanName();
        if (factoryBeanName != null) {
            BeanOwnership factoryTypeOwnership = typeOwnership(beanFactory.getType(factoryBeanName, false));
            if (factoryTypeOwnership != BeanOwnership.UNKNOWN) {
                return factoryTypeOwnership;
            }
            BeanOwnership factoryOwnership = beanOwnership(factoryBeanName, beanFactory, visitedBeans);
            if (factoryOwnership != BeanOwnership.UNKNOWN) {
                return factoryOwnership;
            }
        }
        if (definitionOwnership == BeanOwnership.FRAMEWORK) {
            return BeanOwnership.FRAMEWORK;
        }
        return isGatewayProductionType(beanFactory.getType(beanName, false)) ? BeanOwnership.APPLICATION
            : BeanOwnership.UNKNOWN;
    }

    private BeanOwnership definitionOwnership(BeanDefinition definition) {
        Set<BeanDefinition> visitedDefinitions = Collections.newSetFromMap(new IdentityHashMap<>());
        BeanOwnership ownership = BeanOwnership.UNKNOWN;
        for (BeanDefinition candidate = definition; candidate != null;
            candidate = candidate.getOriginatingBeanDefinition()) {
            if (!visitedDefinitions.add(candidate)) {
                throw new IllegalStateException("RouteDefinitionWriter BeanDefinition provenance contains a cycle");
            }
            if (isGatewayProductionType(candidate.getResolvableType().resolve())
                || classNameOwnership(candidate.getBeanClassName()) == BeanOwnership.APPLICATION) {
                return BeanOwnership.APPLICATION;
            }
            ownership = preferProvenance(ownership, resourceOwnership(candidate.getResourceDescription()));
            if (ownership == BeanOwnership.APPLICATION) {
                return ownership;
            }
            if (candidate instanceof AnnotatedBeanDefinition annotated) {
                ownership = preferProvenance(ownership, classNameOwnership(annotated.getMetadata().getClassName()));
                if (annotated.getFactoryMethodMetadata() != null && ownership != BeanOwnership.APPLICATION) {
                    ownership = preferProvenance(ownership,
                        classNameOwnership(annotated.getFactoryMethodMetadata().getDeclaringClassName()));
                }
                if (ownership == BeanOwnership.APPLICATION) {
                    return ownership;
                }
            }
            if (candidate instanceof RootBeanDefinition root && root.getResolvedFactoryMethod() != null) {
                ownership =
                    preferProvenance(ownership, typeOwnership(root.getResolvedFactoryMethod().getDeclaringClass()));
                if (ownership == BeanOwnership.APPLICATION) {
                    return ownership;
                }
            }
        }
        return ownership;
    }

    private BeanOwnership preferProvenance(BeanOwnership current, BeanOwnership candidate) {
        if (candidate == BeanOwnership.APPLICATION || current == BeanOwnership.UNKNOWN) {
            return candidate;
        }
        return current;
    }

    private BeanOwnership classNameOwnership(String className) {
        if (className == null || (!className.startsWith("com.paytech.gateway.")
            && !className.startsWith("org.springframework.cloud.gateway."))) {
            return BeanOwnership.UNKNOWN;
        }
        try {
            return typeOwnership(Class.forName(className, false, ApiGatewayApplication.class.getClassLoader()));
        } catch (ClassNotFoundException | LinkageError exception) {
            throw new IllegalStateException("无法验证 RouteDefinitionWriter provenance class: " + className, exception);
        }
    }

    private BeanOwnership resourceOwnership(String resourceDescription) {
        if (resourceDescription == null) {
            return BeanOwnership.UNKNOWN;
        }
        String normalized = resourceDescription.replace('\\', '/');
        for (String packageName : List.of("com/paytech/gateway/", "org/springframework/cloud/gateway/")) {
            int packageStart = normalized.indexOf(packageName);
            int classEnd = normalized.indexOf(".class", packageStart);
            if (packageStart >= 0 && classEnd > packageStart) {
                return classNameOwnership(normalized.substring(packageStart, classEnd).replace('/', '.'));
            }
        }
        return BeanOwnership.UNKNOWN;
    }

    private BeanOwnership typeOwnership(Class<?> type) {
        if (type == null) {
            return BeanOwnership.UNKNOWN;
        }
        if (hasApplicationCodeSource(type)) {
            return BeanOwnership.APPLICATION;
        }
        if (!type.getName().startsWith("org.springframework.cloud.gateway.")) {
            return BeanOwnership.UNKNOWN;
        }
        return type.getProtectionDomain().getCodeSource() == null ? BeanOwnership.UNKNOWN : BeanOwnership.FRAMEWORK;
    }

    private enum BeanOwnership {
        APPLICATION,

        FRAMEWORK,

        UNKNOWN
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Repository
    private @interface RepositoryFixture {}

    @Repository
    private static final class DirectDataStore {}

    @RepositoryFixture
    private static final class MetaDataStore {}

    private static final class PlainStore {}
}
