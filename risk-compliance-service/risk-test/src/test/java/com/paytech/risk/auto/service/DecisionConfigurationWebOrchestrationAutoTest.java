package com.paytech.risk.auto.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.paytech.risk.RiskApplication;
import com.paytech.risk.api.decision.DecisionReleaseManagementApi;
import com.paytech.risk.api.decision.DecisionRuleSetManagementApi;
import com.paytech.risk.ext.rest.decision.DecisionReleaseManagementController;
import com.paytech.risk.ext.rest.decision.DecisionRuleSetManagementController;
import com.paytech.risk.test.decision.RiskDecisionHttpClient;
import com.paytech.foundation.web.logging.HttpRequestResponseLoggingFilter;
import com.paytech.foundation.web.logging.WebLoggingProperties;
import com.paytech.foundation.web.request.RequestIdFilter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Web 编排切片的无数据库结构门禁。
 *
 * <p>HTTP 合同已经在 risk-api 冻结；这个测试启动真实 Servlet 上下文，确保两组 Controller 与其
 * Processor 可发布九条路由，并验证无数据库前置的请求校验、请求标识与 metadata-only 日志边界。</p>
 */
class DecisionConfigurationWebOrchestrationAutoTest {

    @Test
    void processorsAndControllersArePublishedForBothFrozenApiContracts() {
        assertDoesNotThrow(() -> Class.forName(
            "com.paytech.risk.service.processor.DecisionRuleSetManagementProcessor"));
        assertDoesNotThrow(() -> Class.forName(
            "com.paytech.risk.service.processor.DecisionReleaseManagementProcessor"));
        assertDoesNotThrow(() -> Class.forName(
            "com.paytech.risk.ext.rest.decision.DecisionRuleSetManagementController"));
        assertDoesNotThrow(() -> Class.forName(
            "com.paytech.risk.ext.rest.decision.DecisionReleaseManagementController"));
    }

    @Test
    void controllersImplementOnlyTheFrozenContractsAndRemainValidatedAdapters() {
        assertTrue(DecisionRuleSetManagementApi.class.isAssignableFrom(DecisionRuleSetManagementController.class));
        assertTrue(DecisionReleaseManagementApi.class.isAssignableFrom(DecisionReleaseManagementController.class));
        assertTrue(DecisionRuleSetManagementController.class.isAnnotationPresent(RestController.class));
        assertTrue(DecisionReleaseManagementController.class.isAnnotationPresent(RestController.class));
        assertTrue(DecisionRuleSetManagementController.class.isAnnotationPresent(Validated.class));
        assertTrue(DecisionReleaseManagementController.class.isAnnotationPresent(Validated.class));
    }

