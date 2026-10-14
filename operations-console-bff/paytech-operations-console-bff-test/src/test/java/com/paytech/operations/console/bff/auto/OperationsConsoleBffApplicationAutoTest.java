package com.paytech.operations.console.bff.auto;

import java.net.URI;
import java.lang.reflect.Proxy;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import com.paytech.foundation.web.error.GlobalExceptionHandler;
import com.paytech.foundation.web.logging.HttpRequestResponseLoggingFilter;
import com.paytech.foundation.web.logging.WebLoggingProperties;
import com.paytech.foundation.web.request.RequestIdFilter;
import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.ext.rest.RiskDecisionConfigurationController;
import com.paytech.operations.console.bff.integration.RiskDecisionConfigurationClient;
import com.paytech.operations.console.bff.integration.impl.RiskDecisionConfigurationClientImpl;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationManagementApi;
import com.paytech.risk.api.decision.DecisionReleaseManagementApi;
import com.paytech.risk.api.decision.DecisionRuleSetManagementApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通过真实随机端口验证 Operations BFF 应用基础与精确公共接口边界。
 *
 * @author Codex
 */
class OperationsConsoleBffApplicationAutoTest extends OperationsBffHttpTestBase {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private ApplicationContext applicationContext;

    /** 公开业务监听不能再通过路径访问任何已启用的管理端点。 */
    @Test
    void test_managementEndpointsAreAbsentFromBusinessListener() throws Exception {
        for (String path : List.of("/actuator/health/readiness", "/actuator/info", "/actuator/prometheus")) {
            var response = CLIENT.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + serverPort + path))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(404, response.statusCode(), "业务监听不得提供管理端点: " + path);
        }
    }

    @Test
    void test_healthEndpointsReportUpWithoutBusinessDependencies() throws Exception {
        for (String path : List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
            HttpResponse<String> response = get(path);

            assertEquals(200, response.statusCode(), "健康端点状态错误: " + path);
            assertTrue(response.body().contains("\"status\":\"UP\""), "健康端点应返回 UP: " + path);
            assertFalse(response.body().contains("\"components\""), "健康端点不应披露组件: " + path);
            assertFalse(response.body().contains("\"details\""), "健康端点不应披露详情: " + path);
        }
    }

    @Test
    void test_infoAndPrometheusAreAvailableOnManagementListener() throws Exception {
        HttpResponse<String> info = get("/actuator/info");
        HttpResponse<String> prometheus = get("/actuator/prometheus");

        assertEquals(200, info.statusCode(), "Info 端点应使用独立管理 Listener");
        assertEquals("{}", info.body(), "当前基础应用尚无业务 Info 内容");
        assertEquals(200, prometheus.statusCode(), "Prometheus 端点应使用独立管理 Listener");
        assertTrue(prometheus.body().contains("jvm_memory_used_bytes"), "指标响应应包含真实 JVM 内存指标");
    }

    @Test
    void test_requestIdIsPreservedOrGeneratedByFoundation() throws Exception {
        String requestId = "operations-foundation-request-01";
        HttpResponse<String> supplied = CLIENT.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + serverPort + "/unmapped-foundation-probe"))
                .timeout(Duration.ofSeconds(5)).header(RequestIdFilter.HEADER_NAME, requestId).GET().build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        HttpResponse<String> generated = CLIENT.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + serverPort + "/unmapped-foundation-probe"))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, supplied.statusCode());
        assertEquals(requestId, supplied.headers().firstValue(RequestIdFilter.HEADER_NAME).orElseThrow());
        assertEquals(404, generated.statusCode());
        assertFalse(generated.headers().firstValue(RequestIdFilter.HEADER_NAME).orElseThrow().isBlank());
    }

    @Test
    void test_onlyApprovedManagementEndpointsAreExposed() {
        Set<String> exposedEndpoints = applicationContext.getBean(WebEndpointsSupplier.class)
            .getEndpoints().stream()
            .map(endpoint -> endpoint.getEndpointId().toString())
            .collect(Collectors.toSet());

        assertEquals(Set.of("health", "info", "prometheus"), exposedEndpoints,
            "不得暴露 env、configprops、beans、mappings、heapdump 等额外管理端点");
    }

    @Test
    void test_applicationReusesFoundationAndExposesExactOperationsRoutes() throws Exception {
        for (Class<?> component : List.of(RequestIdFilter.class, HttpRequestResponseLoggingFilter.class,
            GlobalExceptionHandler.class)) {
            assertEquals(1, applicationContext.getBeansOfType(component).size(),
                "公共 MVC 组件必须唯一: " + component.getSimpleName());
        }
        assertEquals(1, applicationContext.getBeansOfType(GatewayConfigurationManagementApi.class).size(),
            "Platform Gateway 管理 HTTP 代理必须是全局单例 Bean");
        assertEquals(1, applicationContext.getBeansOfType(RiskDecisionConfigurationController.class).size(),
            "Risk 浏览器入口必须是唯一具体 Controller");
        assertEquals(List.of("/auth", "/authorization", "/iam", "/audit", "/gateway-admin", "/console-admin", "/permission-admin", "/api-credential-admin", "/api-scope-admin",
                "/decision-dictionaries", "/decision-rule-sets", "/decision-releases", "/decision-flows", "/decision-variables", "/external-api-admin",
                "/client-onboarding-admin"),
            applicationContext.getBean(WebLoggingProperties.class).metadataOnlyPathPrefixes(),
            "身份、管理筛选和状态原因只能写元数据日志");
        Set<String> routes = applicationContext.getBean(
                "requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
            .getHandlerMethods().entrySet().stream()
            .filter(entry -> entry.getValue().getBeanType().getPackageName().startsWith("com.paytech.operations.console.bff"))
            .flatMap(entry -> entry.getKey().getPatternValues().stream().flatMap(path ->
                entry.getKey().getMethodsCondition().getMethods().stream().map(method -> method + " " + path)))
            .collect(Collectors.toSet());
        assertEquals(Set.of("POST /auth/v1/login", "POST /auth/v1/refresh", "POST /auth/v1/logout",
            "GET /auth/v1/context", "POST /auth/v1/password", "POST /auth/v1/bootstrap/administrator",
            "GET /authorization/v1/navigation", "GET /iam/v1/accounts", "POST /iam/v1/accounts",
            "POST /iam/v1/accounts/{accountId}/disable", "PUT /iam/v1/accounts/{accountId}/roles",
            "POST /iam/v1/accounts/{accountId}/enable",
            "DELETE /iam/v1/accounts/{accountId}",
            "GET /iam/v1/roles", "GET /iam/v1/role-permission-options", "GET /iam/v1/roles/{roleId}",
            "POST /iam/v1/roles", "PUT /iam/v1/roles/{roleId}", "DELETE /iam/v1/roles/{roleId}", "GET /audit/v1/events",
            "GET /audit/v1/events/{auditEventId}",
            "GET /gateway-admin/v1/target-services", "GET /gateway-admin/v1/target-services/{targetServiceCode}",
            "PATCH /gateway-admin/v1/target-services/{targetServiceCode}/status",
            "GET /gateway-admin/v1/routes", "GET /gateway-admin/v1/routes/{routeCode}",
            "PATCH /gateway-admin/v1/routes/{routeCode}/status",
            "GET /decision-rule-sets/v1/authoring-context/{pointCode}",
            "GET /decision-flows/v1/authoring-context",
            "GET /decision-flows/v1/flows",
            "POST /decision-flows/v1/flows",
            "POST /decision-flows/v1/flows/validate",
            "GET /decision-flows/v1/flows/{flowId}",
            "GET /decision-flows/v1/flows/{flowId}/versions",
            "GET /decision-flows/v1/flows/{flowId}/versions/{flowVersionId}",
            "DELETE /decision-flows/v1/flows/{flowId}",
            "GET /decision-flows/v1/rule-set-reference",
            "GET /decision-flows/v1/legacy-point-versions/{pointVersionId}",
            "GET /decision-rule-sets/v1/rule-sets",
            "GET /decision-rule-sets/v1/rule-sets/{ruleSetVersionId}",
            "POST /decision-rule-sets/v1/rule-sets",
            "DELETE /decision-rule-sets/v1/rule-sets/{ruleSetId}",
            "POST /decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze",
            "GET /decision-rule-sets/v1/rule-sets/{ruleSetId}/versions",
            "GET /decision-rule-sets/v1/rule-sets/{ruleSetId}/release-references",
            "GET /decision-dictionaries/v1/rejection-reasons",
            "GET /decision-dictionaries/v1/rejection-reasons/{reasonCode}",
            "POST /decision-dictionaries/v1/rejection-reasons",
            "PUT /decision-dictionaries/v1/rejection-reasons/{reasonCode}",
            "POST /decision-dictionaries/v1/rejection-reasons/{reasonCode}/status",
            "GET /decision-dictionaries/v1/rejection-reasons/{reasonCode}/references",
            "GET /decision-dictionaries/v1/subject-tags",
            "GET /decision-dictionaries/v1/subject-tags/{tagCode}",
            "POST /decision-dictionaries/v1/subject-tags",
            "PUT /decision-dictionaries/v1/subject-tags/{tagCode}",
            "POST /decision-dictionaries/v1/subject-tags/{tagCode}/status",
            "GET /decision-dictionaries/v1/subject-tags/{tagCode}/references",
            "GET /decision-releases/v1/candidates",
            "GET /decision-releases/v1/candidates/{releaseId}",
            "POST /decision-releases/v1/candidates",
            "POST /decision-releases/v1/candidates/{releaseId}/validate",
            "POST /gateway-admin/v1/target-services",
            "PUT /gateway-admin/v1/target-services/{targetServiceCode}",
            "POST /gateway-admin/v1/routes",
            "PUT /gateway-admin/v1/routes/{routeCode}",
            "GET /console-admin/v1/resources",
            "GET /console-admin/v1/resources/{resourceCode}",
            "POST /console-admin/v1/resources",
            "PUT /console-admin/v1/resources/{resourceCode}",
            "PATCH /console-admin/v1/resources/{resourceCode}/status",
            "GET /permission-admin/v1/permissions",
            "GET /permission-admin/v1/permissions/{permissionCode}",
            "POST /permission-admin/v1/permissions",
            "PUT /permission-admin/v1/permissions/{permissionCode}",
            "PATCH /permission-admin/v1/permissions/{permissionCode}/status",
            "GET /permission-admin/v1/permissions/{permissionCode}/references",
            "GET /gateway-admin/v1/capabilities",
            "GET /console-admin/v1/resource-capabilities",
            "GET /permission-admin/v1/permission-options",
            "GET /api-credential-admin/v1/credentials",
            "GET /api-credential-admin/v1/credentials/{credentialId}",
            "GET /api-credential-admin/v1/credentials/{credentialId}/scopes",
            "POST /api-credential-admin/v1/credentials/{credentialId}/revoke",
            "GET /api-scope-admin/v1/scopes",
            "GET /tenant-access-admin/v1/tenants",
            "GET /tenant-access-admin/v1/tenants/{tenantId}",
            "POST /tenant-access-admin/v1/tenants/{tenantId}/security-status",
            "GET /external-api-admin/v1/standard-apis",
            "GET /external-api-admin/v1/standard-apis/{standardApiCode}",
            "POST /external-api-admin/v1/standard-apis",
            "PUT /external-api-admin/v1/standard-apis/{standardApiCode}",
            "DELETE /external-api-admin/v1/standard-apis/{standardApiCode}",
            "GET /external-api-admin/v1/integrations",
            "GET /external-api-admin/v1/integrations/{integrationCode}",
            "POST /external-api-admin/v1/integrations",
            "PUT /external-api-admin/v1/integrations/{integrationCode}",
            "DELETE /external-api-admin/v1/integrations/{integrationCode}",
            "GET /external-api-admin/v1/integrations/{integrationCode}/apis",
            "GET /external-api-admin/v1/integrations/{integrationCode}/apis/{apiCode}",
            "POST /external-api-admin/v1/integrations/{integrationCode}/apis",
            "PUT /external-api-admin/v1/integrations/{integrationCode}/apis/{apiCode}",
            "DELETE /external-api-admin/v1/integrations/{integrationCode}/apis/{apiCode}",
            "POST /external-api-admin/v1/integrations/{integrationCode}/apis/validate",
            "GET /external-api-admin/v1/integrations/{integrationCode}/scripts",
            "GET /external-api-admin/v1/integrations/{integrationCode}/scripts/{scriptCode}",
            "POST /external-api-admin/v1/integrations/{integrationCode}/scripts",
            "PUT /external-api-admin/v1/integrations/{integrationCode}/scripts/{scriptCode}",
            "DELETE /external-api-admin/v1/integrations/{integrationCode}/scripts/{scriptCode}",
            "POST /external-api-admin/v1/integrations/{integrationCode}/scripts/validate",
            "GET /external-api-admin/v1/integrations/{integrationCode}/implementations",
            "GET /external-api-admin/v1/integrations/{integrationCode}/implementations/{standardApiCode}",
            "POST /external-api-admin/v1/integrations/{integrationCode}/implementations",
            "PUT /external-api-admin/v1/integrations/{integrationCode}/implementations/{standardApiCode}",
            "DELETE /external-api-admin/v1/integrations/{integrationCode}/implementations/{standardApiCode}",
            "POST /external-api-admin/v1/integrations/{integrationCode}/implementations/validate",
            "GET /external-api-admin/v1/integrations/{integrationCode}/flow-editor-context",
            "GET /external-api-admin/v1/editor-capabilities",
            "GET /tenant-access-admin/v1/tenants/{tenantId}/members",
            "GET /tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}",
            "GET /tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}/roles",
            "GET /tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}/permissions",
            "GET /tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}/permissions/{permissionId}/sources",

            "GET /api-scope-admin/v1/scopes/{scopeCode}",
            "POST /api-scope-admin/v1/scopes",
            "PUT /api-scope-admin/v1/scopes/{scopeCode}",
            "GET /api-scope-admin/v1/scope-options",
            "GET /decision-variables/v1/authoring-context",
            "GET /decision-variables/v1/variables",
            "POST /decision-variables/v1/variables",
            "GET /decision-variables/v1/variables/{variableCode}",
            "PUT /decision-variables/v1/variables/{variableCode}",
            "DELETE /decision-variables/v1/variables/{variableCode}",
            "GET /decision-variables/v1/variables/{variableCode}/members",
            "PUT /decision-variables/v1/variables/{variableCode}/members",
            "GET /decision-variables/v1/variables/{variableCode}/references",
            "GET /client-onboarding-admin/v1/cases",
            "GET /client-onboarding-admin/v1/cases/{caseId}",
            "PUT /client-onboarding-admin/v1/cases/{caseId}/classifications",
            "POST /client-onboarding-admin/v1/cases/{caseId}/classification-confirmation",
            "POST /client-onboarding-admin/v1/cases/{caseId}/evidence/{evidenceId}/review",
            "POST /client-onboarding-admin/v1/cases/{caseId}/return",
            "POST /client-onboarding-admin/v1/cases/{caseId}/approval",
            "POST /client-onboarding-admin/v1/cases/{caseId}/rejection"), routes);

        Path classes = webModule().resolve("target/classes/com/paytech/operations/console/bff");
        try (var files = Files.walk(classes)) {
            assertTrue(files.filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .noneMatch(name -> name.contains("Risk")
                    && (name.contains("Processor") || name.contains("Service") || name.contains("Repository"))),
                "简单单 Owner 转发不得新增 Risk Processor、Service 或 Repository");
        }
    }

    private static Path webModule() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("paytech-operations-console-bff-web"))) {
            current = current.getParent();
        }
        assertNotNull(current);
        return current.resolve("paytech-operations-console-bff-web");
    }

    @Test
    void test_riskAssemblyUsesExactlyTenHandlersAndIndependentOwnerProxies() {
        var handlers = applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
            .getHandlerMethods().entrySet().stream()
            .filter(entry -> entry.getValue().getBeanType().equals(RiskDecisionConfigurationController.class))
            .toList();
        assertEquals(10, handlers.size());
        assertEquals(Set.of(
            "GET /decision-rule-sets/v1/authoring-context/{pointCode} -> authoringContext",
            "GET /decision-rule-sets/v1/rule-sets -> ruleSets",
            "GET /decision-rule-sets/v1/rule-sets/{ruleSetVersionId} -> ruleSet",
            "POST /decision-rule-sets/v1/rule-sets -> createRuleSetVersion",
            "DELETE /decision-rule-sets/v1/rule-sets/{ruleSetId} -> deleteRuleSet",
            "POST /decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze -> freezeRuleSetVersion",
            "GET /decision-releases/v1/candidates -> candidates",
            "GET /decision-releases/v1/candidates/{releaseId} -> candidate",
            "POST /decision-releases/v1/candidates -> createCandidate",
            "POST /decision-releases/v1/candidates/{releaseId}/validate -> validateCandidate"),
            handlers.stream().flatMap(entry -> entry.getKey().getPatternValues().stream().flatMap(path ->
                entry.getKey().getMethodsCondition().getMethods().stream().map(method ->
                    method + " " + path + " -> " + entry.getValue().getMethod().getName())))
                .collect(Collectors.toSet()));
        for (Class<?> ownerApi : List.of(DecisionRuleSetManagementApi.class, DecisionReleaseManagementApi.class)) {
            var proxies = applicationContext.getBeansOfType(ownerApi);
            assertEquals(1, proxies.size(), "每个 Risk Owner API 必须只有一个 HTTP 代理");
            assertTrue(Proxy.isProxyClass(proxies.values().iterator().next().getClass()));
        }
        var clients = applicationContext.getBeansOfType(RiskDecisionConfigurationClient.class);
        assertEquals(1, clients.size());
        assertEquals(RiskDecisionConfigurationClientImpl.class, clients.values().iterator().next().getClass());
        var factories = applicationContext.getBeansOfType(HttpServiceProxyFactory.class);
        assertEquals(Set.of("operationsPlatformProxyFactory", "operationsConfigurationPlatformProxyFactory", "operationsRiskProxyFactory",
            "operationsRiskFlowProxyFactory"), factories.keySet());
        assertNotSame(factories.get("operationsPlatformProxyFactory"), factories.get("operationsRiskProxyFactory"));
        assertNotSame(factories.get("operationsRiskProxyFactory"), factories.get("operationsRiskFlowProxyFactory"));
        assertTrue(applicationContext.getBeansOfType(DataSource.class).isEmpty(), "无状态 BFF 不得装配数据库");
        for (String name : applicationContext.getBeanDefinitionNames()) {
            Class<?> type = applicationContext.getType(name);
            if (type == null || !type.getName().startsWith("com.paytech.operations.console.bff.")
                || !type.getSimpleName().contains("Risk")) continue;
            assertFalse(type.getSimpleName().matches(".*(Processor|Service|Repository|Mapper|Database|DataSource).*"),
                "单 Owner 转发不得新增 Risk 业务层或数据库 Bean: " + name);
        }
    }

    @Test
    void test_autoProfileKeepsNetworkAccessIsolated() {
        Environment environment = applicationContext.getEnvironment();

        assertTrue(serverPort > 0, "应用必须通过随机端口真实启动");
        assertEquals("127.0.0.1", environment.getRequiredProperty("server.address"));
        assertEquals(0, environment.getRequiredProperty("server.port", Integer.class));
        assertTrue(managementPort > 0 && managementPort != serverPort, "管理端点必须使用独立随机监听");
        assertEquals("127.0.0.1", environment.getRequiredProperty("management.server.address"));
        assertEquals(0, environment.getRequiredProperty("management.server.port", Integer.class));
        for (String property : List.of("spring.cloud.discovery.enabled",
            "spring.cloud.service-registry.auto-registration.enabled",
            "spring.cloud.nacos.discovery.enabled", "spring.cloud.nacos.discovery.register-enabled")) {
            assertFalse(environment.getRequiredProperty(property, Boolean.class),
                "自动测试必须禁用服务发现和注册: " + property);
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        return CLIENT.send(request(path).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + managementPort + path))
            .timeout(Duration.ofSeconds(5))
            .GET();
    }
}
