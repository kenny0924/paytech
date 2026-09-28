package com.paytech.operations.console.bff.auto;

import static org.junit.jupiter.api.Assertions.*;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator;

/** 从浏览器 HTTP 到授权及 Risk HTTP 的完整统一变量配置边界。 */
class RiskVariableBrowserBoundaryAutoTest extends OperationsBffHttpTestBase {
    @Autowired private PlatformOperationsSimulator platform;
    @Autowired private RiskDecisionConfigurationSimulator risk;
    @LocalServerPort private int port;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ROOT = "/decision-variables/v1/";
    @BeforeEach void prepare() { platform.reset(); platform.useRiskIdentity(); risk.reset(); }
    @AfterEach void clean() {
        try { assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString()); assertTrue(risk.unexpectedRequests().isEmpty(), risk.unexpectedRequests().toString()); }
        finally { platform.reset(); risk.reset(); }
    }
    @Test void nineOperationsAuthorizeIndependentlyAndPreserveExactFields() throws Exception {
        ok(send("GET", "authoring-context", "manage-context", null, null), 200);
        var created = send("POST", "variables", "create", draft(), null); ok(created, 201);
        assertEquals("1", JSON.readTree(created.body()).path("version").asString());
        ok(send("GET", "variables?page=1&size=20", "list", null, null), 200);
        ok(send("GET", "variables/TEST_COLLECTION", "detail", null, null), 200);
        ok(send("PUT", "variables/TEST_COLLECTION", "update", "{\"expectedVersion\":\"1\",\"variableName\":\"改名\",\"description\":null,\"currentValue\":null,\"status\":\"ACTIVE\"}", null), 200);
        ok(send("GET", "variables/TEST_COLLECTION/members?expectedVersion=2&page=1&size=20", "members", null, null), 200);
        var replacement = send("PUT", "variables/TEST_COLLECTION/members", "members-replace", "{\"expectedVersion\":\"2\",\"members\":[{\"value\":\"001\",\"displayName\":\"示例\"}]}", null);
        ok(replacement, 200); assertEquals(1, JSON.readTree(replacement.body()).path("memberCount").asInt());
        ok(send("GET", "variables/TEST_COLLECTION/references", "references", null, null), 200);
        ok(send("DELETE", "variables/TEST_COLLECTION?expectedVersion=3", "delete", null, null), 200);
        assertEquals(9, risk.requests().size()); assertEquals(9, platform.completedAuthorizationCount());
        for (var r : risk.requests()) { assertEquals(RISK_ACCOUNT_ID, r.header("X-Gateway-Actor-Subject")); assertEquals(SESSION_ID, r.header("X-Gateway-Session-Id")); assertEquals("rco19-browser", r.header("X-Request-Id")); assertNull(r.header("Idempotency-Key")); }
        assertEquals("", risk.requests().getLast().body());
    }

    @Test void readCannotWriteManageCannotDeleteAndUnknownBodyKeysNeverReachOwner() throws Exception {
        platform.grantPermissions("900000000000000536");
        ok(send("GET", "authoring-context", "manage-context", null, null), 200);
        ok(send("POST", "variables", "create", draft(), null), 403);
        platform.grantPermissions("900000000000031101");
        ok(send("DELETE", "variables/TEST_COLLECTION?expectedVersion=1", "delete", null, null), 403);
        var changed = (ObjectNode)JSON.readTree(draft()); changed.put("systemManaged", true);
        ok(send("POST", "variables", "create", JSON.writeValueAsString(changed), null), 400);
        assertEquals(1, risk.requests().size());
    }

    @Test void malformedTokensQueriesAndOversizeRequestsAreRejectedBeforeForwarding() throws Exception {
        var body = (ObjectNode)JSON.readTree(draft()); body.put("currentValue", 1.2);
        ok(send("POST", "variables", "create", JSON.writeValueAsString(body), null), 400);
        ok(send("GET", "variables?page=1&page=2", "list", null, null), 400);
        ok(send("GET", "variables", "list", "{}", null), 400);
        body = (ObjectNode)JSON.readTree(draft()); body.put("description", "x".repeat(17000));
        ok(send("POST", "variables", "create", JSON.writeValueAsString(body), null), 413);
        assertTrue(risk.requests().isEmpty());
    }

    @Test void memberBodyBeyondGatewayCeilingNeverReachesOwner() throws Exception {
        String body = "{\"expectedVersion\":\"1\",\"members\":[]}";
        String oversized = body + " ".repeat(65537 - body.length());
        ok(send("PUT", "variables/TEST_COLLECTION/members", "members-replace", oversized, null), 413);
        assertTrue(risk.requests().isEmpty());
    }

    @Test void upstreamIdentityAndCapacityAreCheckedAndBusinessErrorsKeepTheirStatus() throws Exception {
        risk.corruptWire(w -> w.put("privateOwnerState", "canary"));
        var invalid = send("GET", "authoring-context", "manage-context", null, null); ok(invalid, 502); assertFalse(invalid.body().contains("canary"));
        risk.corruptWire(w -> {}); risk.problem(409, "VARIABLE_VERSION_CONFLICT");
        var conflict = send("PUT", "variables/TEST_COLLECTION", "update", "{\"expectedVersion\":\"1\",\"variableName\":\"名称\",\"description\":null,\"currentValue\":null,\"status\":\"ACTIVE\"}", null);
        ok(conflict, 409); assertEquals("VARIABLE_VERSION_CONFLICT", JSON.readTree(conflict.body()).path("code").asString());
        assertFalse(conflict.body().contains(RiskDecisionConfigurationSimulator.UPSTREAM_DETAIL_CANARY)); assertEquals(2, risk.requests().size());
    }

    @Test void mismatchedVariableIdentityAndOversizedCatalogNeverReachBrowser() throws Exception {
        ok(send("POST", "variables", "create", draft(), null), 201);
        risk.corruptWire(w -> w.put("variableCode", "UNRELATED_VARIABLE"));
        ok(send("GET", "variables/TEST_COLLECTION", "detail", null, null), 502);
        risk.corruptWire(w -> ((ObjectNode)w.at("/items/0")).put("variableName", "x".repeat(524288)));
        var oversized = send("GET", "variables?page=1&size=20", "list", null, null);
        ok(oversized, 502); assertTrue(oversized.body().length() < 2048);
        risk.corruptWire(w -> ((ObjectNode)w.at("/supportedPoints/0")).putNull("pointCode"));
        ok(send("GET", "authoring-context", "manage-context", null, null), 502);
    }

    @Test void ownerFieldErrorsKeepOnlySubmittedMemberPaths() throws Exception {
        risk.problem(422, "VALIDATION_ERROR");
        risk.problemFields(List.of(
            Map.of("field", "members[0].value", "message", RiskDecisionConfigurationSimulator.UPSTREAM_DETAIL_CANARY),
            Map.of("field", "members[0].value", "message", "duplicate"),
            Map.of("field", "members[2].value", "message", "out of bounds"),
            Map.of("field", "privateOwnerState", "message", "private")));
        var response = send("PUT", "variables/TEST_COLLECTION/members", "members-replace",
            "{\"expectedVersion\":\"1\",\"members\":[{\"value\":\"US\",\"displayName\":null}]}", null);
        ok(response, 422);
        var errors = JSON.readTree(response.body()).path("errors");
        assertEquals(1, errors.size()); assertEquals("members[0].value", errors.get(0).path("field").asString());
        assertFalse(response.body().contains(RiskDecisionConfigurationSimulator.UPSTREAM_DETAIL_CANARY));
        assertFalse(response.body().contains("privateOwnerState"));
    }

    private static void ok(HttpResponse<String> r, int status) { assertEquals(status, r.statusCode(), r.body()); }
    private static String draft() { return """
        {"variableCode":"TEST_COLLECTION","variableName":"集合","description":null,"sourceType":"COLLECTION","valueType":"STRING","pointCode":null,
         "sourceContractCode":null,"sourceContractVersion":null,"sourceField":null,"usages":["RULE_CONDITION","FLOW_CONDITION"],"formatCode":"TEXT",
         "numericUnit":null,"currencyCode":null,"currencyVariableCode":null,"allowedValuesVariableCode":null,"currentValue":null,"status":"ACTIVE"}
        """; }
    private HttpResponse<String> send(String method, String path, String route, String body, String key) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + ROOT + path)).timeout(Duration.ofSeconds(8));
        Map.ofEntries(Map.entry("Origin", "https://operations-console.local.test"), Map.entry("X-Request-Id", "rco19-browser"), Map.entry("X-Gateway-Realm", "OPERATIONS"), Map.entry("X-Gateway-Channel", "OPERATIONS"), Map.entry("X-Gateway-Actor-Type", "PLATFORM_USER"), Map.entry("X-Gateway-Actor-Subject", RISK_ACCOUNT_ID), Map.entry("X-Gateway-Session-Id", SESSION_ID), Map.entry("X-Gateway-Route-Code", "operations-risk-variable-" + route), Map.entry("X-Gateway-Context-Type", "TENANT"), Map.entry("X-Gateway-Tenant-Type", "INTERNAL"), Map.entry("X-Gateway-Tenant-Id", "tenant_10001"), Map.entry("X-Gateway-Tenant-Member-Id", "member_10001")).forEach(builder::header);
        if (body != null) builder.header("Content-Type", "application/json"); if (key != null) builder.header("Idempotency-Key", key);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try (var client = HttpClient.newHttpClient()) { return client.send(builder.build(), HttpResponse.BodyHandlers.ofString()); }
    }
}
