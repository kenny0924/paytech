package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RISK_ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器 HTTP 到授权及 Risk 名单接口的完整边界：转发身份、权限隔离、Owner 错误透传、严格请求与主体原值不进日志。
 *
 * @see com.paytech.operations.console.bff.ext.rest.RiskListController
 */
@ExtendWith(OutputCaptureExtension.class)
class RiskListBrowserAutoTest extends OperationsBffHttpTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ROOT = "/risk-lists/v1/lists";
    private static final String REQUEST_ID = "risk-list-browser";
    private static final String SUBJECT = "203.0.113.77";
    private static final String LIST_READ = "900000000000101101";
    private static final String LIST_MANAGE = "900000000000101102";

    @Autowired private PlatformOperationsSimulator platform;
    @Autowired private RiskDecisionConfigurationSimulator risk;
    @LocalServerPort private int port;

    @BeforeEach
    void prepareRiskList() {
        platform.reset();
        platform.useRiskIdentity();
        risk.reset();
    }

    @AfterEach
    void cleanRiskList() {
        try {
            assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString());
            assertTrue(risk.unexpectedRequests().isEmpty(), risk.unexpectedRequests().toString());
        } finally {
            platform.reset();
            risk.reset();
        }
    }

    @Test
    void test_normalFlow(CapturedOutput output) throws Exception {
        var created = send("POST", "", "create", "{\"listCode\":\"BLOCK_IP\",\"listName\":\"拦截 IP\",\"subjectType\":\"IP_ADDRESS\",\"purpose\":\"BLOCK\"}");
        ok(created);
        assertEquals("BLOCK_IP", JSON.readTree(created.body()).path("listCode").asString());
        var page = send("GET", "?page=1&size=10", "list", null);
        ok(page);
        assertEquals(1, JSON.readTree(page.body()).path("total").asInt());

        var added = send("POST", "/BLOCK_IP/entries", "entry-add", entry(SUBJECT));
        ok(added);
        var entry = JSON.readTree(added.body());
        assertEquals("203.0.***", entry.path("maskedSubjectKey").asString());
        assertFalse(entry.has("subjectValue"));
        var found = send("GET", "/BLOCK_IP/entries?subjectValue=" + URLEncoder.encode(SUBJECT, StandardCharsets.UTF_8) + "&status=ACTIVE&page=1&size=10", "entries", null);
        ok(found);
        assertEquals(1, JSON.readTree(found.body()).path("items").size());
        var released = send("POST", "/BLOCK_IP/entries/" + entry.path("entryId").asString() + "/deactivate", "entry-deactivate", "{\"reason\":\"误封\"}");
        ok(released);
        assertEquals("RELEASED", JSON.readTree(released.body()).path("status").asString());

        assertEquals(5, risk.requests().size());
        assertEquals(5, platform.completedAuthorizationCount());
        for (var call : risk.requests()) {
            assertEquals(RISK_ACCOUNT_ID, call.header("X-Gateway-Actor-Subject"));
            assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id"));
            assertEquals(REQUEST_ID, call.header("X-Request-Id"));
        }
        assertFalse(output.getAll().contains(SUBJECT), "主体原值不得进入 BFF 日志");
        assertFalse(created.body().contains(SUBJECT) || page.body().contains(SUBJECT) || found.body().contains(SUBJECT));
    }

    @Test
    void test_readPermissionCannotWrite() throws Exception {
        platform.grantPermissions(LIST_READ);
        ok(send("GET", "", "list", null));
        int before = risk.requests().size();
        assertEquals(403, send("POST", "", "create", "{\"listCode\":\"BLOCK_IP\",\"listName\":\"拦截\",\"subjectType\":\"IP_ADDRESS\",\"purpose\":\"BLOCK\"}").statusCode());
        assertEquals(403, send("POST", "/BLOCK_IP/entries", "entry-add", entry(SUBJECT)).statusCode());
        assertEquals(before, risk.requests().size());
        platform.grantPermissions(LIST_MANAGE);
        assertEquals(403, send("GET", "/BLOCK_IP/entries", "entries", null).statusCode());
        assertEquals(before, risk.requests().size());
    }

    @Test
    void test_invalidParameter() throws Exception {
        var body = (ObjectNode) JSON.readTree(entry(SUBJECT));
        body.put("operator", "spoofed");
        assertEquals(400, send("POST", "/BLOCK_IP/entries", "entry-add", JSON.writeValueAsString(body)).statusCode());
        body.remove("operator");
        body.remove("effectiveTo");
        assertEquals(400, send("POST", "/BLOCK_IP/entries", "entry-add", JSON.writeValueAsString(body)).statusCode());
        assertEquals(422, send("POST", "", "create", "{\"listCode\":\"bad\",\"listName\":\"x\",\"subjectType\":\"IP_ADDRESS\",\"purpose\":\"BLOCK\"}").statusCode());
        assertEquals(400, send("GET", "/BLOCK_IP/entries?unknown=1", "entries", null).statusCode());
        assertEquals(400, send("GET", "/BLOCK_IP/entries?status=ACTIVE&status=EXPIRED", "entries", null).statusCode());
        assertEquals(400, send("GET", "?size=7", "list", null).statusCode());
        assertTrue(risk.requests().isEmpty());
    }

    @Test
    void test_businessRuleViolation() throws Exception {
        ok(send("POST", "", "create", "{\"listCode\":\"BLOCK_IP\",\"listName\":\"拦截\",\"subjectType\":\"IP_ADDRESS\",\"purpose\":\"BLOCK\"}"));
        ok(send("POST", "/BLOCK_IP/entries", "entry-add", entry(SUBJECT)));
        var duplicate = send("POST", "/BLOCK_IP/entries", "entry-add", entry(SUBJECT));
        assertEquals(409, duplicate.statusCode(), duplicate.body());
        assertEquals("CONCURRENT_REQUEST", JSON.readTree(duplicate.body()).path("code").asString());
        var exists = send("POST", "", "create", "{\"listCode\":\"BLOCK_IP\",\"listName\":\"拦截\",\"subjectType\":\"IP_ADDRESS\",\"purpose\":\"BLOCK\"}");
        assertEquals(400, exists.statusCode());
        assertEquals("BUSINESS_CHECK_FAILED", JSON.readTree(exists.body()).path("code").asString());
        assertEquals(4, risk.requests().size());
    }

    @Test
    void test_ownerResponseMismatchIsRejected() throws Exception {
        risk.corruptWire(wire -> wire.put("listCode", "OTHER"));
        var mismatch = send("POST", "", "create", "{\"listCode\":\"BLOCK_IP\",\"listName\":\"拦截\",\"subjectType\":\"IP_ADDRESS\",\"purpose\":\"BLOCK\"}");
        assertEquals(502, mismatch.statusCode(), mismatch.body());
        risk.corruptWire(wire -> wire.put("subjectValue", SUBJECT));
        var leaked = send("GET", "", "list", null);
        assertEquals(502, leaked.statusCode(), leaked.body());
        assertFalse(leaked.body().contains(SUBJECT));
        assertEquals(2, risk.requests().size());
    }

    private static String entry(String subject) {
        return "{\"subjectValue\":\"" + subject + "\",\"effectiveFrom\":null,\"effectiveTo\":null,\"reason\":\"卡测试来源\"}";
    }

    private static void ok(HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), response.body());
    }

    private HttpResponse<String> send(String method, String path, String route, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + ROOT + path)).timeout(Duration.ofSeconds(8));
        Map.ofEntries(Map.entry("Origin", "https://operations-console.local.test"), Map.entry("X-Request-Id", REQUEST_ID),
            Map.entry("X-Gateway-Realm", "OPERATIONS"), Map.entry("X-Gateway-Channel", "OPERATIONS"),
            Map.entry("X-Gateway-Actor-Type", "PLATFORM_USER"), Map.entry("X-Gateway-Actor-Subject", RISK_ACCOUNT_ID),
            Map.entry("X-Gateway-Session-Id", SESSION_ID), Map.entry("X-Gateway-Route-Code", "operations-risk-list-" + route),
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
