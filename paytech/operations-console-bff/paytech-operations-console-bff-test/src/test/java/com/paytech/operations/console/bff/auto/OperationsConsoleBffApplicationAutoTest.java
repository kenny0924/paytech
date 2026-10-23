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

import com.paytech.operations.console.bff.service.processor.RiskEvaluationExplanationProcessor;
import com.paytech.foundation.web.error.GlobalExceptionHandler;
import com.paytech.foundation.web.logging.HttpRequestResponseLoggingFilter;
import com.paytech.foundation.web.logging.WebLoggingProperties;
import com.paytech.foundation.web.request.RequestIdFilter;
import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.ext.rest.RiskDecisionConfigurationController;
import com.paytech.operations.console.bff.integration.RiskDecisionConfigurationClient;
import com.paytech.operations.console.bff.integration.impl.RiskDecisionConfigurationClientImpl;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationManagementApi;
import com.paytech.risk.decision.api.DecisionReleaseManagementApi;
import com.paytech.risk.decision.api.DecisionRuleSetManagementApi;
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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通过真实随机端口验证 Operations BFF 应用基础与精确公共接口边界。
 *
 * @author Codex
 */
class OperationsConsoleBffApplicationAutoTest extends OperationsBffHttpTestBase {
    /** 唯一获批的 Risk 页面聚合：评估解释组合解释与规则集详情两个 Risk 接口。 */
    private static final String APPROVED_RISK_AGGREGATION_CLASS = "RiskEvaluationExplanationProcessor.class";


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
                "/decision-dictionaries", "/decision-rule-sets", "/decision-releases", "/decision-flows", "/decision-variables", "/risk-lists", "/risk-decisions", "/external-api-admin",
                "/client-onboarding-admin", "/vault-admin", "/notification-admin", "/webhook-admin", "/currency-admin", "/storage-admin",
                "/data-query-admin/v1/data-sources", "/data-query-admin/v1/data-source-connection-tests",
                "/data-query-admin/v1/template-trials", "/data-query/v1",
                "/channel-admin/v1/channel-accounts", "/finance/reconciliation/v1/cash-accounts", "/workflow-admin", "/workflow",
                "/card-admin/v1/bin-library/uploads"),
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
            "GET /decision-releases/v1/executions",
            "POST /decision-releases/v1/executions",
            "GET /decision-releases/v1/executions/{releaseId}",
            "POST /decision-releases/v1/executions/{releaseId}/validate",
            "POST /decision-releases/v1/executions/{releaseId}/shadow-runs",
            "POST /decision-releases/v1/executions/{releaseId}/activate",
            "GET /risk-lists/v1/lists",
            "POST /risk-lists/v1/lists",
            "GET /risk-lists/v1/lists/{listCode}/entries",
            "POST /risk-lists/v1/lists/{listCode}/entries",
            "POST /risk-lists/v1/lists/{listCode}/entries/{entryId}/deactivate",
            "GET /risk-decisions/v1/evaluations",
            "GET /risk-decisions/v1/evaluations/{evaluationId}",
            "GET /risk-decisions/v1/evaluations/{evaluationId}/explanation",
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
            "GET /currency-admin/v1/currency-specs",
            "POST /currency-admin/v1/currency-specs",
            "PUT /currency-admin/v1/currency-specs/{currency}",
            "PATCH /currency-admin/v1/currency-specs/{currency}/status",
            "GET /storage-admin/v1/instances",
            "POST /storage-admin/v1/instances",
            "PUT /storage-admin/v1/instances/{instanceCode}",
            "PUT /storage-admin/v1/instances/{instanceCode}/credential",
            "PATCH /storage-admin/v1/instances/{instanceCode}/status",
            "POST /storage-admin/v1/instances/{instanceCode}/buckets",
            "POST /storage-admin/v1/instances/{instanceCode}/buckets/{bucketName}/check",
            "PATCH /storage-admin/v1/instances/{instanceCode}/buckets/{bucketName}/status",
            "GET /storage-admin/v1/routes",
            "POST /storage-admin/v1/routes",
            "PUT /storage-admin/v1/routes/{sourceSystem}",
            "DELETE /storage-admin/v1/routes/{sourceSystem}",
            "GET /card-admin/v1/bin-library/versions",
            "GET /card-admin/v1/bin-library/versions/{versionNo}",
            "GET /card-admin/v1/bin-library/versions/{versionNo}/errors",
            "GET /card-admin/v1/bin-library/activations",
            "GET /card-admin/v1/bin-library/bins/{bin}",
            "POST /card-admin/v1/bin-library/uploads",
            "POST /card-admin/v1/bin-library/uploads/{uploadId}/completion",
            "POST /card-admin/v1/bin-library/versions",
            "POST /card-admin/v1/bin-library/versions/{versionNo}/activation",
            "GET /data-query-admin/v1/data-sources",
            "GET /data-query-admin/v1/data-sources/{dataSourceId}",
            "POST /data-query-admin/v1/data-sources",
            "PUT /data-query-admin/v1/data-sources/{dataSourceId}",
            "POST /data-query-admin/v1/data-source-connection-tests",
            "POST /data-query-admin/v1/data-sources/{dataSourceId}/status",
            "DELETE /data-query-admin/v1/data-sources/{dataSourceId}",
            "GET /data-query-admin/v1/templates",
            "GET /data-query-admin/v1/templates/{templateId}",
            "POST /data-query-admin/v1/templates",
            "PUT /data-query-admin/v1/templates/{templateId}",
            "POST /data-query-admin/v1/templates/{templateId}/status",
            "GET /data-query-admin/v1/templates/{templateId}/versions",
            "GET /data-query-admin/v1/templates/{templateId}/versions/{versionNo}",
            "POST /data-query-admin/v1/templates/{templateId}/restoration",
            "POST /data-query-admin/v1/template-validations",
            "POST /data-query-admin/v1/template-trials",
            "POST /data-query/v1/templates/{templateCode}/executions",
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
            "GET /external-api-admin/v1/integrations/{integrationCode}/verify-accounts",
            "GET /external-api-admin/v1/integrations/{integrationCode}/verify-accounts/{accountCode}",
            "POST /external-api-admin/v1/integrations/{integrationCode}/verify-accounts",
            "PUT /external-api-admin/v1/integrations/{integrationCode}/verify-accounts/{accountCode}",
            "DELETE /external-api-admin/v1/integrations/{integrationCode}/verify-accounts/{accountCode}",
            "GET /external-api-admin/v1/integrations/{integrationCode}/verify-cases",
            "GET /external-api-admin/v1/integrations/{integrationCode}/verify-cases/{caseCode}",
            "POST /external-api-admin/v1/integrations/{integrationCode}/verify-cases",
            "PUT /external-api-admin/v1/integrations/{integrationCode}/verify-cases/{caseCode}",
            "DELETE /external-api-admin/v1/integrations/{integrationCode}/verify-cases/{caseCode}",
            "POST /external-api-admin/v1/integrations/{integrationCode}/verify-cases/validate",
            "POST /external-api-admin/v1/integrations/{integrationCode}/verify-cases/{caseCode}/runs",
            "GET /external-api-admin/v1/integrations/{integrationCode}/verify-runs",
            "GET /external-api-admin/v1/integrations/{integrationCode}/verify-runs/{runCode}",
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
            "POST /client-onboarding-admin/v1/cases/{caseId}/rejection",
            "POST /client-onboarding-admin/v1/cases/{caseId}/files/{fileId}/download",
            "GET /vault-admin/v1/data-types", "POST /vault-admin/v1/data-types", "PUT /vault-admin/v1/data-types/{dataTypeCode}",
            "GET /vault-admin/v1/access-rules", "POST /vault-admin/v1/access-rules",
            "PUT /vault-admin/v1/access-rules/{accessRuleId}", "GET /vault-admin/v1/policy-changes",
            "GET /vault-admin/v1/policy-changes/{policyVersionNo}", "POST /vault-admin/v1/manual/protect",
            "POST /vault-admin/v1/manual/secret-protect", "POST /vault-admin/v1/manual/reveal",
            "POST /vault-admin/v1/manual/secret-reveal", "GET /vault-admin/v1/manual-events",
            "GET /notification-admin/v1/templates", "GET /notification-admin/v1/templates/{templateCode}",
            "POST /notification-admin/v1/templates", "PATCH /notification-admin/v1/templates/{templateCode}/status",
            "PUT /notification-admin/v1/templates/{templateCode}/contents/{locale}",
            "POST /notification-admin/v1/template-preview", "PUT /notification-admin/v1/templates/{templateCode}/route",
            "PATCH /notification-admin/v1/templates/{templateCode}/route/status",
            "GET /notification-admin/v1/providers", "PATCH /notification-admin/v1/providers/{providerCode}/status",
            "POST /notification-admin/v1/providers", "PUT /notification-admin/v1/providers/{providerCode}",
            "PUT /notification-admin/v1/providers/{providerCode}/credential",
            "POST /notification-admin/v1/providers/{providerCode}/test-send",
            "GET /notification-admin/v1/requests", "GET /notification-admin/v1/requests/{requestId}",
            "GET /webhook-admin/v1/clients", "GET /webhook-admin/v1/clients/{clientId}/endpoints",
            "POST /webhook-admin/v1/clients/{clientId}/endpoints", "PUT /webhook-admin/v1/endpoints/{endpointId}",
            "PATCH /webhook-admin/v1/endpoints/{endpointId}/status",
            "POST /webhook-admin/v1/endpoints/{endpointId}/signing-secret/rotations",
            "POST /webhook-admin/v1/endpoints/{endpointId}/tests", "GET /webhook-admin/v1/deliveries",
            "GET /webhook-admin/v1/deliveries/{deliveryId}", "POST /webhook-admin/v1/deliveries/{deliveryId}/replays",
            "GET /finance/finance-entities/v1", "POST /finance/finance-entities/v1",
            "GET /finance/fiscal-calendars/v1", "POST /finance/fiscal-calendars/v1",
            "GET /finance/fiscal-calendars/v1/{fiscalCalendarId}/periods",
            "POST /finance/fiscal-calendars/v1/{fiscalCalendarId}/periods",
            "POST /finance/accounting-periods/v1/{accountingPeriodId}/open",
            "GET /finance/ledger-books/v1", "POST /finance/ledger-books/v1",
            "GET /finance/ledger-books/v1/{ledgerBookId}/accounts", "POST /finance/ledger-books/v1/{ledgerBookId}/accounts",
            "GET /finance/ledger-books/v1/{ledgerBookId}/accounts/{accountId}/journal-lines",
            "GET /finance/ledger-books/v1/{ledgerBookId}/journal-entries/{journalEntryId}",
            "GET /finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/account-balances",
            "GET /finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances",
            "GET /finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances/{runNo}",
            "GET /finance/chart-of-accounts/v1", "GET /finance/chart-of-accounts/v1/{chartCode}/accounts",
            "POST /finance/chart-of-accounts/v1/{chartCode}/accounts",
            "POST /finance/chart-of-accounts/v1/{chartCode}/accounts/{chartAccountId}/update",
            "POST /finance/chart-of-accounts/v1/{chartCode}/accounts/{chartAccountId}/disable",
            "POST /finance/chart-of-accounts/v1/{chartCode}/accounts/{chartAccountId}/enable",
            "GET /finance/chart-of-accounts/v1/{chartCode}/changes",
            "GET /finance/currencies/v1", "GET /finance/balances/v1/clients/{clientId}",
            "GET /finance/balances/v1/partners/{partnerId}", "GET /finance/owner-accounts/v1",
            "GET /finance/owner-accounts/v1/{accountId}/entries", "GET /finance/account-postings/v1/{postingId}",
            "POST /finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances",
            "GET /finance/manual-adjustments/v1", "POST /finance/manual-adjustments/v1",
            "GET /finance/manual-adjustments/v1/{manualAdjustmentId}",
            "POST /finance/manual-adjustments/v1/{manualAdjustmentId}/approve",
            "POST /finance/manual-adjustments/v1/{manualAdjustmentId}/reject",
            "GET /finance/billing-facts/v1", "POST /finance/billing-facts/v1", "GET /finance/billing-facts/v1/{factId}",
            "POST /finance/billing-facts/v1/{factId}/approve", "POST /finance/billing-facts/v1/{factId}/void",
            "GET /finance/period-close/v1/board", "GET /finance/accounting-periods/v1/{accountingPeriodId}/closes",
            "GET /finance/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}",
            "POST /finance/accounting-periods/v1/{accountingPeriodId}/closes",
            "POST /finance/accounting-periods/v1/{accountingPeriodId}/reopens",
            "POST /finance/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}/approve",
            "POST /finance/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}/reject",
            "GET /finance/accounting-rule-bundles/v1", "GET /finance/accounting-rule-bundles/v1/{bundleCode}",
            "GET /finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}",
            "POST /finance/accounting-rule-bundles/v1/{bundleCode}/versions",
            "POST /finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/submit",
            "POST /finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/approve",
            "POST /finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/reject",
            "POST /finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/void",
            "GET /commercial-admin/v1/agreements/{agreementId}", "POST /commercial-admin/v1/agreements",
            "POST /commercial-admin/v1/agreements/{agreementId}/submit-for-signature",
            "POST /commercial-admin/v1/agreements/{agreementId}/parties/{partyId}/acceptance",
            "POST /commercial-admin/v1/agreements/{agreementId}/activate",
            "POST /commercial-admin/v1/agreements/{agreementId}/terminate",
            "PUT /commercial-admin/v1/agreements/{agreementId}/products",
            "POST /commercial-admin/v1/agreements/{agreementId}/capabilities",
            "PUT /commercial-admin/v1/agreements/{agreementId}/capabilities/{agreementCapabilityId}",
            "GET /commercial-admin/v1/pricing-profiles", "POST /commercial-admin/v1/pricing-profiles",
            "PUT /commercial-admin/v1/pricing-profiles/{pricingProfileId}",
            "POST /commercial-admin/v1/pricing-profiles/{pricingProfileId}/activate",
            "POST /commercial-admin/v1/pricing-profiles/{pricingProfileId}/retire",
            "POST /commercial-admin/v1/commercial-terms/plans",
            "POST /commercial-admin/v1/commercial-terms/plans/{planId}/versions",
            "PUT /commercial-admin/v1/commercial-terms/versions/{versionId}/prices",
            "POST /commercial-admin/v1/commercial-terms/versions/{versionId}/submit",
            "POST /commercial-admin/v1/commercial-terms/versions/{versionId}/approve",
            "POST /commercial-admin/v1/commercial-terms/versions/{versionId}/reject",
            "POST /commercial-admin/v1/commercial-terms/versions/{versionId}/schedule",
            "POST /commercial-admin/v1/commercial-terms/versions/{versionId}/activate",
            "GET /commercial-admin/v1/commercial-terms/versions/{versionId}",
            "GET /commercial-admin/v1/commercial-terms/versions/{versionId}/prices",
            "GET /commercial-admin/v1/dashboard", "GET /commercial-admin/v1/rate-cards",
            "POST /commercial-admin/v1/commercial-prices/simulate", "GET /commercial-admin/v1/agreements",
            "GET /commercial-admin/v1/commercial-terms/plans", "GET /commercial-admin/v1/commercial-terms/versions",
            "GET /commercial-admin/v1/commercial-terms/versions/{baseVersionId}/diff/{targetVersionId}",
            "GET /commercial-admin/v1/products", "GET /commercial-admin/v1/products/capabilities",
            "GET /commercial-admin/v1/products/fee-events", "GET /commercial-admin/v1/business-changes",
            "GET /commercial-admin/v1/products/dimensions", "POST /commercial-admin/v1/products/capabilities",
            "PUT /commercial-admin/v1/products/capabilities/{productCapabilityId}",
            "POST /commercial-admin/v1/products/capabilities/{productCapabilityId}/retire",
            "POST /commercial-admin/v1/products/capabilities/{productCapabilityId}/activate",
            "PUT /commercial-admin/v1/products/fee-events/{productFeeEventId}/capability",
            "GET /commercial-admin/v1/commercial-terms/templates", "POST /commercial-admin/v1/commercial-terms/templates",
            "POST /commercial-admin/v1/commercial-terms/templates/{planId}/versions",
            "PUT /commercial-admin/v1/commercial-terms/template-versions/{versionId}/prices",
            "POST /commercial-admin/v1/commercial-terms/template-versions/{versionId}/submit",
            "POST /commercial-admin/v1/commercial-terms/template-versions/{versionId}/approve",
            "POST /commercial-admin/v1/commercial-terms/template-versions/{versionId}/reject",
            "POST /commercial-admin/v1/commercial-terms/template-versions/{versionId}/schedule",
            "POST /commercial-admin/v1/commercial-terms/template-versions/{versionId}/activate",
            "POST /commercial-admin/v1/commercial-terms/versions/{versionId}/fill-from-template",
            "POST /commercial-admin/v1/commercial-terms/versions/{versionId}/acceptance",
            "GET /channel-admin/v1/channels", "GET /channel-admin/v1/channels/{channelId}", "POST /channel-admin/v1/channels",
            "PUT /channel-admin/v1/channels/{channelId}", "POST /channel-admin/v1/channels/{channelId}/status",
            "PUT /channel-admin/v1/channels/{channelId}/endpoints", "GET /channel-admin/v1/channel-call-grants",
            "POST /channel-admin/v1/channel-call-grants", "POST /channel-admin/v1/channel-call-grants/{callGrantId}/status",
            "GET /channel-admin/v1/channel-accounts", "GET /channel-admin/v1/channel-accounts/{channelAccountId}",
            "POST /channel-admin/v1/channel-accounts", "PUT /channel-admin/v1/channel-accounts/{channelAccountId}",
            "PUT /channel-admin/v1/channel-accounts/{channelAccountId}/configuration",
            "POST /channel-admin/v1/channel-accounts/{channelAccountId}/status", "GET /channel-admin/v1/mids",
            "GET /channel-admin/v1/mids/{midId}", "POST /channel-admin/v1/mids", "PUT /channel-admin/v1/mids/{midId}/account",
            "POST /channel-admin/v1/mids/{midId}/status", "GET /channel-admin/v1/mid-capabilities/definitions",
            "POST /channel-admin/v1/mid-capabilities", "PUT /channel-admin/v1/mid-capabilities/{midCapabilityId}/details",
            "POST /channel-admin/v1/mid-capabilities/{midCapabilityId}/status", "GET /channel-admin/v1/config-changes",
            "GET /channel-admin/v1/channel-cost-plans", "GET /channel-admin/v1/channel-cost-plans/{planCode}",
            "GET /channel-admin/v1/channel-cost-plans/{planCode}/versions/{costVer}",
            "POST /channel-admin/v1/channel-cost-plans", "POST /channel-admin/v1/channel-cost-plans/{planCode}/versions",
            "POST /channel-admin/v1/channel-cost-plans/{planCode}/versions/{costVer}/submit",
            "POST /channel-admin/v1/channel-cost-plans/{planCode}/versions/{costVer}/approve",
            "POST /channel-admin/v1/channel-cost-plans/{planCode}/versions/{costVer}/reject",
            "POST /channel-admin/v1/channel-cost-plans/{planCode}/versions/{costVer}/cancel",
            "GET /channel-admin/v1/channel-cost-plans/{planCode}/versions/{baseCostVer}/diff/{targetCostVer}",
            "GET /finance/reconciliation/v1/clearing-accounts", "POST /finance/reconciliation/v1/clearing-accounts",
            "GET /finance/reconciliation/v1/clearing-accounts/unassigned-mids", "GET /finance/reconciliation/v1/clearing-accounts/{clearingAccountId}",
            "PUT /finance/reconciliation/v1/clearing-accounts/{clearingAccountId}/settings", "POST /finance/reconciliation/v1/clearing-accounts/{clearingAccountId}/mids",
            "DELETE /finance/reconciliation/v1/clearing-accounts/{clearingAccountId}/mids/{midId}", "POST /finance/reconciliation/v1/clearing-accounts/{clearingAccountId}/status",
            "GET /finance/reconciliation/v1/cash-accounts", "POST /finance/reconciliation/v1/cash-accounts",
            "GET /finance/reconciliation/v1/cash-accounts/{cashAccountId}", "POST /finance/reconciliation/v1/cash-accounts/{cashAccountId}/closure",
            "POST /finance/reconciliation/v1/cash-accounts/{cashAccountId}/approval", "GET /finance/reconciliation/v1/channel-statements",
            "POST /finance/reconciliation/v1/channel-statements", "GET /finance/reconciliation/v1/channel-statements/{statementId}",
            "GET /finance/reconciliation/v1/channel-statements/{statementId}/lines", "POST /finance/reconciliation/v1/channel-statements/{statementId}/lines",
            "POST /finance/reconciliation/v1/channel-statements/{statementId}/completion", "POST /finance/reconciliation/v1/channel-statements/{statementId}/void",
            "GET /finance/reconciliation/v1/platform-txns", "GET /finance/reconciliation/v1/bank-statements",
            "POST /finance/reconciliation/v1/bank-statements", "GET /finance/reconciliation/v1/bank-statements/{bankStatementId}/lines",
            "POST /finance/reconciliation/v1/bank-statements/{bankStatementId}/lines", "POST /finance/reconciliation/v1/bank-statements/{bankStatementId}/completion",
            "GET /finance/reconciliation/v1/bank-statement-lines", "GET /finance/reconciliation/v1/expected-bank-movements",
            "GET /finance/reconciliation/v1/bank-matches", "POST /finance/reconciliation/v1/bank-matches",
            "POST /finance/reconciliation/v1/bank-matches/{bankMatchId}/confirmation", "POST /finance/reconciliation/v1/bank-matches/{bankMatchId}/rejection",
            "GET /finance/reconciliation/v1/recon-exceptions", "POST /finance/reconciliation/v1/recon-exceptions",
            "GET /finance/reconciliation/v1/recon-exceptions/{exceptionId}", "POST /finance/reconciliation/v1/recon-exceptions/{exceptionId}/resolutions",
            "POST /finance/reconciliation/v1/recon-resolutions/{resolutionId}/approval", "GET /finance/reconciliation/v1/summary",
            "GET /finance/reconciliation/v1/channel-statement-lines", "GET /finance/reconciliation/v1/bank-statements/{bankStatementId}",
            "GET /finance/reconciliation/v1/bank-statement-lines/{bankStatementLineId}",
            "GET /workflow-admin/v1/action-templates",
            "GET /workflow-admin/v1/action-templates/{templateId}",
            "GET /workflow-admin/v1/action-templates/{templateId}/references",
            "POST /workflow-admin/v1/action-templates",
            "PUT /workflow-admin/v1/action-templates/{templateId}",
            "POST /workflow-admin/v1/action-templates/{templateId}/status",
            "GET /workflow-admin/v1/definitions",
            "GET /workflow-admin/v1/definitions/{definitionId}",
            "GET /workflow-admin/v1/definitions/{definitionId}/versions/{versionNo}",
            "POST /workflow-admin/v1/definitions",
            "PUT /workflow-admin/v1/definitions/{definitionId}/draft",
            "POST /workflow-admin/v1/definitions/{definitionId}/publication",
            "POST /workflow-admin/v1/definitions/{definitionId}/status",
            "GET /workflow/v1/launchable-definitions",
            "GET /workflow/v1/launchable-definitions/{definitionId}",
            "POST /workflow/v1/instances",
            "GET /workflow/v1/instances",
            "GET /workflow/v1/instances/{instanceId}",
            "GET /workflow/v1/instances/{instanceId}/activities",
            "POST /workflow/v1/instances/{instanceId}/termination",
            "GET /workflow/v1/my-tasks",
            "GET /workflow/v1/my-tasks/handled",
            "GET /workflow/v1/assignee-candidates",
            "POST /workflow/v1/action-runs/{actionRunId}/submission",
            "POST /workflow/v1/attachments/uploads",
            "POST /workflow/v1/attachments/uploads/{uploadId}/completion",
            "POST /workflow/v1/instances/{instanceId}/files/{fileId}/download",
            "POST /workflow/v1/action-runs/{actionRunId}/transfer",
            "POST /workflow/v1/approvals/{approvalId}/decision",
            "POST /workflow/v1/approvals/{approvalId}/transfer",
            "POST /workflow/v1/callbacks/{callbackId}/retry"), routes);

        Path classes = appModule().resolve("target/classes/com/paytech/operations/console/bff");
        try (var files = Files.walk(classes)) {
            assertTrue(files.filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> !APPROVED_RISK_AGGREGATION_CLASS.equals(name))
                .noneMatch(name -> name.contains("Risk")
                    && (name.contains("Processor") || name.contains("Service") || name.contains("Repository"))),
                "除获批的评估解释页聚合 Processor 外，Risk 适配不得新增 Processor、Service 或 Repository");
        }
    }

    /** Surefire 在本测试模块目录运行；app 模块是同一服务目录下的相邻模块，不向上遍历查找目录名。 */
    private static Path appModule() {
        Path module = Path.of("").resolve("../paytech-operations-console-bff-app").toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(module), "app 模块不存在: " + module);
        return module;
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
            "operationsRiskFlowProxyFactory", "operationsDataQueryPlatformProxyFactory"), factories.keySet());
        assertNotSame(factories.get("operationsPlatformProxyFactory"), factories.get("operationsRiskProxyFactory"));
        assertNotSame(factories.get("operationsRiskProxyFactory"), factories.get("operationsRiskFlowProxyFactory"));
        assertTrue(applicationContext.getBeansOfType(DataSource.class).isEmpty(), "无状态 BFF 不得装配数据库");
        for (String name : applicationContext.getBeanDefinitionNames()) {
            Class<?> type = applicationContext.getType(name);
            if (type == null || !type.getName().startsWith("com.paytech.operations.console.bff.")
                || !type.getSimpleName().contains("Risk") || type == RiskEvaluationExplanationProcessor.class) continue;
            assertFalse(type.getSimpleName().matches(".*(Processor|Service|Repository|Mapper|Database|DataSource).*"),
                "除获批的评估解释页聚合 Processor 外，Risk 适配不得新增业务层或数据库 Bean: " + name);
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