    @Test
    void realWebContextPublishesAllRoutesAndSafeRequestInfrastructureWithoutOpeningTheDatabase() throws Exception {
        try (var context = new SpringApplicationBuilder(RiskApplication.class)
            .web(WebApplicationType.SERVLET)
            .run(
                "--spring.main.banner-mode=off",
                "--spring.profiles.active=auto",
                "--management.metrics.use-global-registry=false",
                "--spring.cloud.nacos.discovery.enabled=false",
                "--spring.cloud.service-registry.auto-registration.enabled=false",
                "--server.port=0",
                "--spring.datasource.url=jdbc:mysql://127.0.0.1:1/not-used?connectTimeout=100&socketTimeout=100",
                "--spring.datasource.username=not-used",
                "--spring.datasource.password=not-used",
                "--decision.identifier.region-id=0",
                "--decision.identifier.worker-id=0"
            )) {
            assertEquals(1, context.getBeansOfType(DecisionRuleSetManagementController.class).size());
            assertEquals(1, context.getBeansOfType(DecisionReleaseManagementController.class).size());
            int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
            assertTrue(port > 0);
            assertEquals(Map.ofEntries(
                Map.entry("GET /decision-rule-sets/v1/authoring-context/{pointCode}", "getAuthoringContext"),
                Map.entry("GET /decision-variables/v1/authoring-context", "getAuthoringContext"),
                Map.entry("GET /decision-variables/v1/variables", "listVariables"),
                Map.entry("GET /decision-variables/v1/variables/{variableCode}", "getVariable"),
                Map.entry("POST /decision-variables/v1/variables", "createVariable"),
                Map.entry("PUT /decision-variables/v1/variables/{variableCode}", "updateVariable"),
                Map.entry("DELETE /decision-variables/v1/variables/{variableCode}", "deleteVariable"),
                Map.entry("GET /decision-variables/v1/variables/{variableCode}/members", "listMembers"),
                Map.entry("PUT /decision-variables/v1/variables/{variableCode}/members", "replaceMembers"),
                Map.entry("GET /decision-variables/v1/variables/{variableCode}/references", "getReferences"),
                Map.entry("GET /decision-flows/v1/authoring-context", "authoringContext"),
                Map.entry("GET /decision-flows/v1/flows", "list"),
                Map.entry("POST /decision-flows/v1/flows", "save"),
                Map.entry("POST /decision-flows/v1/flows/validate", "validate"),
                Map.entry("GET /decision-flows/v1/flows/{flowId}", "current"),
                Map.entry("GET /decision-flows/v1/flows/{flowId}/versions", "history"),
                Map.entry("GET /decision-flows/v1/flows/{flowId}/versions/{flowVersionId}", "version"),
                Map.entry("DELETE /decision-flows/v1/flows/{flowId}", "delete"),
                Map.entry("GET /decision-flows/v1/rule-set-reference", "ruleSetReference"),
                Map.entry("GET /decision-flows/v1/legacy-point-versions/{pointVersionId}", "legacyPoint"),
                Map.entry("GET /decision-rule-sets/v1/rule-sets", "listRuleSets"),
                Map.entry("GET /decision-rule-sets/v1/rule-sets/{ruleSetVersionId}", "getRuleSet"),
                Map.entry("POST /decision-rule-sets/v1/rule-sets", "createRuleSetVersion"),
                Map.entry("DELETE /decision-rule-sets/v1/rule-sets/{ruleSetId}", "deleteRuleSet"),
                Map.entry("POST /decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze", "freezeRuleSetVersion"),
                Map.entry("GET /decision-releases/v1/candidates", "listCandidates"),
                Map.entry("GET /decision-releases/v1/candidates/{releaseId}", "getCandidate"),
                Map.entry("POST /decision-releases/v1/candidates", "createCandidate"),
                Map.entry("POST /decision-releases/v1/candidates/{releaseId}/validate", "validateCandidate"),
                Map.entry("GET /decision-rule-sets/v1/rule-sets/{ruleSetId}/versions", "history"),
                Map.entry("GET /decision-rule-sets/v1/rule-sets/{ruleSetId}/release-references", "releases"),
                Map.entry("GET /decision-dictionaries/v1/rejection-reasons", "listRejectionReasons"),
                Map.entry("GET /decision-dictionaries/v1/rejection-reasons/{reasonCode}", "getRejectionReason"),
                Map.entry("POST /decision-dictionaries/v1/rejection-reasons", "createRejectionReason"),
                Map.entry("PUT /decision-dictionaries/v1/rejection-reasons/{reasonCode}", "updateRejectionReason"),
                Map.entry("POST /decision-dictionaries/v1/rejection-reasons/{reasonCode}/status", "changeRejectionReasonStatus"),
                Map.entry("GET /decision-dictionaries/v1/rejection-reasons/{reasonCode}/references", "getRejectionReasonReferences"),
                Map.entry("GET /decision-dictionaries/v1/subject-tags", "listSubjectTags"),
                Map.entry("GET /decision-dictionaries/v1/subject-tags/{tagCode}", "getSubjectTag"),
                Map.entry("POST /decision-dictionaries/v1/subject-tags", "createSubjectTag"),
                Map.entry("PUT /decision-dictionaries/v1/subject-tags/{tagCode}", "updateSubjectTag"),
                Map.entry("POST /decision-dictionaries/v1/subject-tags/{tagCode}/status", "changeSubjectTagStatus"),
                Map.entry("GET /decision-dictionaries/v1/subject-tags/{tagCode}/references", "getSubjectTagReferences")
            ), decisionRoutes(context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)));
            assertEquals(1, context.getBeansOfType(RequestIdFilter.class).size());
            assertEquals(1, context.getBeansOfType(HttpRequestResponseLoggingFilter.class).size());
            assertEquals(Set.of("/internal/due-diligence/material-requirements", "/decision-rule-sets", "/decision-releases", "/decision-dictionaries", "/decision-flows", "/decision-variables"),
                Set.copyOf(context.getBean(WebLoggingProperties.class).metadataOnlyPathPrefixes()));

            var rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(
                ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
            ListAppender<ILoggingEvent> capturedLogs = new ListAppender<>();
            capturedLogs.start();
            rootLogger.addAppender(capturedLogs);
            try {
                HttpResponse<String> invalidActor = send(HttpRequest.newBuilder(uri(port,
                        "/decision-rule-sets/v1/authoring-context/CHECKOUT_PREPARE?probe=query-secret"))
                    .header(RequestIdFilter.HEADER_NAME, "risk-web-invalid-actor")
                    .header("X-Gateway-Actor-Subject", "0")
                    .header("X-Gateway-Session-Id", "session-1")
                    .GET().build());
                assertInvalidRequest(invalidActor, "risk-web-invalid-actor");

                HttpResponse<String> strictJson = send(HttpRequest.newBuilder(uri(port,
                        "/decision-releases/v1/candidates"))
                    .header(RequestIdFilter.HEADER_NAME, "risk-web-strict-json")
                    .header("X-Gateway-Actor-Subject", "1")
                    .header("X-Gateway-Session-Id", "session-1")
                    .header("Idempotency-Key", "valid-key-1")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"unexpected\":\"payload-secret\"}"))
                    .build());
                assertInvalidRequest(strictJson, "risk-web-strict-json");

                HttpResponse<String> semanticBody = send(HttpRequest.newBuilder(uri(port,
                        "/decision-rule-sets/v1/rule-sets/1/freeze"))
                    .header(RequestIdFilter.HEADER_NAME, "risk-web-semantic-body")
                    .header("X-Gateway-Actor-Subject", "1")
                    .header("X-Gateway-Session-Id", "session-1")
                    .header("Idempotency-Key", "valid-key-2")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"expectedContentHash\":\"not-a-hash\"}"))
                    .build());
                var semanticProblem = RiskDecisionHttpClient.assertProblem(
                    semanticBody, 422, "VALIDATION_ERROR", "risk-web-semantic-body");
                assertEquals(1, semanticProblem.path("errors").size());
                assertEquals("expectedContentHash",
                    semanticProblem.path("errors").get(0).path("field").asString());

                List<ILoggingEvent> requestLogs = capturedLogs.list.stream()
                    .filter(event -> event.getLoggerName().equals(HttpRequestResponseLoggingFilter.class.getName()))
                    .filter(event -> event.getFormattedMessage().equals("HTTP request completed"))
                    .toList();
                assertEquals(3, requestLogs.size());
                assertEquals(Set.of("/decision-rule-sets/**", "/decision-releases/**"), requestLogs.stream()
                    .map(event -> String.valueOf(keyValue(event, "uri"))).collect(Collectors.toSet()));
                String renderedLogs = capturedLogs.list.stream()
                    .map(DecisionConfigurationWebOrchestrationAutoTest::render)
                    .collect(Collectors.joining("\n"));
                assertTrue(!renderedLogs.contains("query-secret"));
                assertTrue(!renderedLogs.contains("payload-secret"));
            } finally {
                rootLogger.detachAppender(capturedLogs);
                capturedLogs.stop();
            }
        }
    }

    private static Object keyValue(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream().filter(pair -> pair.key.equals(key))
            .map(pair -> pair.value).findFirst().orElse(null);
    }

    private static String render(ILoggingEvent event) {
        String keyValues = event.getKeyValuePairs() == null ? "" : event.getKeyValuePairs().stream()
            .map(pair -> pair.key + "=" + pair.value).collect(Collectors.joining(","));
        return event.getFormattedMessage() + " " + keyValues;
    }

    private static URI uri(int port, String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static HttpResponse<String> send(HttpRequest request) throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private static void assertInvalidRequest(HttpResponse<String> response, String requestId) {
        RiskDecisionHttpClient.assertProblem(response, 400, "INVALID_REQUEST", requestId);
        assertTrue(!response.body().contains("secret"));
    }

    private static Map<String, String> decisionRoutes(RequestMappingHandlerMapping mapping) {
        return mapping.getHandlerMethods().entrySet().stream()
            .flatMap(entry -> entry.getKey().getPatternValues().stream()
                .filter(path -> path.startsWith("/decision-rule-sets/") || path.startsWith("/decision-releases/")
                    || path.startsWith("/decision-dictionaries/") || path.startsWith("/decision-flows/") || path.startsWith("/decision-variables/"))
                .map(path -> Map.entry(entry.getKey().getMethodsCondition().getMethods().iterator().next().name()
                    + " " + path, entry.getValue().getMethod().getName())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
