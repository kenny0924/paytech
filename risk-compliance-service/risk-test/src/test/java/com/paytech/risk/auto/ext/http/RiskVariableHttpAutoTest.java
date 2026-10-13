package com.paytech.risk.auto.ext.http;

import static com.paytech.risk.test.decision.DecisionReleaseTestFixture.ACTOR;
import static com.paytech.risk.test.decision.DecisionReleaseTestFixture.SESSION;
import static org.junit.jupiter.api.Assertions.*;

import com.paytech.risk.test.base.RiskServiceAutoTestBase;
import com.paytech.risk.test.decision.RiskDecisionHttpClient;
import com.paytech.risk.test.decision.RiskVariableTestMapper;
import com.paytech.risk.test.builder.DecisionFlowRequestBuilder;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 真实 HTTP、MySQL 和 Redis 验证目录、CAS 及整体成员事务；禁止启动建表或种子。 */
@org.springframework.context.annotation.Import(RiskVariableHttpAutoTest.ProbeConfiguration.class)
class RiskVariableHttpAutoTest extends RiskServiceAutoTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ROOT = "/decision-variables/v1/variables";
    private final List<String> owned = new ArrayList<>();
    private RiskVariableTestMapper variables;
    private RiskDecisionHttpClient http;

    @Autowired private com.paytech.risk.test.decision.DecisionConfigurationLockProbe probe;

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @org.springframework.context.annotation.Bean
        com.paytech.risk.test.decision.DecisionConfigurationLockProbe variableLockProbe() {
            return new com.paytech.risk.test.decision.DecisionConfigurationLockProbe("RiskVariableMapper.find");
        }
    }

    @Autowired void mapper(JdbcTemplate jdbc) { variables = new RiskVariableTestMapper(jdbc); }
    @BeforeEach void open() { http = new RiskDecisionHttpClient(port); }
    @AfterEach void clean() { try { variables.deleteOwned(owned); } finally { http.close(); } }

    @Test void catalogAndCurrentValuesComeFromVariableLibrary() throws Exception {
        var context = http.get("/decision-variables/v1/authoring-context", ACTOR, SESSION);
        assertEquals(200, context.statusCode(), context.body());
        assertTrue(http.json(context).path("contracts").isArray());
        var batch = http.get(ROOT + "?codes=DEMO_AMOUNT_THRESHOLD,NODE_DECISION", ACTOR, SESSION);
        assertEquals(200, batch.statusCode(), batch.body());
        assertEquals(2, http.json(batch).path("items").size());
        assertEquals(0, http.json(batch).path("missingCodes").size());
    }

    @Test void memberRequestUsesGatewayCompatibleByteBoundary() throws Exception {
        var created = send("POST", ROOT, draft("COLLECTION", "STRING"), 201);
        String path = ROOT + "/" + created.path("variableCode").asString() + "/members";
        String body = "{\"expectedVersion\":\"1\",\"members\":[]}";
        String exact = body + " ".repeat(65536 - body.length());
        var oversized = http.configuration("PUT", path, ACTOR, SESSION, exact + " ");
        assertEquals(413, oversized.statusCode(), oversized.body());
        var accepted = http.configuration("PUT", path, ACTOR, SESSION, exact);
        assertEquals(200, accepted.statusCode(), accepted.body());
        assertEquals("2", http.json(accepted).path("version").asString());
        var context = http.get("/decision-variables/v1/authoring-context", ACTOR, SESSION);
        assertEquals(65536, http.json(context).at("/limits/memberBodyBytes").asInt());
    }

    @Test void previouslyUnknownInputAliasAppearsWithoutRestartOrCodeRegistration() throws Exception {
        var draft = draft("INPUT", "STRING");
        draft.put("pointCode", "CHECKOUT_PREPARE").put("sourceContractCode", "ORDER_PREPARATION")
            .put("sourceContractVersion", 4).put("sourceField", "country_code").put("formatCode", "UPPER_ALPHA_2");
        String code = draft.path("variableCode").asString();
        send("POST", ROOT, draft, 201);
        var page = send("GET", ROOT + "?codes=" + code, null, 200);
        assertEquals(code, page.at("/items/0/variableCode").asString());
        assertEquals("INPUT", page.at("/items/0/sourceType").asString());
        assertEquals("country_code", send("GET", ROOT + "/" + code, null, 200).path("sourceField").asString());
    }

    @Test void catalogSearchAcceptsUnicodeNamesAndTreatsWildcardsLiterally() throws Exception {
        var draft = draft("CONSTANT", "STRING").put("variableName", "中文阈值_100%!").put("currentValue", "AE");
        String code = draft.path("variableCode").asString();
        send("POST", ROOT, draft, 201);
        for (String keyword : List.of("中文阈值_100%!", code)) {
            String encoded = java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8);
            var found = send("GET", ROOT + "?keyword=" + encoded, null, 200);
            assertEquals(1, found.path("total").asInt());
            assertEquals(code, found.at("/items/0/variableCode").asString());
        }
        String absent = java.net.URLEncoder.encode("中文阈值_100%!不存在", java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, send("GET", ROOT + "?keyword=" + absent, null, 200).path("total").asInt());
    }

    @Test void constantUsesCasAndDeletedCodeCannotBeReused() throws Exception {
        var draft = draft("CONSTANT", "NUMBER"); draft.put("currentValue", "8000");
        String code = draft.path("variableCode").asString(), path = ROOT + "/" + code;
        var created = send("POST", ROOT, draft, 201);
        assertEquals("8000.00000000", created.path("currentValue").asString());
        send("POST", ROOT, draft, 409);
        var change = update("1", "9000", "ACTIVE");
        assertEquals("2", send("PUT", path, change, 200).path("version").asString());
        assertEquals("VARIABLE_VERSION_CONFLICT", send("PUT", path, change, 409).path("code").asString());
        send("PUT", path, update("2", "9000", "DISABLED"), 200);
        assertEquals("9000.00000000", send("GET", path, null, 200).path("currentValue").asString());
        send("DELETE", path + "?expectedVersion=3", null, 200);
        assertEquals("DELETED", send("GET", path, null, 200).path("status").asString());
        send("POST", ROOT, draft, 409);
    }

    @Test void membersRetainExactTextAndOldPageVersionCannotMixReplacements() throws Exception {
        var draft = draft("COLLECTION", "STRING"); String path = ROOT + "/" + draft.path("variableCode").asString();
        send("POST", ROOT, draft, 201);
        send("PUT", path + "/members", members("1", List.of("A", "A ", "001")), 200);
        var first = send("GET", path + "/members?page=1&size=2", null, 200);
        assertEquals("A ", first.at("/items/1/value").asString());
        send("PUT", path + "/members", members("2", List.of("B")), 200);
        send("GET", path + "/members?page=2&size=2&expectedVersion=2", null, 409);
        assertEquals(1, send("GET", path + "/members", null, 200).path("total").asInt());
        send("PUT", path + "/members", members("3", List.of()), 200);
        assertEquals(0, send("GET", path + "/members", null, 200).path("total").asInt());
    }

    @Test void duplicateNumericMemberRejectsWholeBatchAndRetainsRootVersion() throws Exception {
        var draft = draft("COLLECTION", "NUMBER"); String path = ROOT + "/" + draft.path("variableCode").asString();
        send("POST", ROOT, draft, 201);
        send("PUT", path + "/members", members("1", List.of("2")), 200);
        send("PUT", path + "/members", members("2", List.of("1", "1.00000000")), 400);
        var current = send("GET", path + "/members", null, 200);
        assertEquals("2", current.path("version").asString());
        assertEquals("2.00000000", current.at("/items/0/value").asString());
        send("PUT", path + "/members", members("2", Collections.nCopies(1001, "1")), 422);
        assertEquals("2", send("GET", path, null, 200).path("version").asString());
    }

    @Test void dependencyReferencesBlockDeleteAndInvalidCursorIsAClientError() throws Exception {
        var collection = draft("COLLECTION", "STRING"); String code = collection.path("variableCode").asString();
        send("POST", ROOT, collection, 201);
        send("PUT", ROOT + "/" + code + "/members", members("1", List.of("AE")), 200);
        var constant = draft("CONSTANT", "STRING"); constant.put("allowedValuesVariableCode", code).put("currentValue", "AE");
        send("POST", ROOT, constant, 201);
        var refs = send("GET", ROOT + "/" + code + "/references", null, 200);
        assertTrue(refs.path("items").valueStream().anyMatch(v -> v.path("code").asString().equals(constant.path("variableCode").asString())));
        assertEquals("VARIABLE_IN_USE", send("DELETE", ROOT + "/" + code + "?expectedVersion=2", null, 409).path("code").asString());
        send("GET", ROOT + "/" + code + "/references?cursor=W10", null, 400);
    }

    @Test void systemDefinitionsCannotChangeStatusOrBeDeleted() throws Exception {
        var detail = send("GET", ROOT + "/NODE_MATCHED", null, 200);
        String version = detail.path("version").asString();
        send("PUT", ROOT + "/NODE_MATCHED", update(version, null, "DISABLED"), 403);
        send("DELETE", ROOT + "/NODE_MATCHED?expectedVersion=" + version, null, 403);
        assertEquals(version, send("GET", ROOT + "/NODE_MATCHED", null, 200).path("version").asString());
    }

    @Test void simultaneousCasUpdatesHaveExactlyOneWinner() throws Exception {
        var draft = draft("CONSTANT", "NUMBER"); draft.put("currentValue", "1");
        String path = ROOT + "/" + draft.path("variableCode").asString(); send("POST", ROOT, draft, 201);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<Integer>>();
            for (String value : List.of("2", "3")) futures.add(pool.submit(() -> {
                start.await();
                try (var concurrent = new RiskDecisionHttpClient(port)) {
                    return concurrent.configuration("PUT", path, ACTOR, SESSION, update("1", value, "ACTIVE").toString()).statusCode();
                }
            }));
            start.countDown();
            var statuses = new ArrayList<Integer>();
            for (var future : futures) statuses.add(future.get(10, TimeUnit.SECONDS));
            Collections.sort(statuses); assertEquals(List.of(200, 409), statuses);
        }
        assertEquals("2", send("GET", path, null, 200).path("version").asString());
    }

    @Test void ruleAndFlowReadLatestSharedValueWithoutChangingSavedHashesOrSuccessfulReplay() throws Exception {
        String ruleCode = decisionTestMapper.uniqueRuleSetCode(), flowCode = decisionTestMapper.uniqueRuleSetCode();
        String ruleKey = decisionTestMapper.uniqueNamespace(), flowKey = decisionTestMapper.uniqueNamespace();
        var runtimeBefore = decisionTestMapper.runtimeFactCounts();
        try {
            var variable = draft("CONSTANT", "NUMBER").put("pointCode", "CHECKOUT_PREPARE")
                .put("numericUnit", "MONEY").put("currencyVariableCode", "INPUT_CURRENCY").put("currentValue", "8000");
            String variableCode = variable.path("variableCode").asString();
            send("POST", ROOT, variable, 201);
            var rule = JSON.createObjectNode().put("pointCode", "CHECKOUT_PREPARE").put("ruleSetCode", ruleCode)
                .put("ruleSetName", "共享阈值规则").putNull("baseRuleSetVersionId").putNull("baseContentHash")
                .put("changeReason", "开发回归").put("operandFormat", "VARIABLE_REFERENCE");
            var entry = rule.putArray("rules").addObject().put("ruleCode", "AMOUNT_CHECK").put("ruleName", "金额判断")
                .put("priorityNo", 1).put("enabledFlag", true).putNull("reasonCode").putNull("reasonMessage").putNull("action");
            entry.putArray("additionalActions");
            var predicate = entry.putArray("conditionNodes").addObject().put("nodeKey", "root").putNull("parentNodeKey")
                .put("sequenceNo", 1).put("nodeType", "PREDICATE").putNull("groupOperator").put("comparisonOperator", "GT");
            predicate.putArray("operands").add(reference("LEFT", "INPUT_NET_AMOUNT", "NUMBER"))
                .add(reference("RIGHT", variableCode, "NUMBER"));
            var ruleResult = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, ruleKey, rule);
            assertEquals(200, ruleResult.statusCode(), ruleResult.body());
            String versionId = http.json(ruleResult).path("ruleSetVersionId").asString();
            var beforeRule = send("GET", "/decision-rule-sets/v1/rule-sets/" + versionId, null, 200);

            var flow = DecisionFlowRequestBuilder.template(flowCode);
            var graph = (ObjectNode)flow.path("graph");
            graph.putArray("nodes").add(DecisionFlowRequestBuilder.node("start", "START", null))
                .add(DecisionFlowRequestBuilder.node("first", "RULE_SET", null).put("ruleSetVersionId", versionId))
                .add(DecisionFlowRequestBuilder.node("second", "RULE_SET", null).put("ruleSetVersionId", versionId))
                .add(DecisionFlowRequestBuilder.node("allowed", "END", "ALLOW"))
                .add(DecisionFlowRequestBuilder.node("review", "END", "REVIEW"));
            var conditional = edge("decision", "first", "second", "CONDITIONAL", 1);
            var condition = conditional.putObject("condition").put("schemaVersion", 2).putObject("root")
                .put("kind", "PREDICATE").put("operator", "EQ");
            var deny = reference("RIGHT", null, "STRING").put("sourceType", "LITERAL").put("literalValue", "DENY");
            condition.putArray("operands").add(reference("LEFT", "NODE_DECISION", "STRING")).add(deny);
            graph.putArray("edges").add(edge("entry", "start", "first", "UNCONDITIONAL", 0)).add(conditional)
                .add(edge("otherwise", "first", "allowed", "ELSE", 0)).add(edge("finish", "second", "review", "UNCONDITIONAL", 0));
            var flowResult = http.post("/decision-flows/v1/flows", ACTOR, SESSION, flowKey, flow);
            assertEquals(200, flowResult.statusCode(), flowResult.body());
            String flowPath = "/decision-flows/v1/flows/" + http.json(flowResult).path("flowId").asString();
            var beforeFlow = send("GET", flowPath, null, 200);

            send("PUT", ROOT + "/" + variableCode, update("1", "9000", "ACTIVE"), 200);
            var afterRule = send("GET", "/decision-rule-sets/v1/rule-sets/" + versionId, null, 200);
            var afterFlow = send("GET", flowPath, null, 200);
            assertEquals(beforeRule.path("contentHash"), afterRule.path("contentHash"));
            assertEquals(beforeRule.path("rules"), afterRule.path("rules"));
            assertEquals(beforeFlow.path("contentHash"), afterFlow.path("contentHash"));
            assertEquals(beforeFlow.path("graph"), afterFlow.path("graph"));
            for (JsonNode detail : List.of(afterRule, afterFlow)) {
                var current = detail.path("currentVariables").valueStream()
                    .filter(v -> v.path("variableCode").asString().equals(variableCode)).findFirst().orElseThrow();
                assertEquals("9000.00000000", current.path("currentValue").asString());
            }
            send("DELETE", ROOT + "/" + variableCode + "?expectedVersion=2", null, 409);
            send("PUT", ROOT + "/" + variableCode, update("2", "9000", "DISABLED"), 200);
            RiskDecisionHttpClient.assertProblem(http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, ruleKey, rule), 409, "CONCURRENT_REQUEST");
            RiskDecisionHttpClient.assertProblem(http.post("/decision-flows/v1/flows", ACTOR, SESSION, flowKey, flow), 409, "RISK_FLOW_CODE_EXISTS");
            assertEquals(runtimeBefore, decisionTestMapper.runtimeFactCounts());
        } finally {
            flowTestMapper.clean(flowCode);
            decisionTestMapper.deleteOwnedCommand("OPERATIONS_ACCOUNT:" + ACTOR, "SAVE_DECISION_FLOW", flowKey);
            decisionTestMapper.deleteOwnedDecisionConfiguration(ruleCode, List.of(ruleKey));
        }
    }

    /** 两种保存和两种变量变更分别走真实共享/排他锁，两个取得顺序都必须成立。 */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "RULE,DELETE,SHARE", "RULE,DELETE,WRITE", "RULE,DISABLE,SHARE", "RULE,DISABLE,WRITE",
        "FLOW,DELETE,SHARE", "FLOW,DELETE,WRITE", "FLOW,DISABLE,SHARE", "FLOW,DISABLE,WRITE"
    })
    void configurationSaveAndVariableMutationRespectDatabaseLockOrder(String kind, String mutation, String firstLock) throws Exception {
        var variable = draft("CONSTANT", "STRING").put("currentValue", "AE");
        String code = variable.path("variableCode").asString(), path = ROOT + "/" + code;
        send("POST", ROOT, variable, 201);
        String configCode = decisionTestMapper.uniqueRuleSetCode(), key = decisionTestMapper.uniqueNamespace();
        String operation = kind.equals("RULE") ? "CREATE_RULE_SET_VERSION" : "SAVE_DECISION_FLOW";
        String baseRuleCode = decisionTestMapper.uniqueRuleSetCode(), baseRuleKey = decisionTestMapper.uniqueNamespace();
        try {
            String baseRuleVersion = null;
            if (kind.equals("FLOW")) {
                var savedRule = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, baseRuleKey,
                    com.paytech.risk.test.builder.DecisionRuleSetRequestBuilder.currentFirstVersion(baseRuleCode));
                assertEquals(200, savedRule.statusCode(), savedRule.body());
                baseRuleVersion = http.json(savedRule).path("ruleSetVersionId").asString();
            }
            var draft = kind.equals("RULE") ? ruleUsing(configCode, code) : flowUsing(configCode, code, baseRuleVersion);
            String savePath = kind.equals("RULE") ? "/decision-rule-sets/v1/rule-sets" : "/decision-flows/v1/flows";
            Callable<HttpResponse<String>> save = () -> http.post(savePath, ACTOR, SESSION, key, draft);
            Callable<HttpResponse<String>> change = () -> http.configuration(mutation.equals("DELETE") ? "DELETE" : "PUT",
                path + (mutation.equals("DELETE") ? "?expectedVersion=1" : ""), ACTOR, SESSION,
                mutation.equals("DELETE") ? null : update("1", "AE", "DISABLED").toString());
            boolean saveFirst = firstLock.equals("SHARE");
            try (var pool = Executors.newVirtualThreadPerTaskExecutor(); var gate = probe.arm(code, firstLock)) {
                var first = pool.submit(saveFirst ? save : change);
                assertTrue(gate.awaitAcquired(), "首个请求必须真实持有变量根锁");
                var second = pool.submit(saveFirst ? change : save);
                assertTrue(gate.awaitCompetitor(), "竞争请求必须到达同一变量根 SQL");
                assertFalse(first.isDone());
                assertFalse(second.isDone());
                gate.close();
                var firstResult = first.get(5, TimeUnit.SECONDS);
                var secondResult = second.get(5, TimeUnit.SECONDS);
                assertEquals(200, firstResult.statusCode(), firstResult.body());
                if (!saveFirst) {
                    RiskDecisionHttpClient.assertProblem(secondResult, kind.equals("RULE") ? 409 : 422,
                        kind.equals("RULE") ? "VARIABLE_DISABLED" : "VALIDATION_ERROR");
                    if (kind.equals("FLOW")) assertEquals("graph", http.json(secondResult).at("/errors/0/field").asString());
                }
                else if (mutation.equals("DELETE")) RiskDecisionHttpClient.assertProblem(secondResult, 409, "VARIABLE_IN_USE");
                else assertEquals(200, secondResult.statusCode(), secondResult.body());
                String expectedStatus = mutation.equals("DISABLE") ? "DISABLED" : saveFirst ? "ACTIVE" : "DELETED";
                assertEquals(expectedStatus, send("GET", path, null, 200).path("status").asString());
                if (saveFirst) {
                    String detailPath = kind.equals("RULE") ? savePath + "/" + http.json(firstResult).path("ruleSetVersionId").asString()
                        : savePath + "/" + http.json(firstResult).path("flowId").asString();
                    send("GET", detailPath, null, 200);
                }
            }
        } finally {
            if (kind.equals("RULE")) decisionTestMapper.deleteOwnedDecisionConfiguration(configCode, List.of(key));
            else {
                flowTestMapper.clean(configCode);
                decisionTestMapper.deleteOwnedCommand("OPERATIONS_ACCOUNT:" + ACTOR, operation, key);
                decisionTestMapper.deleteOwnedDecisionConfiguration(baseRuleCode, List.of(baseRuleKey));
            }
        }
    }

    /** 使用本用例独占常量，避免把其他用例的引用计入并发断言。 */
    private ObjectNode ruleUsing(String ruleCode, String variableCode) {
        ObjectNode body = JSON.valueToTree(com.paytech.risk.test.builder.DecisionRuleSetRequestBuilder.currentFirstVersion(ruleCode));
        ((ObjectNode) body.at("/rules/0/conditionNodes/0/operands/0")).put("variableCode", variableCode);
        return body;
    }

    /** 规则节点出线引用共享常量；被引用规则自身不使用该常量。 */
    private ObjectNode flowUsing(String flowCode, String variableCode, String ruleVersionId) {
        var body = DecisionFlowRequestBuilder.template(flowCode);
        var graph = (ObjectNode) body.path("graph");
        ((tools.jackson.databind.node.ArrayNode) graph.path("nodes")).add(DecisionFlowRequestBuilder.node("other", "END", "REVIEW"));
        ((tools.jackson.databind.node.ArrayNode) graph.path("nodes")).add(
            DecisionFlowRequestBuilder.node("check", "RULE_SET", null).put("ruleSetVersionId", ruleVersionId));
        var conditional = edge("match", "check", "finish", "CONDITIONAL", 1);
        var predicate = conditional.putObject("condition").put("schemaVersion", 2).putObject("root")
            .put("kind", "PREDICATE").put("operator", "EQ");
        predicate.putArray("operands").add(reference("LEFT", variableCode, "STRING"))
            .add(reference("RIGHT", null, "STRING").put("sourceType", "LITERAL").put("literalValue", "AE"));
        graph.putArray("edges").add(edge("entry", "start", "check", "UNCONDITIONAL", 0))
            .add(conditional).add(edge("otherwise", "check", "other", "ELSE", 0));
        return body;
    }

    private static ObjectNode reference(String role, String code, String type) {
        return JSON.createObjectNode().put("operandRole", role).put("valueSequence", 1).put("sourceType", "VARIABLE")
            .put("variableCode", code).putNull("fieldCode").put("valueType", type).putNull("literalValue")
            .putNull("numericUnit").putNull("currencyCode").putNull("currencyVariableCode");
    }

    private static ObjectNode edge(String key, String source, String target, String type, int priority) {
        return JSON.createObjectNode().put("edgeKey", key).put("sourceNodeKey", source).put("targetNodeKey", target)
            .put("type", type).put("priority", priority).put("name", key).putNull("condition");
    }

    private ObjectNode draft(String source, String type) {
        String code = "AUTO_VAR_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(); owned.add(code);
        var draft = JSON.createObjectNode().put("variableCode", code).put("variableName", "开发回归变量")
            .putNull("description").put("sourceType", source).put("valueType", type).putNull("pointCode")
            .putNull("sourceContractCode").putNull("sourceContractVersion").putNull("sourceField")
            .put("formatCode", type.equals("STRING") ? "TEXT" : "NONE").putNull("currencyCode")
            .putNull("currencyVariableCode").putNull("allowedValuesVariableCode").putNull("currentValue").put("status", "ACTIVE");
        draft.put("numericUnit", type.equals("NUMBER") ? "SCALAR" : null);
        draft.putArray("usages").add("RULE_CONDITION").add("FLOW_CONDITION"); return draft;
    }

    private static ObjectNode update(String version, String value, String status) {
        return JSON.createObjectNode().put("expectedVersion", version).put("variableName", "开发回归变量")
            .putNull("description").put("currentValue", value).put("status", status);
    }

    private static ObjectNode members(String version, List<String> values) {
        var body = JSON.createObjectNode().put("expectedVersion", version); var members = body.putArray("members");
        values.forEach(v -> members.addObject().put("value", v).putNull("displayName")); return body;
    }

    private JsonNode send(String method, String path, ObjectNode body, int status) throws Exception {
        HttpResponse<String> result = http.configuration(method, path, ACTOR, SESSION, body == null ? null : body.toString());
        assertEquals(status, result.statusCode(), result.body()); return http.json(result);
    }
}
