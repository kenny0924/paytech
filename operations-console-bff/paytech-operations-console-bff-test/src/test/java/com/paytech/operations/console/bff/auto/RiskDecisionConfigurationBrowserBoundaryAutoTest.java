package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.OperationsProblemChecker.assertProblem;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUTHORIZE_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RISK_ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator.HASH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.function.Consumer;

import com.paytech.foundation.web.request.RequestIdFilter;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.ConditionNodeRequestBody;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.CreateRuleSetVersionRequestBody;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.OperandRequestBody;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.RuleActionRequestBody;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.RuleRequestBody;
import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator;
import com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator.Mode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

/** 真实浏览器 HTTP 经 PLT-I02 后只转发一次到 Risk。 */
@ExtendWith(OutputCaptureExtension.class)
class RiskDecisionConfigurationBrowserBoundaryAutoTest extends OperationsBffHttpTestBase {
    private static final String ORIGIN = "https://operations-console.local.test";
    private static final String REQUEST_ID = "risk-browser-boundary-request";
    private static final String KEY = "Original_Risk-Key_123";
    private static final String RULES = "/decision-rule-sets/v1/rule-sets";
    private static final String CANDIDATES = "/decision-releases/v1/candidates";
    private static final String AUTHORING = "/decision-rule-sets/v1/authoring-context/CHECKOUT_PREPARE";
    private static final String HANDLER_CODE = "PRIVATE_HANDLER_CANARY";
    private static final String HANDLER_VERSION = "9.8.7-private-canary";
    private static final String HANDLER_HASH = "d".repeat(64);
    private static final String LITERAL_CANARY = "private-literal-browser-canary";
    private static final String SUMMARY_CANARY = "private-error-summary-browser-canary";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    @Autowired
    private PlatformOperationsSimulator platform;

