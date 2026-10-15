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

/** 从浏览器 HTTP 到授权及 Risk HTTP 的完整决策流配置边界。 */
class RiskFlowBrowserBoundaryAutoTest extends OperationsBffHttpTestBase {
    @Autowired private PlatformOperationsSimulator platform;
    @Autowired private RiskDecisionConfigurationSimulator risk;
    @LocalServerPort private int port;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ROOT = "/decision-flows/v1/";
    private static final String KEY = "RCO17_Command_123456";
    @BeforeEach void prepare() { platform.reset(); platform.useRiskIdentity(); risk.reset(); }
    @AfterEach void clean() {
        try { assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString()); assertTrue(risk.unexpectedRequests().isEmpty(), risk.unexpectedRequests().toString()); }
        finally { platform.reset(); risk.reset(); }
    }
    @Test void allTenOperationsForwardExactIdentityAndDeleteHasNoBody() throws Exception {
        success(send("GET", "authoring-context?pointCode=CHECKOUT_PREPARE", "authoring-context", null, null));
        success(send("POST", "flows/validate", "validate", draft(), null));
        success(send("POST", "flows", "save", draft(), KEY));
        success(send("GET", "flows?pointCode=CHECKOUT_PREPARE&page=1&size=10", "list", null, null));
        success(send("GET", "flows/50001", "detail", null, null));
        success(send("GET", "flows/50001/versions?maxVersionNo=1&page=1&size=10", "history", null, null));
        success(send("GET", "flows/50001/versions/60001", "version", null, null));
        success(send("GET", "rule-set-reference?sourceType=LEGACY_POINT&sourceVersionId=20001&nodeKey=binding_1", "rule-set-reference", null, null));
        success(send("GET", "legacy-point-versions/20001", "legacy-point", null, null));
        success(send("DELETE", "flows/50001?expectedVersion=1", "delete", null, KEY));
        assertEquals(10, risk.requests().size()); assertEquals(10, platform.completedAuthorizationCount());
        for (var call : risk.requests()) { assertEquals(RISK_ACCOUNT_ID, call.header("X-Gateway-Actor-Subject")); assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id")); assertEquals("rco17-browser", call.header("X-Request-Id")); }
        var deletion = risk.requests().getLast(); assertEquals("", deletion.body()); assertEquals("expectedVersion=1", deletion.query()); assertNull(deletion.header("Idempotency-Key"));
        assertTrue(risk.requests().stream().allMatch(call -> call.header("Idempotency-Key") == null));
    }
    @Test void flowRuleReferenceAcceptsExplicitNullLegacyPointButRejectsMissingKey() throws Exception {
        risk.corruptWire(w -> ((ObjectNode) w.get("ruleSet")).putNull("pointVersionId"));
        String path = "rule-set-reference?sourceType=FLOW_VERSION&sourceVersionId=60001&nodeKey=rules";
        var response = send("GET", path, "rule-set-reference", null, null);
        success(response);
        var ruleSet = JSON.readTree(response.body()).path("ruleSet");
        assertTrue(ruleSet.has("pointVersionId"));
        assertTrue(ruleSet.get("pointVersionId").isNull());
        risk.corruptWire(w -> ((ObjectNode) w.get("ruleSet")).remove("pointVersionId"));
        assertEquals(502, send("GET", path, "rule-set-reference", null, null).statusCode());
        assertEquals(2, risk.requests().size());
    }
    @Test void readAndSavePermissionsCannotDeleteOrBypassAuthorization() throws Exception {
        platform.grantPermissions("900000000000000536");
        success(send("GET", "authoring-context", "authoring-context", null, null)); int before = risk.requests().size();
        assertEquals(403, send("POST", "flows", "save", draft(), KEY).statusCode());
        assertEquals(403, send("POST", "flows/validate", "validate", draft(), null).statusCode());
        assertEquals(before, risk.requests().size());
        platform.grantPermissions("900000000000021101");
        assertEquals(403, send("DELETE", "flows/50001?expectedVersion=1", "delete", null, KEY).statusCode()); assertEquals(before, risk.requests().size());
    }
    @Test void malformedBodiesAndQueriesNeverReachOwner() throws Exception {
        var body = (ObjectNode) JSON.readTree(draft()); body.put("viewport", "private");
        assertEquals(400, send("POST", "flows", "save", JSON.writeValueAsString(body), KEY).statusCode());
        assertEquals(400, send("GET", "flows?pointCode=CHECKOUT_PREPARE&page=1&page=2", "list", null, null).statusCode());
        assertEquals(400, send("DELETE", "flows/50001?expectedVersion=1", "delete", "{}", KEY).statusCode());
        assertTrue(risk.requests().isEmpty());
    }
    @Test void validationIssueIsReturnedAndMalformedOwnerIsSafe() throws Exception {
        var body = (ObjectNode) JSON.readTree(draft()); ((ObjectNode)body.path("graph").path("nodes").get(1)).putNull("decisionCode");
        var validation = send("POST", "flows/validate", "validate", JSON.writeValueAsString(body), null); success(validation);
        assertFalse(JSON.readTree(validation.body()).path("valid").asBoolean());
        risk.corruptWire(w -> w.put("privateOwnerState", "canary"));
        var response = send("GET", "authoring-context", "authoring-context", null, null);
        assertEquals(502, response.statusCode(), response.body()); assertFalse(response.body().contains("canary")); assertEquals(2, risk.requests().size());
    }
    @Test void ownerProblemsRetainPublicCodeWithoutInternalDetailOrRetry() throws Exception {
        risk.problem(409, "RISK_FLOW_VERSION_CONFLICT");
        var response = send("POST", "flows", "save", draft(), KEY);
        assertEquals(409, response.statusCode(), response.body()); assertEquals("RISK_FLOW_VERSION_CONFLICT", JSON.readTree(response.body()).path("code").asString());
        assertFalse(response.body().contains(RiskDecisionConfigurationSimulator.UPSTREAM_DETAIL_CANARY)); assertEquals(1, risk.requests().size());
    }
    @Test void ownerFieldValidationIs422WithOnlyCurrentBodyPathsAndLocalMessages() throws Exception {
        risk.problem(422, "VALIDATION_ERROR");
        risk.problemFields(List.of(
            Map.of("field", "graph.nodes[1].decisionCode", "message", RiskDecisionConfigurationSimulator.UPSTREAM_DETAIL_CANARY),
            Map.of("field", "graph.nodes[1].decisionCode", "message", "duplicate"),
            Map.of("field", "graph.nodes[99].decisionCode", "message", "out of bounds"),
            Map.of("field", "privateOwnerState.secret", "message", "private"),
            Map.of("field", "graph.nodes[1].unknown", "message", "unknown")));
        var response = send("POST", "flows", "save", draft(), KEY);
        assertEquals(422, response.statusCode(), response.body());
        var problem = JSON.readTree(response.body());
        assertEquals("VALIDATION_ERROR", problem.path("code").asString());
        assertEquals("rco17-browser", problem.path("requestId").asString());
        assertEquals(1, problem.path("errors").size());
        assertEquals("graph.nodes[1].decisionCode", problem.path("errors").get(0).path("field").asString());
        assertFalse(response.body().contains(RiskDecisionConfigurationSimulator.UPSTREAM_DETAIL_CANARY));
        assertFalse(response.body().contains("privateOwnerState"));
        assertEquals(1, risk.requests().size());
    }

    @Test void allowEndOutputsRoundTripAndPaymentContextDeclaresThem() throws Exception {
        var checkout = JSON.readTree(send("GET", "authoring-context?pointCode=CHECKOUT_PREPARE", "authoring-context", null, null).body());
        assertFalse(checkout.has("outputs"));
        var payment = send("GET", "authoring-context?pointCode=PAYMENT_ATTEMPT_RISK", "authoring-context", null, null); success(payment);
        var declaration = JSON.readTree(payment.body()).path("outputs").get(0);
        assertEquals("AUTHENTICATION_REQUIREMENT", declaration.path("outputCode").asString());
        assertEquals("NOT_REQUIRED", declaration.path("defaultValue").asString());
        assertEquals(2, declaration.path("allowedValues").size());

        var body = (ObjectNode) JSON.readTree(draft()); body.put("pointCode", "PAYMENT_ATTEMPT_RISK");
        var end = (ObjectNode) body.path("graph").path("nodes").get(1); end.put("decisionCode", "ALLOW");
        end.putObject("outputs").put("AUTHENTICATION_REQUIREMENT", "THREE_DS_REQUIRED");
        success(send("POST", "flows", "save", JSON.writeValueAsString(body), null));
        assertEquals("THREE_DS_REQUIRED", JSON.readTree(risk.requests().getLast().body()).at("/graph/nodes/1/outputs/AUTHENTICATION_REQUIREMENT").asString());
        var detail = JSON.readTree(send("GET", "flows/50001", "detail", null, null).body());
        assertEquals("THREE_DS_REQUIRED", detail.at("/graph/nodes/1/outputs/AUTHENTICATION_REQUIREMENT").asString());
        assertFalse(detail.at("/graph/nodes/0").has("outputs"));

        end.putObject("outputs").put("authentication", "THREE_DS_REQUIRED");
        assertEquals(400, send("POST", "flows", "save", JSON.writeValueAsString(body), null).statusCode());
        end.putObject("outputs").put("AUTHENTICATION_REQUIREMENT", true);
        assertEquals(400, send("POST", "flows/validate", "validate", JSON.writeValueAsString(body), null).statusCode());
        risk.corruptWire(w -> ((ObjectNode) w.at("/graph/nodes/1")).putObject("outputs"));
        assertEquals(502, send("GET", "flows/50001", "detail", null, null).statusCode());
        assertEquals(5, risk.requests().size());
    }

    private static String draft() { return """
        {"pointCode":"CHECKOUT_PREPARE","flowCode":"FLOW","flowName":"流程","baseFlowVersionId":null,"expectedVersion":null,"changeReason":"初始配置","graph":{"schemaVersion":1,"nodes":[
        {"nodeKey":"start","type":"START","name":"开始","position":{"x":"0","y":"0"},"ruleSetVersionId":null,"decisionCode":null,"reasonCode":null,"reasonMessage":null},
        {"nodeKey":"end","type":"END","name":"结束","position":{"x":"300","y":"0"},"ruleSetVersionId":null,"decisionCode":"REVIEW","reasonCode":null,"reasonMessage":null}],
        "edges":[{"edgeKey":"next","sourceNodeKey":"start","targetNodeKey":"end","type":"UNCONDITIONAL","priority":0,"name":"继续","condition":null}]}}
        """; }
    private static void success(HttpResponse<String> r) { assertEquals(200, r.statusCode(), r.body()); }
    private HttpResponse<String> send(String method, String path, String route, String body, String key) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + ROOT + path)).timeout(Duration.ofSeconds(8));
        Map.ofEntries(Map.entry("Origin", "https://operations-console.local.test"), Map.entry("X-Request-Id", "rco17-browser"), Map.entry("X-Gateway-Realm", "OPERATIONS"), Map.entry("X-Gateway-Channel", "OPERATIONS"), Map.entry("X-Gateway-Actor-Type", "PLATFORM_USER"), Map.entry("X-Gateway-Actor-Subject", RISK_ACCOUNT_ID), Map.entry("X-Gateway-Session-Id", SESSION_ID), Map.entry("X-Gateway-Route-Code", "operations-risk-flow-" + route), Map.entry("X-Gateway-Context-Type", "TENANT"), Map.entry("X-Gateway-Tenant-Type", "INTERNAL"), Map.entry("X-Gateway-Tenant-Id", "tenant_10001"), Map.entry("X-Gateway-Tenant-Member-Id", "member_10001")).forEach(builder::header);
        if (body != null) builder.header("Content-Type", "application/json"); if (key != null) builder.header("Idempotency-Key", key);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try (var client = HttpClient.newHttpClient()) { return client.send(builder.build(), HttpResponse.BodyHandlers.ofString()); }
    }
}
