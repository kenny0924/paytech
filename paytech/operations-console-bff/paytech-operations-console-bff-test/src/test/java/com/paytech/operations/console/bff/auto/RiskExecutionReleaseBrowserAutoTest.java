package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RISK_ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器 HTTP 到授权及 Risk 执行发布接口的完整边界：创建 → 校验 → 激活 → 影子运行 → 再激活，
 * 准备与激活权限隔离，状态编码容错，回执身份不一致拒绝，Owner 业务拒绝透传。
 *
 * @see com.paytech.operations.console.bff.ext.rest.RiskExecutionReleaseController
 */
class RiskExecutionReleaseBrowserAutoTest extends OperationsBffHttpTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ROOT = "/decision-releases/v1/executions";
    private static final String REQUEST_ID = "risk-execution-browser";
    private static final String CONFIG_READ = "900000000000000536";
    private static final String PREPARE = "900000000000101103";
    private static final String CREATE = "{\"pointCode\":\"PAYMENT_ATTEMPT_RISK\",\"flowVersionId\":\"60001\"}";

    @Autowired private PlatformOperationsSimulator platform;
    @Autowired private RiskDecisionConfigurationSimulator risk;
    @LocalServerPort private int port;

    @BeforeEach
    void prepareExecutionRelease() {
        platform.reset();
        platform.useRiskIdentity();
        risk.reset();
    }

    @AfterEach
    void cleanExecutionRelease() {
        try {
            assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString());
            assertTrue(risk.unexpectedRequests().isEmpty(), risk.unexpectedRequests().toString());
        } finally {
            platform.reset();
            risk.reset();
        }
    }

    @Test
    void test_normalFlow() throws Exception {
        String first = body(send("POST", "", "create", CREATE)).path("releaseId").asString();
        var validated = body(send("POST", "/" + first + "/validate", "validate", null));
        assertEquals("SHADOWING", validated.path("status").asString());
        assertEquals(8, validated.path("checks").size());
        assertEquals("ACTIVE", body(send("POST", "/" + first + "/activate", "activate", null)).path("status").asString());

        String second = body(send("POST", "", "create", CREATE)).path("releaseId").asString();
        body(send("POST", "/" + second + "/validate", "validate", null));
        var shadow = body(send("POST", "/" + second + "/shadow-runs", "shadow-run", "{\"sampleTarget\":200}")).path("shadowRun");
        assertEquals(first, shadow.path("baselineReleaseId").asString());
        assertEquals(1, shadow.path("denyToAllowCount").asInt());
        assertEquals(7, shadow.path("actionChangeCount").asInt());
        assertEquals("ACTIVE", body(send("POST", "/" + second + "/activate", "activate", null)).path("status").asString());

        var page = body(send("GET", "?pointCode=PAYMENT_ATTEMPT_RISK&page=1&size=10", "list", null));
        assertEquals(2, page.path("total").asInt());
        assertEquals(second, page.path("items").get(0).path("releaseId").asString());
        assertTrue(page.path("items").get(0).path("active").asBoolean());
        assertEquals("SUPERSEDED", page.path("items").get(1).path("status").asString());
        assertEquals("ACTIVE", body(send("GET", "?pointCode=PAYMENT_ATTEMPT_RISK&status=ACTIVE", "list", null)).path("items").get(0).path("status").asString());
        assertEquals(first, body(send("GET", "/" + first, "detail", null)).path("releaseId").asString());

        assertEquals(10, risk.requests().size());
        for (var call : risk.requests()) {
            assertEquals(RISK_ACCOUNT_ID, call.header("X-Gateway-Actor-Subject"));
            assertEquals(REQUEST_ID, call.header("X-Request-Id"));
            assertNull(call.header("Idempotency-Key"));
        }
        var validate = risk.requests().stream().filter(call -> call.path().endsWith("/validate")).findFirst().orElseThrow();
        assertEquals("", validate.body());
    }

    @Test
    void test_preparePermissionCannotActivate() throws Exception {
        platform.grantPermissions(CONFIG_READ, PREPARE);
        String id = body(send("POST", "", "create", CREATE)).path("releaseId").asString();
        body(send("POST", "/" + id + "/validate", "validate", null));
        int before = risk.requests().size();
        assertEquals(403, send("POST", "/" + id + "/activate", "activate", null).statusCode());
        platform.grantPermissions(CONFIG_READ);
        assertEquals(403, send("POST", "", "create", CREATE).statusCode());
        assertEquals(403, send("POST", "/" + id + "/shadow-runs", "shadow-run", "{\"sampleTarget\":200}").statusCode());
        assertEquals(before, risk.requests().size());
    }

    @Test
    void test_businessRuleViolation() throws Exception {
        String first = body(send("POST", "", "create", CREATE)).path("releaseId").asString();
        var noBaseline = send("POST", "/" + first + "/shadow-runs", "shadow-run", "{\"sampleTarget\":200}");
        assertEquals(400, noBaseline.statusCode(), noBaseline.body());
        body(send("POST", "/" + first + "/validate", "validate", null));
        body(send("POST", "/" + first + "/activate", "activate", null));
        String second = body(send("POST", "", "create", CREATE)).path("releaseId").asString();
        body(send("POST", "/" + second + "/validate", "validate", null));
        var withoutShadow = send("POST", "/" + second + "/activate", "activate", null);
        assertEquals(400, withoutShadow.statusCode(), withoutShadow.body());
        assertEquals("BUSINESS_CHECK_FAILED", JSON.readTree(withoutShadow.body()).path("code").asString());
        var checkout = send("POST", "", "create", "{\"pointCode\":\"CHECKOUT_PREPARE\",\"flowVersionId\":\"60001\"}");
        assertEquals(400, checkout.statusCode());
    }

    @Test
    void test_invalidParameter() throws Exception {
        assertEquals(400, send("GET", "", "list", null).statusCode());
        assertEquals(400, send("GET", "?pointCode=PAYMENT_ATTEMPT_RISK&sort=desc", "list", null).statusCode());
        assertEquals(400, send("POST", "", "create", "{\"pointCode\":\"PAYMENT_ATTEMPT_RISK\"}").statusCode());
        assertEquals(422, send("POST", "", "create", "{\"pointCode\":\"PAYMENT_ATTEMPT_RISK\",\"flowVersionId\":\"0\"}").statusCode());
        assertEquals(422, send("POST", "/90001/shadow-runs", "shadow-run", "{\"sampleTarget\":2001}").statusCode());
        assertEquals(400, send("POST", "/90001/shadow-runs", "shadow-run", "{\"sampleTarget\":\"200\"}").statusCode());
        assertEquals(400, send("GET", "/abc", "detail", null).statusCode());
        assertTrue(risk.requests().isEmpty());
    }

    @Test
    void test_edgeCase() throws Exception {
        String id = body(send("POST", "", "create", CREATE)).path("releaseId").asString();
        risk.corruptWire(wire -> wire.put("status", "REVIEW_PENDING"));
        assertEquals("REVIEW_PENDING", body(send("GET", "/" + id, "detail", null)).path("status").asString());
        risk.corruptWire(wire -> wire.put("releaseId", "99999"));
        assertEquals(502, send("GET", "/" + id, "detail", null).statusCode());
        risk.corruptWire(wire -> ((ObjectNode) wire.get("items").get(0)).put("pointCode", "CHECKOUT_PREPARE"));
        assertEquals(502, send("GET", "?pointCode=PAYMENT_ATTEMPT_RISK", "list", null).statusCode());
        risk.corruptWire(wire -> wire.put("privateOwnerState", "canary"));
        var unknown = send("GET", "/" + id, "detail", null);
        assertEquals(502, unknown.statusCode());
        assertFalse(unknown.body().contains("canary"));
    }

    private static JsonNode body(HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), response.body());
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, String route, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + ROOT + path)).timeout(Duration.ofSeconds(8));
        Map.ofEntries(Map.entry("Origin", "https://operations-console.local.test"), Map.entry("X-Request-Id", REQUEST_ID),
            Map.entry("X-Gateway-Realm", "OPERATIONS"), Map.entry("X-Gateway-Channel", "OPERATIONS"),
            Map.entry("X-Gateway-Actor-Type", "PLATFORM_USER"), Map.entry("X-Gateway-Actor-Subject", RISK_ACCOUNT_ID),
            Map.entry("X-Gateway-Session-Id", SESSION_ID), Map.entry("X-Gateway-Route-Code", "operations-risk-execution-" + route),
            Map.entry("X-Gateway-Context-Type", "TENANT"), Map.entry("X-Gateway-Tenant-Type", "INTERNAL"),
            Map.entry("X-Gateway-Tenant-Id", "tenant_10001"), Map.entry("X-Gateway-Tenant-Member-Id", "member_10001")).forEach(builder::header);
        if (body != null || method.equals("POST")) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try (var client = HttpClient.newHttpClient()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