    @Autowired
    private RiskDecisionConfigurationSimulator risk;

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        platform.reset();
        platform.useRiskIdentity();
        risk.reset();
    }

    @AfterEach
    void verifyAndReset() {
        List<?> unexpectedPlatformRequests = platform.unexpectedRequests();
        List<?> unexpectedRiskRequests = risk.unexpectedRequests();
        platform.reset();
        risk.reset();
        assertTrue(unexpectedPlatformRequests.isEmpty(), () -> "Platform 未准备请求: " + unexpectedPlatformRequests);
        assertTrue(unexpectedRiskRequests.isEmpty(), () -> "Risk 未准备请求: " + unexpectedRiskRequests);
        assertEquals(ACCOUNT_ID, platform.activeAccountId(), "Risk 身份必须由 reset 恢复为原 Operations fixture");
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void everyEndpointWaitsForCompletedAuthorizationBeforeSingleRiskCall(Endpoint endpoint) throws Exception {
        platform.blockAuthorizationResponse();
        CompletableFuture<HttpResponse<String>> responseFuture = sendAsync(endpoint.method(), endpoint.path(),
            body(endpoint), headers(endpoint));
        try {
            assertTrue(platform.awaitBlockedAuthorizationResponse(Duration.ofSeconds(2)),
                "Platform 必须在授权决定完成、任何响应字节写出前到达屏障");
            assertFalse(risk.awaitRequestArrival(Duration.ofMillis(250)),
                "Platform 授权响应尚未释放时不得向 Risk 发起请求");
            assertTrue(risk.requests().isEmpty(), "Platform 授权响应尚未释放时 Risk journal 必须为空");
            assertFalse(responseFuture.isDone(), "授权响应仍被屏障阻塞时浏览器请求不得完成");
        } finally {
            platform.releaseAuthorizationResponse();
        }

        HttpResponse<String> response = responseFuture.get(8, TimeUnit.SECONDS);

        assertEquals(200, response.statusCode(), response.body());
        assertOneOrderedCall(endpoint);
        ObjectNode expected = (ObjectNode) json.readTree(risk.responses().getFirst());
        if (endpoint.name().equals("authoring")) expected.path("actions").forEach(action ->
            ((ObjectNode) action).remove(List.of("handlerCode", "implementationVersion", "implementationHash")));
        assertEquals(expected, json.readTree(response.body()));
    }

    @Test
    void test_deniedAuthoringContextStopsBeforeRisk() throws Exception {
        platform.allow(false);

        HttpResponse<String> response = send("GET", "/decision-rule-sets/v1/authoring-context/CHECKOUT_PREPARE",
            null, trustedHeaders("operations-risk-rule-authoring-context"));

        assertProblem(json, response, 403, "OPERATIONS_PERMISSION_DENIED", REQUEST_ID);
        assertSingleAuthorization("operations-risk-rule-authoring-context");
        assertEquals(1, platform.completedAuthorizationCount());
        assertTrue(risk.requests().isEmpty());
    }

    @Test
    void test_saveForwardsExactTrustedContextKeyAndBodyOnce() throws Exception {
        CreateRuleSetVersionRequestBody request = saveRequest();
        HttpResponse<String> response = send("POST", RULES, json.writeValueAsString(request),
            commandHeaders("operations-risk-rule-set-save", KEY));

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(RISK_ACCOUNT_ID, json.readTree(response.body()).path("createdBy").asString());
        assertSingleAuthorization("operations-risk-rule-set-save");
        assertEquals(1, risk.requests().size());
        var call = risk.requests().getFirst();
        assertEquals("POST", call.method());
        assertEquals(RULES, call.path());
        assertEquals(RISK_ACCOUNT_ID, call.header("X-Gateway-Actor-Subject"));
        assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id"));
        assertEquals(REQUEST_ID, call.header(RequestIdFilter.HEADER_NAME));
        assertEquals(KEY, call.header("Idempotency-Key"));
        assertEquals(json.valueToTree(request), json.readTree(call.body()));
    }

    @Test
    void test_nondeterministicValidateKeyFailsAfterAuthorizationWithoutRisk() throws Exception {
        HttpResponse<String> response = send("POST", CANDIDATES + "/40001/validate", "{\"expectedVersion\":1}",
            commandHeaders("operations-risk-test-candidate-validate", "random_valid_key_12345"));

        assertProblem(json, response, 400, "INVALID_REQUEST", REQUEST_ID);
        assertSingleAuthorization("operations-risk-test-candidate-validate");
        assertEquals(1, platform.completedAuthorizationCount());
        assertTrue(risk.requests().isEmpty());
    }

    @Test
    void test_invalidProtectedGetOriginFailsAfterAuthorizationWithoutRisk() throws Exception {
        Map<String, String> headers = trustedHeaders("operations-risk-rule-authoring-context");
        headers.put("Origin", "https://attacker.example.test");

        HttpResponse<String> response = send("GET", "/decision-rule-sets/v1/authoring-context/CHECKOUT_PREPARE",
            null, headers);

        assertProblem(json, response, 403, "OPERATIONS_ORIGIN_REJECTED", REQUEST_ID);
        assertSingleAuthorization("operations-risk-rule-authoring-context");
        assertEquals(1, platform.completedAuthorizationCount());
        assertTrue(risk.requests().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "invalid", "non-json"})
    void test_invalidProtectedPostContentTypeFailsAfterAuthorizationWithoutRisk(String scenario) throws Exception {
        Map<String, String> headers = commandHeaders("operations-risk-rule-set-save", KEY);
        if (scenario.equals("missing")) headers.remove("Content-Type");
        if (scenario.equals("invalid")) headers.put("Content-Type", "not-a-media-type");
        if (scenario.equals("non-json")) headers.put("Content-Type", "text/plain");

        HttpResponse<String> response = send("POST", RULES, json.writeValueAsString(saveRequest()), headers);

        assertProblem(json, response, 415, "OPERATIONS_UNSUPPORTED_MEDIA_TYPE", REQUEST_ID);
        assertSingleAuthorization("operations-risk-rule-set-save");
        assertEquals(1, platform.completedAuthorizationCount());
        assertTrue(risk.requests().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void everyEndpointMapsEveryFieldAndJournalsOneOrderedCall(Endpoint endpoint, CapturedOutput output) throws Exception {
        if (endpoint.name().equals("authoring")) risk.corruptWire(wire -> wire.path("actions").forEach(action -> {
            ((ObjectNode) action).put("handlerCode", HANDLER_CODE);
            ((ObjectNode) action).put("implementationVersion", HANDLER_VERSION);
            ((ObjectNode) action).put("implementationHash", HANDLER_HASH);
        }));
        HttpResponse<String> response = call(endpoint);
        assertEquals(200, response.statusCode(), response.body());
        assertOneOrderedCall(endpoint);
        ObjectNode expected = (ObjectNode) json.readTree(risk.responses().getFirst());
        if (endpoint.name().equals("authoring")) {
            expected.path("actions").forEach(action -> ((ObjectNode) action).remove(List.of(
                "handlerCode", "implementationVersion", "implementationHash")));
            for (String hidden : List.of("handlerCode", "implementationVersion", "implementationHash", HANDLER_CODE,
                HANDLER_VERSION, HANDLER_HASH)) {
                assertFalse(response.body().contains(hidden), hidden);
                assertFalse(output.getAll().contains(hidden), hidden);
            }
        }
        assertEquals(expected, json.readTree(response.body()), "必须逐字段保留 Owner 响应（仅删私有 Handler）");
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void exactRiskPermissionDenialPrecedesAllLocalValidation(Endpoint endpoint) throws Exception {
        platform.grantPermissions();
        Map<String, String> headers = headers(endpoint);
        headers.put("Origin", "https://invalid-origin.example.test");
        if (endpoint.post()) {
            headers.put("Content-Type", "not-a-media-type");
            headers.put("Idempotency-Key", "short");
        }
        String path = endpoint.path().replace("/30001", "/0").replace("/40001", "/0")
            .replace("CHECKOUT_PREPARE", "invalid-point");
        HttpResponse<String> response = send(endpoint.method(), path + "?probe=x", endpoint.post() ? "{" : null, headers);
        assertLocalProblem(response, endpoint, 403, "OPERATIONS_PERMISSION_DENIED");
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void onlyExactPlatformPermissionGrantsEachRiskRoute(Endpoint endpoint) throws Exception {
        platform.grantPermissions(endpoint.permission());
        assertEquals(200, call(endpoint).statusCode());
        assertOneOrderedCall(endpoint);
    }

    @Test
    void successfulSaveLogsOnlyMetadata(CapturedOutput output) throws Exception {
        Endpoint save = endpoints().filter(Endpoint::post).findFirst().orElseThrow();
        ObjectNode body = (ObjectNode) json.readTree(body(save));
        ((ObjectNode) body.at("/rules/0/conditionNodes/1/operands/1")).put("literalValue", LITERAL_CANARY);
        HttpResponse<String> response = send("POST", RULES, json.writeValueAsString(body), headers(save));
        assertEquals(200, response.statusCode(), response.body());
        assertFalse(output.getAll().contains(LITERAL_CANARY), "Risk 请求 Literal 不得进入日志");
        assertFalse(output.getAll().contains(KEY), "Risk 幂等 Key 不得进入日志");
        assertFalse(output.getAll().contains(HASH), "Risk 完整 Content Hash 不得进入日志");
    }

    @ParameterizedTest
    @MethodSource("unknownQueries")
    void everyEndpointRejectsUnknownQueryAfterAuthorization(Endpoint endpoint, String query) throws Exception {
        HttpResponse<String> response = send(endpoint.method(), endpoint.path() + "?" + query, body(endpoint), headers(endpoint));
        assertLocalProblem(response, endpoint, 400, "INVALID_REQUEST");
    }

    private static Stream<Arguments> unknownQueries() {
        return endpoints().flatMap(endpoint -> Stream.of("probe=x", "Page=1").map(query -> Arguments.of(endpoint, query)));
    }

    @ParameterizedTest
    @MethodSource("listQueries")
    void listAllowsOnlyExactFiltersAndForwardsRawQueryOnce(Endpoint endpoint, String query, String forwarded) throws Exception {
        HttpResponse<String> response = send("GET", endpoint.path() + (query.isEmpty() ? "" : "?" + query), null, headers(endpoint));
        assertEquals(200, response.statusCode(), response.body());
        assertSingleAuthorization(endpoint.route());
        assertEquals(1, risk.requests().size());
        var request = risk.requests().getFirst();
        assertEquals(forwarded, request.query());
        assertEquals(endpoint.path(), request.path());
        assertEquals("GET", request.method());
        assertEquals(RISK_ACCOUNT_ID, request.header("X-Gateway-Actor-Subject"));
        assertEquals(SESSION_ID, request.header("X-Gateway-Session-Id"));
        assertEquals(REQUEST_ID, request.header("X-Request-Id"));
        assertTrue(platform.requests().getFirst().sequence() < request.sequence());
        assertEquals(json.readTree(risk.responses().getFirst()), json.readTree(response.body()));
        if (forwarded.startsWith("page=1&")) assertFalse(json.readTree(response.body()).path("items").isEmpty(),
            "允许组合必须命中 fixture，不能用空列表掩盖过滤关联错误");
    }

    private static Stream<Arguments> listQueries() {
        return endpoints().filter(e -> e.name().equals("rules") || e.name().equals("candidates")).flatMap(endpoint -> {
            List<Arguments> cases = new ArrayList<>();
            cases.add(Arguments.of(endpoint, "", "page=1&size=10"));
            cases.add(Arguments.of(endpoint, "page=3&size=20", "page=3&size=20"));
            List<String> filters = endpoint.name().equals("rules")
                ? List.of("pointCode=CHECKOUT_PREPARE", "ruleSetCode=DEFAULT_RULES", "status=FROZEN")
                : List.of("pointCode=CHECKOUT_PREPARE", "status=DRAFT");
            // 每个非空子集都真正命中至少一个 fixture；禁止只测空结果掩盖关联验证。
            for (int mask = 1; mask < 1 << filters.size(); mask++) {
                List<String> selected = new ArrayList<>();
                for (int i = 0; i < filters.size(); i++) if ((mask & (1 << i)) != 0) selected.add(filters.get(i));
                String query = String.join("&", selected);
                cases.add(Arguments.of(endpoint, query, "page=1&size=10&" + query));
                cases.add(Arguments.of(endpoint, "page=1&size=50&" + query, "page=1&size=50&" + query));
            }
            return cases.stream();
        });
    }

    @ParameterizedTest
    @MethodSource("posts")
    void eachPostStrictlyRejectsEveryDecodingClassBeforeRisk(Endpoint endpoint) throws Exception {
        String valid = body(endpoint);
        ObjectNode tree = (ObjectNode) json.readTree(valid);
        String field = tree.propertyNames().iterator().next();
        ObjectNode unknown = tree.deepCopy().put("privateUnknownCanary", "privateUnknownValueCanary");
        ObjectNode missing = tree.deepCopy(); missing.remove(field);
        ObjectNode wrongToken = tree.deepCopy(); wrongToken.set(field, json.createArrayNode());
        List<String> rejected = List.of("{", valid.substring(0, valid.length() - 1) + ",\"" + field + "\":"
            + json.writeValueAsString(tree.get(field)) + "}", valid + " {}", json.writeValueAsString(unknown),
            json.writeValueAsString(missing), json.writeValueAsString(wrongToken), "null", "[]", "true", "\"object\"", "");
        assertEquals(11, rejected.size());
        for (String payload : rejected) {
            resetScenario();
            HttpResponse<String> response = send(endpoint.method(), endpoint.path(), payload, headers(endpoint));
            assertLocalProblem(response, endpoint, 400, "INVALID_REQUEST");
            assertFalse(response.body().contains("privateUnknown"));
        }
    }

    @ParameterizedTest
    @MethodSource("posts")
    void everyDeclaredBodyMemberIsRequiredAndNestedObjectsAreClosed(Endpoint endpoint) throws Exception {
        ObjectNode original = (ObjectNode) json.readTree(body(endpoint));
        List<String> objects = new ArrayList<>();
        collectObjects(original, "", objects);
        assertFalse(objects.isEmpty());
        int mutations = 0;
        for (String pointer : objects) {
            ObjectNode object = (ObjectNode) original.at(pointer);
            List<String> members = new ArrayList<>(object.propertyNames());
            assertFalse(members.isEmpty());
            for (String member : members) {
                // 旧重放可省略新增的空槽位；新格式完整性由变量操作数合同用例覆盖。
                if (java.util.Set.of("operandFormat", "variableCode", "numericUnit", "currencyCode", "currencyVariableCode", "subjectVariableCode").contains(member)
                    && object.get(member).isNull()) continue;
                resetScenario();
                ObjectNode request = original.deepCopy();
                ((ObjectNode) request.at(pointer)).remove(member);
                HttpResponse<String> response = send(endpoint.method(), endpoint.path(), json.writeValueAsString(request), headers(endpoint));
                assertLocalProblem(response, endpoint, 400, "INVALID_REQUEST");
                mutations++;
            }
            resetScenario();
            ObjectNode request = original.deepCopy();
            ((ObjectNode) request.at(pointer)).put("privateUnknownCanary", "privateUnknownValueCanary");
            assertLocalProblem(send(endpoint.method(), endpoint.path(), json.writeValueAsString(request), headers(endpoint)),
                endpoint, 400, "INVALID_REQUEST");
            mutations++;
        }
        assertTrue(mutations > 1);
    }

    private void collectObjects(JsonNode node, String pointer, List<String> objects) {
        if (node instanceof ObjectNode object) {
            objects.add(pointer);
            object.properties().forEach(entry -> collectObjects(entry.getValue(), pointer + "/" + entry.getKey(), objects));
        } else if (node instanceof ArrayNode array) {
            for (int index = 0; index < array.size(); index++) collectObjects(array.get(index), pointer + "/" + index, objects);
        }
    }

    @ParameterizedTest
    @MethodSource("posts")
    void eachPostBeanConstraintsHaveOnlySafeExternalFieldPaths(Endpoint endpoint) throws Exception {
        List<Constraint> cases = constraints(endpoint);
        assertFalse(cases.isEmpty());
        for (Constraint constraint : cases) {
            resetScenario();
            ObjectNode request = (ObjectNode) json.readTree(body(endpoint));
            setAt(request, constraint.path(), constraint.value());
            HttpResponse<String> response = send(endpoint.method(), endpoint.path(), json.writeValueAsString(request), headers(endpoint));
            assertLocalProblem(response, endpoint, 422, "VALIDATION_ERROR");
            JsonNode problem = json.readTree(response.body());
            assertTrue(problem.has("errors"), constraint.toString());
            assertTrue(problem.path("errors").valueStream().anyMatch(error ->
                error.path("field").asString().equals(constraint.externalField())), constraint.toString() + response.body());
            for (String hidden : List.of("RequestBody", "createRule", "freezeRule", "validateCandidate", "arg0",
                "com.paytech", "private-invalid-canary")) assertFalse(response.body().contains(hidden), hidden);
        }
    }

    private List<Constraint> constraints(Endpoint endpoint) {
        List<Constraint> cases = new ArrayList<>();
        switch (endpoint.name()) {
            case "save" -> {
                for (String field : List.of("pointCode", "ruleSetCode", "ruleSetName", "changeReason", "rules"))
                    cases.add(new Constraint("/" + field, null, field));
                cases.add(new Constraint("/pointCode", " ", "pointCode"));
                cases.add(new Constraint("/pointCode", "private-invalid-canary", "pointCode"));
                cases.add(new Constraint("/ruleSetName", "x".repeat(256), "ruleSetName"));
                cases.add(new Constraint("/changeReason", "x".repeat(501), "changeReason"));
                cases.add(new Constraint("/baseRuleSetVersionId", "0", "baseRuleSetVersionId"));
                cases.add(new Constraint("/baseContentHash", "A".repeat(64), "baseContentHash"));
                cases.add(new Constraint("/rules", List.of(), "rules"));
                cases.add(new Constraint("/rules/0", null, "rules[0]"));
                cases.add(new Constraint("/rules/0/priorityNo", 0, "rules[0].priorityNo"));
                cases.add(new Constraint("/rules/0/enabledFlag", null, "rules[0].enabledFlag"));
                cases.add(new Constraint("/rules/0/action/actionVersionNo", 0, "rules[0].action.actionVersionNo"));
                cases.add(new Constraint("/rules/0/conditionNodes", List.of(), "rules[0].conditionNodes"));
                cases.add(new Constraint("/rules/0/conditionNodes/0/nodeKey", " ", "rules[0].conditionNodes[0].nodeKey"));
                cases.add(new Constraint("/rules/0/conditionNodes/0/sequenceNo", 0, "rules[0].conditionNodes[0].sequenceNo"));
                cases.add(new Constraint("/rules/0/conditionNodes/1/operands/0/valueSequence", 0,
                    "rules[0].conditionNodes[1].operands[0].valueSequence"));
                cases.add(new Constraint("/rules/0/conditionNodes/1/operands/0/fieldCode", "Bad_field",
                    "rules[0].conditionNodes[1].operands[0].fieldCode"));
                cases.add(new Constraint("/rules/0/conditionNodes/1/operands/0", null,
                    "rules[0].conditionNodes[1].operands[0]"));
                cases.add(new Constraint("/rules", java.util.Collections.nCopies(201,
                    json.readTree(body(endpoint)).path("rules").get(0)), "rules"));
            }
            case "freeze" -> {
                for (Object value : java.util.Arrays.asList(null, "", " ", "A".repeat(64), "a".repeat(63)))
                    cases.add(new Constraint("/expectedContentHash", value, "expectedContentHash"));
            }
            case "create" -> {
                for (Object value : java.util.Arrays.asList(null, "", "0", "01", "1".repeat(20)))
                    cases.add(new Constraint("/ruleSetVersionId", value, "ruleSetVersionId"));
                for (Object value : java.util.Arrays.asList(null, "", "A".repeat(64), "a".repeat(63)))
                    cases.add(new Constraint("/expectedRuleSetContentHash", value, "expectedRuleSetContentHash"));
            }
            case "delete" -> {
                for (Object value : java.util.Arrays.asList(null, "0", "01", "-1", "9223372036854775808"))
                    cases.add(new Constraint("/expectedVersion", value, "expectedVersion"));
            }
            case "validate" -> {
                for (Object value : java.util.Arrays.asList(null, 0, -1))
                    cases.add(new Constraint("/expectedVersion", value, "expectedVersion"));
            }
            default -> throw new AssertionError("not a command");
        }
        return cases;
    }

    @ParameterizedTest
    @MethodSource("posts")
    void eachPostRejectsProtectedProtocolErrorsOnlyAfterPlt(Endpoint endpoint) throws Exception {
        for (String scenario : List.of("origin", "missing", "invalid", "non-json", "short-key", "format-key", "long-key")) {
            resetScenario();
            Map<String, String> headers = headers(endpoint);
            if (scenario.equals("origin")) headers.put("Origin", "https://invalid.example.test");
            if (scenario.equals("missing")) headers.remove("Content-Type");
            if (scenario.equals("invalid")) headers.put("Content-Type", "not-a-media-type");
            if (scenario.equals("non-json")) headers.put("Content-Type", "text/plain");
            if (scenario.equals("short-key")) headers.put("Idempotency-Key", "short");
            if (scenario.equals("format-key")) headers.put("Idempotency-Key", "bad key with spaces");
            if (scenario.equals("long-key")) headers.put("Idempotency-Key", "x".repeat(129));
            HttpResponse<String> response = send(endpoint.method(), endpoint.path(), body(endpoint), headers);
            boolean key = scenario.endsWith("key");
            assertLocalProblem(response, endpoint, key ? 400 : scenario.equals("origin") ? 403 : 415,
                key ? "INVALID_REQUEST" : scenario.equals("origin") ? "OPERATIONS_ORIGIN_REJECTED" : "OPERATIONS_UNSUPPORTED_MEDIA_TYPE");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"random_valid_key_12345", "risk-validate-v1-40002-1", "risk-validate-v1-40001-2"})
    void validateRejectsEveryDeterministicKeyMismatch(String key) throws Exception {
        Endpoint endpoint = endpoint("validate");
        HttpResponse<String> response = send(endpoint.method(), endpoint.path(), body(endpoint), commandHeaders(endpoint.route(), key));
        assertLocalProblem(response, endpoint, 400, "INVALID_REQUEST");
    }

    @Test
    void duplicateAstNodeKeysRemainAuthoritativeRiskRejection() throws Exception {
        Endpoint endpoint = endpoint("save");
        ObjectNode request = (ObjectNode) json.readTree(body(endpoint));
        setAt(request, "/rules/0/conditionNodes/2/nodeKey", "9");
        HttpResponse<String> response = send(endpoint.method(), endpoint.path(), json.writeValueAsString(request), headers(endpoint));
        assertProblem(json, response, 400, "BUSINESS_CHECK_FAILED", REQUEST_ID);
        assertSingleAuthorization(endpoint.route());
        assertEquals(1, risk.requests().size());
        assertEquals(request, json.readTree(risk.requests().getFirst().body()));
    }

    @ParameterizedTest
    @MethodSource("invalidPathsAndFilters")
    void invalidPathAndFilterConstraintsRemainSafe400AfterPlt(Endpoint endpoint, String path) throws Exception {
        HttpResponse<String> response = send(endpoint.method(), path, body(endpoint), headers(endpoint));
        assertLocalProblem(response, endpoint, 400, "INVALID_REQUEST");
    }

    private static Stream<Arguments> invalidPathsAndFilters() {
        List<Arguments> cases = new ArrayList<>();
        for (String point : List.of("invalid", "_INVALID", "A".repeat(65)))
            cases.add(Arguments.of(endpoint("authoring"), AUTHORING.replace("CHECKOUT_PREPARE", point)));
        for (String name : List.of("rule", "freeze", "candidate", "validate")) for (String id : List.of("0", "01", "x", "1".repeat(20))) {
            Endpoint endpoint = endpoint(name);
            cases.add(Arguments.of(endpoint, endpoint.path().replace("30001", id).replace("40001", id)));
        }
        for (String name : List.of("rules", "candidates")) for (String query : List.of("pointCode=invalid", "status=UNKNOWN",
            "page=0", "page=-1", "page=100001", "page=x", "page=1.0", "size=11", "size=0", "size=201"))
            cases.add(Arguments.of(endpoint(name), endpoint(name).path() + "?" + query));
        cases.add(Arguments.of(endpoint("rules"), RULES + "?ruleSetCode=invalid"));
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("responseFaults")
    void malformedSuccessfulFactsFailClosedWithoutLeakingValues(ResponseFault fault, CapturedOutput output) throws Exception {
        prepareFault(fault);
        HttpResponse<String> control = call(fault.endpoint());
        assertEquals(200, control.statusCode(), "反例必须先有可用的成功控制组: " + fault.name());
        String baseline = risk.responses().getFirst();
        resetScenario(); prepareFault(fault);
        risk.corruptWire(fault.mutation());
        HttpResponse<String> response = call(fault.endpoint());
        assertUpstreamInvalid(response, fault.endpoint(), output);
        assertEquals(1, risk.responses().size());
        assertFalse(baseline.equals(risk.responses().getFirst()), "故障必须实际改变 Wire: " + fault.name());
    }

    private void prepareFault(ResponseFault fault) {
        if (fault.name().startsWith("page-")) { risk.addRuleSet("9", "FROZEN"); risk.addRuleSet("10", "FROZEN"); }
        if (fault.name().startsWith("rejected-")) risk.candidateState("REJECTED");
    }

    private static Stream<ResponseFault> responseFaults() {
        List<ResponseFault> cases = new ArrayList<>();
        for (String invalidId : List.of("0", "01", "-1", "1".repeat(20)))
            cases.add(fault("invalid-id-" + invalidId.length(), "rule", root -> root.put("ruleSetVersionId", invalidId)));
        cases.add(fault("code", "rule", root -> root.put("ruleSetCode", "lower_code")));
        cases.add(fault("enum", "rule", root -> root.put("status", "UNKNOWN")));
        cases.add(fault("hash", "rule", root -> root.put("contentHash", "A".repeat(64))));
        cases.add(fault("time-precision", "rule", root -> root.put("createTime", "2026-09-07T01:02:03Z")));
        cases.add(fault("time-calendar", "rule", root -> root.put("createTime", "2026-02-30T01:02:03.123456Z")));
        cases.add(fault("positive-version", "rule", root -> root.put("versionNo", 0)));
        cases.add(fault("null-root-field", "rule", root -> root.putNull("contentHash")));
        cases.add(fault("null-element", "rule", root -> ((ArrayNode) root.path("rules")).set(0, root.nullNode())));
        cases.add(fault("frozen-state", "rule", root -> root.putNull("frozenBy")));
        cases.add(fault("wire-preorder", "rule", root -> reverse((ArrayNode) root.at("/rules/0/conditionNodes"))));
        cases.add(fault("operand-order", "rule", root -> reverse((ArrayNode) root.at("/rules/0/conditionNodes/1/operands"))));
        cases.add(fault("duplicate-node", "rule", root -> ((ObjectNode) root.at("/rules/0/conditionNodes/2")).put("nodeKey", "9")));
        cases.add(fault("page-window", "rules", root -> root.put("total", 2)));
        cases.add(fault("page-number", "rules", root -> root.put("page", 2)));
        cases.add(fault("page-size", "rules", root -> root.put("size", 20)));
        cases.add(fault("page-total", "rules", root -> root.put("total", -1)));
        cases.add(fault("page-duplicate", "rules", root -> ((ArrayNode) root.path("items")).set(1, root.path("items").get(0))));
        cases.add(fault("page-numeric-order", "rules", root -> reverse((ArrayNode) root.path("items"))));
        cases.add(fault("candidate-draft-matrix", "candidate", root -> root.put("waitingForShadow", true)));
        cases.add(fault("candidate-state-actor", "candidate", root -> ((ObjectNode) root.at("/stateChanges/0")).put("actorId", "0")));
        cases.add(fault("rejected-check-order", "candidate", root -> reverse((ArrayNode) root.path("staticChecks"))));
        cases.add(fault("rejected-check-duplicate", "candidate", root -> ((ArrayNode) root.path("staticChecks")).set(1,
            root.path("staticChecks").get(0))));
        cases.add(fault("rejected-summary", "candidate", root -> ((ObjectNode) root.at("/staticChecks/2")).put("errorSummary", SUMMARY_CANARY)));
        cases.add(fault("authoring-variable-duplicate", "authoring", root -> ((ObjectNode)root.at("/fields/1")).put("variableCode", root.at("/fields/0/variableCode").asString())));
        cases.add(fault("authoring-private-hash", "authoring", root -> ((ObjectNode) root.at("/actions/0")).put("implementationHash", "x")));
        cases.add(fault("authoring-private-code", "authoring", root -> ((ObjectNode) root.at("/actions/1")).put("handlerCode", "invalid")));
        cases.add(fault("authoring-private-version", "authoring", root -> ((ObjectNode) root.at("/actions/2")).put("implementationVersion", " ")));
        cases.add(fault("authoring-reserved", "authoring", root -> ((ArrayNode) root.path("features")).add("private-reserved-canary")));
        return cases.stream();
    }

    private static ResponseFault fault(String name, String endpoint, Consumer<ObjectNode> mutation) {
        return new ResponseFault(name, endpoint(endpoint), mutation);
    }
    private static void reverse(ArrayNode array) {
        assertTrue(array.size() > 1, "有序反例至少需要两个不同成员");
        for (int left = 0, right = array.size() - 1; left < right; left++, right--) {
            JsonNode old = array.get(left); array.set(left, array.get(right)); array.set(right, old);
        }
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void everyEndpointRejectsValidShapeButDifferentCorrelation(Endpoint endpoint, CapturedOutput output) throws Exception {
        assertEquals(200, call(endpoint).statusCode());
        resetScenario();
        String path = endpoint.path();
        switch (endpoint.name()) {
            case "authoring" -> path = AUTHORING.replace("CHECKOUT_PREPARE", "OTHER_POINT");
            case "rules" -> {
                path += "?pointCode=CHECKOUT_PREPARE&ruleSetCode=DEFAULT_RULES&status=FROZEN";
                risk.corruptWire(root -> ((ObjectNode) root.at("/items/0")).put("ruleSetCode", "OTHER_RULES"));
            }
            case "rule" -> risk.corruptWire(root -> root.put("ruleSetVersionId", "30002"));
            case "save" -> risk.corruptWire(root -> root.put("ruleSetCode", "OTHER_RULES"));
            case "freeze" -> risk.corruptWire(root -> root.put("contentHash", "b".repeat(64)));
            case "delete" -> risk.corruptWire(root -> root.put("ruleSetId", "90002"));
            case "candidates" -> {
                path += "?pointCode=CHECKOUT_PREPARE&status=DRAFT";
                risk.corruptWire(root -> ((ObjectNode) root.at("/items/0")).put("pointCode", "OTHER_POINT"));
            }
            case "candidate" -> risk.corruptWire(root -> root.put("releaseId", "40002"));
            case "create" -> risk.corruptWire(root -> root.put("ruleSetVersionId", "30002"));
            case "validate" -> risk.corruptWire(root -> root.put("releaseId", "40002"));
            default -> throw new AssertionError(endpoint.name());
        }
        HttpResponse<String> response = send(endpoint.method(), path, body(endpoint), headers(endpoint));
        assertUpstreamInvalid(response, endpoint, output);
    }

    @ParameterizedTest
    @MethodSource("additionalCorrelations")
    void everyRemainingCorrelationRejectsIndependently(ResponseFault fault, CapturedOutput output) throws Exception {
        String path = fault.endpoint().path();
        if (fault.endpoint().name().equals("rules")) path += "?pointCode=CHECKOUT_PREPARE&ruleSetCode=DEFAULT_RULES&status=FROZEN";
        if (fault.endpoint().name().equals("candidates")) path += "?pointCode=CHECKOUT_PREPARE&status=DRAFT";
        HttpResponse<String> control = send(fault.endpoint().method(), path, body(fault.endpoint()), headers(fault.endpoint()));
        assertEquals(200, control.statusCode());
        resetScenario(); risk.corruptWire(fault.mutation());
        HttpResponse<String> response = send(fault.endpoint().method(), path, body(fault.endpoint()), headers(fault.endpoint()));
        assertUpstreamInvalid(response, fault.endpoint(), output);
    }

    private static Stream<ResponseFault> additionalCorrelations() {
        List<ResponseFault> cases = new ArrayList<>();
        cases.add(fault("rule-point-filter", "rules", root -> ((ObjectNode) root.at("/items/0")).put("pointCode", "OTHER_POINT")));
        cases.add(fault("rule-status-filter", "rules", root -> {
            ObjectNode item = (ObjectNode) root.at("/items/0");
            item.put("status", "DRAFT").putNull("frozenBy").putNull("frozenTime");
        }));
        cases.add(fault("save-status", "save", root -> root.put("status", "FROZEN")));
        cases.add(fault("freeze-id", "freeze", root -> root.put("ruleSetVersionId", "30002")));
        cases.add(fault("freeze-status", "freeze", root -> root.put("status", "DRAFT")));
        cases.add(fault("candidate-status-filter", "candidates", root ->
            ((ObjectNode) root.at("/items/0")).put("status", "VALIDATING").put("version", 2)));
        for (String field : List.of("decisionPointId", "pointCode", "pointVersionId", "ruleSetVersionId"))
            cases.add(fault("frozen-" + field, "candidate", root -> ((ObjectNode) root.path("frozenComposition"))
                .put(field, field.equals("pointCode") ? "OTHER_POINT" : "99999")));
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("staleValidationVersions")
    void validateMustCorrelateExpectedVersionSuccessor(boolean rejected, int expectedVersion, CapturedOutput output) throws Exception {
        Endpoint endpoint = endpoint("validate");
        risk.validationFails(rejected);
        assertEquals(200, call(endpoint).statusCode());
        JsonNode validStateMatrix = json.readTree(risk.responses().getFirst());
        resetScenario(); risk.validationFails(rejected); risk.staleCommandReplay();
        HttpResponse<String> response = send(endpoint.method(), endpoint.path(), "{\"expectedVersion\":" + expectedVersion + "}",
            commandHeaders(endpoint.route(), "risk-validate-v1-40001-" + expectedVersion));
        assertUpstreamInvalid(response, endpoint, output);
        assertEquals(validStateMatrix, json.readTree(risk.responses().getFirst()),
            "响应本身必须满足状态矩阵，只有与当前 expectedVersion 的 successor 不匹配");
    }

    private static Stream<Arguments> staleValidationVersions() {
        return Stream.of(false, true).flatMap(rejected -> Stream.of(2, Integer.MAX_VALUE)
            .map(version -> Arguments.of(rejected, version)));
    }

    @ParameterizedTest
    @ValueSource(strings = {SUMMARY_CANARY, "first=AST_TYPE_INVALID@nodes/09; violation_count=1",
        "first=AST_TYPE_INVALID@nodes/9; violation_count=01", "first=OTHER_ERROR@nodes/9; violation_count=1",
        "first=AST_TYPE_INVALID@nodes/9; violation_count=1 ", "first=AST_TYPE_INVALID@nodes/9; violation_count=0"})
    void unsafeAndNoncanonicalFailedSummariesNeverReachBrowserOrLogs(String summary, CapturedOutput output) throws Exception {
        Endpoint endpoint = endpoint("validate");
        risk.validationFails(true);
        risk.corruptWire(root -> ((ObjectNode) root.at("/checks/2")).put("errorSummary", summary));
        assertUpstreamInvalid(call(endpoint), endpoint, output);
        assertFalse(output.getAll().contains(summary));
    }

    @Test
    void rejectedWithEightCanonicalChecksIsNormal200() throws Exception {
        risk.validationFails(true);
        Endpoint endpoint = endpoint("validate");
        HttpResponse<String> response = call(endpoint);
        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertEquals("REJECTED", body.path("status").asString());
        assertEquals(3, body.path("version").asInt());
        assertEquals(8, body.path("checks").size());
        assertEquals(RiskDecisionConfigurationSimulator.CHECKS,
            body.path("checks").valueStream().map(check -> check.path("checkCode").asString()).toList());
        assertEquals(json.readTree(risk.responses().getFirst()), body);
        assertOneOrderedCall(endpoint);
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void everyUpstreamProblemIsRebuiltWithBrowserRequestIdentity(Endpoint endpoint, CapturedOutput output) throws Exception {
        risk.problem(404, "RISK_DECISION_CONFIGURATION_NOT_FOUND");
        HttpResponse<String> response = call(endpoint);
        assertProblem(json, response, 404, "RISK_DECISION_CONFIGURATION_NOT_FOUND", REQUEST_ID);
        assertOneOrderedCall(endpoint);
        assertNoUpstreamSecrets(response, output);
    }

    @ParameterizedTest
    @MethodSource("upstreamProblems")
    void upstreamRfc9457KeepsOnlyApprovedErrorCode(int status, String code, CapturedOutput output) throws Exception {
        risk.problem(status, code);
        Endpoint endpoint = endpoint("authoring");
        HttpResponse<String> response = call(endpoint);
        assertProblem(json, response, status, status == 503 ? "DEPENDENCY_UNAVAILABLE" : code, REQUEST_ID);
        assertOneOrderedCall(endpoint);
        assertNoUpstreamSecrets(response, output);
    }

    private static Stream<Arguments> upstreamProblems() {
        return Stream.of(Arguments.of(400, "BUSINESS_CHECK_FAILED"), Arguments.of(409, "CONCURRENT_REQUEST"),
            Arguments.of(503, "SERVICE_UNAVAILABLE"));
    }

    @Test
    void legacyFourFieldErrorIsAnInvalidDependencyResponse(CapturedOutput output) throws Exception {
        risk.legacyError(400, "BUSINESS_CHECK_FAILED");
        Endpoint endpoint = endpoint("save");
        assertUpstreamInvalid(call(endpoint), endpoint, output);
    }

    @ParameterizedTest
    @EnumSource(value = Mode.class, names = {"MALFORMED_SUCCESS", "REDIRECT", "TIMEOUT", "DISCONNECT"})
    void everyPostTransportFailureCallsRiskExactlyOnceWithoutRetry(Mode mode, CapturedOutput output) throws Exception {
        for (Endpoint endpoint : posts().toList()) {
            resetScenario(); risk.mode(mode);
            HttpResponse<String> response = call(endpoint);
            int status = mode == Mode.TIMEOUT ? 504 : mode == Mode.DISCONNECT ? 503 : 502;
            String code = mode == Mode.TIMEOUT ? "DEPENDENCY_TIMEOUT" : mode == Mode.DISCONNECT
                ? "DEPENDENCY_UNAVAILABLE" : "UPSTREAM_INVALID_RESPONSE";
            assertProblem(json, response, status, code, REQUEST_ID);
            assertOneOrderedCall(endpoint);
            assertNoUpstreamSecrets(response, output);
            assertFalse(response.body().contains(risk.baseUrl()));
        }
    }

    @Test
    void validateTimeoutIsNotRetriedAndDetailPreservesRecoveryActorFacts() throws Exception {
        Endpoint validate = endpoint("validate");
        risk.timeoutAfterValidationStarted();
        HttpResponse<String> timeout = call(validate);
        assertProblem(json, timeout, 504, "DEPENDENCY_TIMEOUT", REQUEST_ID);
        assertOneOrderedCall(validate);
        risk.mode(Mode.SUCCESS);
        Endpoint detail = endpoint("candidate");
        List<JsonNode> bodies = new ArrayList<>();
        for (String actor : List.of("10002", "10003")) {
            platform.useRiskIdentity(actor);
            Map<String, String> headers = headers(detail); headers.put("X-Gateway-Actor-Subject", actor);
            HttpResponse<String> response = send("GET", detail.path(), null, headers);
            assertEquals(200, response.statusCode(), response.body());
            JsonNode body = json.readTree(response.body()); bodies.add(body);
            assertEquals("VALIDATING", body.path("status").asString());
            assertEquals(2, body.path("version").asInt());
            assertTrue(body.path("allStaticChecksPassed").isNull());
            assertFalse(body.path("waitingForShadow").asBoolean());
            assertTrue(body.path("staticChecks").isEmpty());
            assertEquals(2, body.path("stateChanges").size());
            assertEquals(RISK_ACCOUNT_ID, body.path("createdBy").asString());
            body.path("stateChanges").forEach(change -> assertEquals(RISK_ACCOUNT_ID, change.path("actorId").asString()));
            assertEquals(json.readTree(risk.responses().getLast()), body);
            assertEquals(actor, risk.requests().getLast().header("X-Gateway-Actor-Subject"));
        }
        assertEquals(bodies.getFirst(), bodies.getLast(), "不同当前调用者不能改变 Owner 的原始恢复坐标");
        assertEquals(3, risk.requests().size());
        assertEquals(1, risk.requests().stream().filter(call -> call.method().equals("POST")).count());
        assertEquals(3, platform.requests().size());
        assertEquals(3, platform.completedAuthorizationCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "denial", "timeout", "malformed"})
    void logsContainMetadataButNeverRiskPayloadCanaries(String scenario, CapturedOutput output) throws Exception {
        Endpoint endpoint = endpoint("save");
        ObjectNode request = (ObjectNode) json.readTree(body(endpoint));
        setAt(request, "/rules/0/conditionNodes/1/operands/1/literalValue", LITERAL_CANARY);
        if (scenario.equals("denial")) platform.allow(false);
        if (scenario.equals("timeout")) risk.mode(Mode.TIMEOUT);
        if (scenario.equals("malformed")) risk.mode(Mode.MALFORMED_SUCCESS);
        HttpResponse<String> response = send(endpoint.method(), endpoint.path(), json.writeValueAsString(request), headers(endpoint));
        assertEquals(switch (scenario) { case "success" -> 200; case "denial" -> 403; case "timeout" -> 504; default -> 502; },
            response.statusCode(), response.body());
        assertSingleAuthorization(endpoint.route());
        assertEquals(scenario.equals("denial") ? 0 : 1, risk.requests().size());
        for (String canary : List.of(LITERAL_CANARY, SUMMARY_CANARY, HANDLER_CODE, HANDLER_VERSION, HANDLER_HASH, HASH, KEY)) {
            assertFalse(output.getAll().contains(canary), "不得记录合成敏感 Canary");
            if (!scenario.equals("success")) assertFalse(response.body().contains(canary));
        }
        assertTrue(output.getAll().contains(REQUEST_ID), "必须实际捕获本请求的日志，不能空日志通过");
        assertTrue(output.getAll().contains("HTTP request completed"));
    }

    private void assertUpstreamInvalid(HttpResponse<String> response, Endpoint endpoint, CapturedOutput output) {
        var problem = assertProblem(json, response, 502, "UPSTREAM_INVALID_RESPONSE", REQUEST_ID);
        assertEquals("依赖服务响应异常，请稍后重试", problem.path("detail").asString());
        assertSingleAuthorization(endpoint.route());
        assertEquals(1, platform.completedAuthorizationCount());
        assertEquals(1, risk.requests().size());
        assertTrue(platform.requests().getFirst().sequence() < risk.requests().getFirst().sequence());
        assertNoUpstreamSecrets(response, output);
    }

    private void assertNoUpstreamSecrets(HttpResponse<String> response, CapturedOutput output) {
        for (String canary : List.of(SUMMARY_CANARY, HANDLER_CODE, HANDLER_HASH, HASH,
            RiskDecisionConfigurationSimulator.UPSTREAM_REQUEST_ID, RiskDecisionConfigurationSimulator.UPSTREAM_DETAIL_CANARY)) {
            assertFalse(response.body().contains(canary), "浏览器 Problem 不得披露上游值");
            assertFalse(output.getAll().contains(canary), "日志不得披露上游值");
        }
    }
    private record ResponseFault(String name, Endpoint endpoint, Consumer<ObjectNode> mutation) {
        @Override public String toString() { return name; }
    }

    private static Stream<Endpoint> posts() { return endpoints().filter(Endpoint::post); }
    private static Endpoint endpoint(String name) { return endpoints().filter(e -> e.name().equals(name)).findFirst().orElseThrow(); }
    private void resetScenario() {
        assertTrue(platform.unexpectedRequests().isEmpty());
        assertTrue(risk.unexpectedRequests().isEmpty());
        platform.reset(); platform.useRiskIdentity(); risk.reset();
    }
    private void setAt(ObjectNode root, String pointer, Object value) {
        int split = pointer.lastIndexOf('/');
        JsonNode parent = root.at(pointer.substring(0, split));
        String key = pointer.substring(split + 1);
        assertFalse(parent.isMissingNode(), pointer);
        JsonNode node = json.valueToTree(value);
        if (parent instanceof ObjectNode object) { assertTrue(object.has(key), pointer); object.set(key, node); }
        else ((ArrayNode) parent).set(Integer.parseInt(key), node);
    }
    private record Constraint(String path, Object value, String externalField) {
        @Override public String toString() { return path + " -> " + externalField; }
    }

    private static Stream<Endpoint> endpoints() {
        String read = "900000000000000536";
        return Stream.of(new Endpoint("authoring", "GET", AUTHORING, "operations-risk-rule-authoring-context", read),
            new Endpoint("rules", "GET", RULES, "operations-risk-rule-set-list", read),
            new Endpoint("rule", "GET", RULES + "/30001", "operations-risk-rule-set-detail", read),
            new Endpoint("save", "POST", RULES, "operations-risk-rule-set-save", "900000000000000537"),
            new Endpoint("delete", "DELETE", RULES + "/90001", "operations-risk-rule-set-delete", "900000000000000541"),
            new Endpoint("freeze", "POST", RULES + "/30001/freeze", "operations-risk-rule-set-freeze", "900000000000000538"),
            new Endpoint("candidates", "GET", CANDIDATES, "operations-risk-test-candidate-list", read),
            new Endpoint("candidate", "GET", CANDIDATES + "/40001", "operations-risk-test-candidate-detail", read),
            new Endpoint("create", "POST", CANDIDATES, "operations-risk-test-candidate-create", "900000000000000539"),
            new Endpoint("validate", "POST", CANDIDATES + "/40001/validate", "operations-risk-test-candidate-validate", "900000000000000540"));
    }

    private String body(Endpoint endpoint) {
        return switch (endpoint.name()) {
            case "save" -> json.writeValueAsString(saveRequest());
            case "delete" -> "{\"expectedVersion\":\"1\"}";
            case "freeze" -> "{\"expectedContentHash\":\"" + HASH + "\"}";
            case "create" -> "{\"ruleSetVersionId\":\"30001\",\"expectedRuleSetContentHash\":\"" + HASH + "\"}";
            case "validate" -> "{\"expectedVersion\":1}";
            default -> null;
        };
    }

    private Map<String, String> headers(Endpoint endpoint) {
        return endpoint.post() ? commandHeaders(endpoint.route(), endpoint.name().equals("validate")
            ? "risk-validate-v1-40001-1" : KEY) : trustedHeaders(endpoint.route());
    }

    @Test
    void configuredActionsAndManualMessageAreForwardedWithoutNormalization() throws Exception {
        Endpoint save = endpoint("save");
        ObjectNode request = (ObjectNode) json.readTree(body(save));
        ObjectNode rule = (ObjectNode) request.at("/rules/0");
        rule.putNull("action"); rule.putNull("reasonMessage");
        rule.set("additionalActions", configurationActions());
        var response = send("POST", save.path(), request.toString(), headers(save));
        assertEquals(200, response.statusCode(), response.body());
        assertSingleAuthorization(save.route());
        assertEquals(1, risk.requests().size());
        assertEquals(request, json.readTree(risk.requests().getFirst().body()));
        assertEquals(headers(save).get("Idempotency-Key"), risk.requests().getFirst().header("Idempotency-Key"));
        assertTrue(platform.requests().getFirst().sequence() < risk.requests().getFirst().sequence());
        resetScenario();
        rule.set("action", json.createObjectNode().put("actionCode", "DENY_CHECKOUT").put("actionVersionNo", 1));
        rule.put("reasonCode", "MANUAL_REASON"); rule.put("reasonMessage", "人工填写拒绝说明");
        response = send("POST", save.path(), request.toString(), headers(save));
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(request, json.readTree(risk.requests().getFirst().body()));
    }

    @Test
    void configuredCommandDeniedByPlatformNeverCallsRisk() throws Exception {
        Endpoint save = endpoint("save"); platform.allow(false);
        ObjectNode request = (ObjectNode) json.readTree(body(save));
        ((ObjectNode) request.at("/rules/0")).set("additionalActions", configurationActions());
        var response = send("POST", save.path(), request.toString(), headers(save));
        assertEquals(403, response.statusCode()); assertTrue(risk.requests().isEmpty());
        assertSingleAuthorization(save.route());
    }

    @Test
    void configurationDetailsAndDynamicTargetMetadataReachBrowserWithoutHandlerFields() throws Exception {
        Endpoint detail = endpoint("rule");
        risk.corruptWire(root -> { var rule = (ObjectNode) root.at("/rules/0"); rule.putNull("action");
            rule.putNull("reasonMessage"); rule.set("additionalActions", configurationActions()); });
        var response = call(detail); assertEquals(200, response.statusCode(), response.body());
        assertEquals(configurationActions(), json.readTree(response.body()).at("/rules/0/additionalActions"));
        resetScenario();
        risk.corruptWire(root -> {
            var fields = (ArrayNode) root.get("fields");
            fields.insert(0, json.createObjectNode().put("fieldCode", "client_id").put("valueType", "STRING")
                .put("requiredFlag", true).put("dataClassification", "INTERNAL").put("ruleReferenceFlag", false).put("actionParameterFlag", true).put("variableCode", "REGISTERED_CLIENT_ID"));
            var actions = (ArrayNode) root.get("actions");
            actions.insert(1, json.createObjectNode().put("actionCode", "APPLY_COOLDOWN").put("actionName", "冷却")
                .put("actionCategory", "RESTRICTION").put("versionNo", 1).putNull("handlerCode")
                .putNull("implementationVersion").putNull("implementationHash").put("terminalFlag", false)
                .putNull("decisionCode").put("mergePolicy", "MAX").put("shadowSupportedFlag", false).put("status", "CONFIGURATION_ONLY"));
            root.set("targetBindings", json.createArrayNode().add(json.createObjectNode().put("bindingCode", "ENGINE_DEFINED_V9")
                .put("displayName", "工程批准的主体").put("subjectType", "APPROVED_SUBJECT").put("subjectSemanticRole", "CLIENT_ID").set("requiredFields", json.createArrayNode())));
            var parameters = json.createArrayNode();
            parameters.add(parameterDefinition("SUBJECT_ID", "STRING", true)); parameters.add(parameterDefinition("DURATION", "DURATION", false));
            root.set("actionParameters", parameters);
        });
        response = call(endpoint("authoring")); assertEquals(200, response.statusCode(), response.body());
        var browser = json.readTree(response.body());
        assertEquals("ENGINE_DEFINED_V9", browser.at("/targetBindings/0/bindingCode").asString());
        assertEquals(2, browser.path("actionParameters").size());
        assertTrue(browser.path("actions").findValues("handlerCode").isEmpty());
    }

    private ObjectNode parameterDefinition(String code, String type, boolean target) {
        return json.createObjectNode().put("actionCode", "APPLY_COOLDOWN").put("actionVersionNo", 1).put("parameterCode", code)
            .put("valueType", type).put("requiredFlag", true).put("allowedSourceType", target ? "VARIABLE" : "LITERAL")
            .put("targetParameterFlag", target).put("maxValues", 1).put("minimumValue", target ? null : "1")
            .put("maximumValue", target ? null : "31536000000").putNull("valuePattern");
    }

    private ArrayNode configurationActions() {
        return json.createArrayNode().add(json.createObjectNode().put("sequenceNo", 1).put("actionCode", "APPLY_COOLDOWN")
            .put("actionVersionNo", 1).put("targetBindingCode", "ENGINE_DEFINED_V9").putNull("subjectVariableCode").set("parameters", json.createArrayNode().add(
                json.createObjectNode().put("parameterCode", "DURATION").put("valueSequence", 1).put("sourceType", "LITERAL")
                    .putNull("fieldCode").put("valueType", "DURATION").put("literalValue", "1800000"))));
    }

    private HttpResponse<String> call(Endpoint endpoint) throws Exception {
        return send(endpoint.method(), endpoint.path(), body(endpoint), headers(endpoint));
    }

    private void assertOneOrderedCall(Endpoint endpoint) {
        assertSingleAuthorization(endpoint.route());
        assertEquals(1, platform.completedAuthorizationCount());
        assertEquals(1, risk.requests().size());
        var call = risk.requests().getFirst();
        assertTrue(platform.requests().getFirst().sequence() > 0);
        assertTrue(platform.requests().getFirst().sequence() < call.sequence(), "Platform 到达先于 Risk；完成顺序另由屏障证明");
        assertEquals(endpoint.method(), call.method());
        assertEquals(endpoint.path(), call.path());
        assertEquals(RISK_ACCOUNT_ID, call.header("X-Gateway-Actor-Subject"));
        assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id"));
        assertEquals(REQUEST_ID, call.header("X-Request-Id"));
        assertEquals(headers(endpoint).get("Idempotency-Key"), call.header("Idempotency-Key"));
        if (endpoint.post()) assertEquals(json.readTree(body(endpoint)), json.readTree(call.body()));
        else assertEquals("", call.body());
        assertEquals(endpoint.name().equals("rules") || endpoint.name().equals("candidates") ? "page=1&size=10" : null,
            call.query());
    }

    private void assertLocalProblem(HttpResponse<String> response, Endpoint endpoint, int status, String code) {
        assertProblem(json, response, status, code, REQUEST_ID);
        assertSingleAuthorization(endpoint.route());
        assertEquals(1, platform.completedAuthorizationCount());
        assertTrue(risk.requests().isEmpty(), "本地拒绝不得调用 Risk");
    }

    private record Endpoint(String name, String method, String path, String route, String permission) {
        boolean post() { return !method.equals("GET"); }
    }

    private void assertSingleAuthorization(String routeCode) {
        assertEquals(1, platform.requests().size());
        var authorization = platform.requests().getFirst();
        assertEquals(AUTHORIZE_PATH, authorization.path());
        assertEquals(routeCode, json.readTree(authorization.body()).path("routeCode").asString());
        assertEquals(RISK_ACCOUNT_ID, authorization.header("X-Gateway-Actor-Subject"));
        assertEquals(SESSION_ID, authorization.header("X-Gateway-Session-Id"));
    }

    private Map<String, String> commandHeaders(String routeCode, String key) {
        Map<String, String> headers = trustedHeaders(routeCode);
        headers.put("Content-Type", "application/json");
        headers.put("Idempotency-Key", key);
        return headers;
    }

    private Map<String, String> trustedHeaders(String routeCode) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Origin", ORIGIN);
        headers.put(RequestIdFilter.HEADER_NAME, REQUEST_ID);
        headers.put("X-Gateway-Realm", "OPERATIONS");
        headers.put("X-Gateway-Channel", "OPERATIONS");
        headers.put("X-Gateway-Actor-Type", "PLATFORM_USER");
        headers.put("X-Gateway-Actor-Subject", RISK_ACCOUNT_ID);
        headers.put("X-Gateway-Session-Id", SESSION_ID);
        headers.put("X-Gateway-Route-Code", routeCode);
        headers.put("X-Gateway-Context-Type", "TENANT");
        headers.put("X-Gateway-Tenant-Type", "INTERNAL");
        headers.put("X-Gateway-Tenant-Id", "tenant_10001");
        headers.put("X-Gateway-Tenant-Member-Id", "member_10001");
        return headers;
    }

    private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers)
        throws Exception {
        return sendAsync(method, path, body, headers).get(8, TimeUnit.SECONDS);
    }

    private CompletableFuture<HttpResponse<String>> sendAsync(String method, String path, String body,
        Map<String, String> headers) {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(8));
        headers.forEach(builder::header);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return CLIENT.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private CreateRuleSetVersionRequestBody saveRequest() {
        var operands = List.of(new OperandRequestBody("LEFT", 1, "CONTEXT", "country_code", "STRING", null),
            new OperandRequestBody("RIGHT", 1, "LITERAL", null, "STRING", "AE"));
        var nodes = List.of(new ConditionNodeRequestBody("8", null, 1, "GROUP", "AND", null, List.of()),
            new ConditionNodeRequestBody("9", "8", 1, "PREDICATE", null, "EQ", operands),
            new ConditionNodeRequestBody("10", "8", 2, "PREDICATE", null, "EQ", operands));
        return new CreateRuleSetVersionRequestBody("CHECKOUT_PREPARE", "DEFAULT_RULES", "规则集", "30001", HASH,
            "浏览器边界测试", List.of(new RuleRequestBody("ALLOW_RULE", "允许规则", 1, true, null, nodes,
                new RuleActionRequestBody("ALLOW_CHECKOUT", 1))));
    }
}
