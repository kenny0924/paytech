package com.paytech.risk.auto.ext.http;

import static org.junit.jupiter.api.Assertions.*;
import static com.paytech.risk.test.decision.RiskDecisionHttpClient.assertProblem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.test.decision.LegacyRuleSetTestMapper;

import com.paytech.risk.test.base.RiskServiceAutoTestBase;
import com.paytech.risk.test.builder.DecisionFlowRequestBuilder;
import com.paytech.risk.test.decision.RiskDecisionHttpClient;
import com.paytech.risk.test.decision.RiskDecisionTestMapper.OwnedCommandIdentity;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** 从真实 HTTP 到本机 MySQL/Redis 验证完整配置、不可变历史、回放及 CAS。 */
class DecisionFlowConfigurationHttpAutoTest extends RiskServiceAutoTestBase {
    private static final String BASE = "/decision-flows/v1";
    private static final String ACTOR = "910000000000000091";
    private static final String SESSION = "risk-auto-decision-flow";
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DecisionIdentifierGenerator identifiers;

    @Test
    void test_fullVersionRoundTripAndCurrentRootListing() throws Exception {
        String code = decisionTestMapper.uniqueRuleSetCode();
        var keys = new ArrayList<OwnedCommandIdentity>();
        var before = decisionTestMapper.runtimeFactCounts();
        try (var http = new RiskDecisionHttpClient(port)) {
            var catalog = http.get(BASE + "/authoring-context", ACTOR, SESSION);
            assertEquals(200, catalog.statusCode(), catalog.body());
            assertEquals(9, http.json(catalog).get("fields").size());
            var request = DecisionFlowRequestBuilder.template(code);
            var first = save(http, request, key("SAVE_DECISION_FLOW", keys));
            var detail = get(http, BASE + "/flows/" + first.get("flowId").asText());
            assertEquals("1", detail.get("version").asText());
            assertEquals("-120", detail.at("/graph/nodes/0/position/x").asText());
            assertEquals(2, detail.at("/graph/nodes").size());
            var edited = request.deepCopy().put("baseFlowVersionId", first.get("flowVersionId").asText())
                .put("expectedVersion", "1").put("flowName", "更新名称").put("changeReason", "配置第二版");
            var second = save(http, edited, key("SAVE_DECISION_FLOW", keys));
            assertEquals(2, second.get("versionNo").intValue());
            var old = get(http, BASE + "/flows/" + first.get("flowId").asText() + "/versions/" + first.get("flowVersionId").asText());
            assertEquals("配置测试流程", old.get("flowName").asText());
            assertEquals(1, old.get("versionNo").intValue());
            assertEquals(2, old.get("currentVersionNo").intValue());
            var listing = get(http, BASE + "/flows?pointCode=CHECKOUT_PREPARE&flowCode=" + code);
            assertEquals(1, listing.get("total").intValue());
            assertEquals("更新名称", listing.at("/items/0/flowName").asText());
            var history = get(http, BASE + "/flows/" + first.get("flowId").asText() + "/versions?maxVersionNo=1");
            assertEquals(1, history.get("total").intValue());
            assertEquals(2, flowTestMapper.versionCount(code));
            assertEquals(before, decisionTestMapper.runtimeFactCounts());
        } finally { cleanup(code, keys); }
    }

    @Test
    void test_successReplayRemainsStableAfterEditAndWholeDeletion() throws Exception {
        String code = decisionTestMapper.uniqueRuleSetCode();
        var keys = new ArrayList<OwnedCommandIdentity>();
        try (var http = new RiskDecisionHttpClient(port)) {
            var request = DecisionFlowRequestBuilder.template(code);
            String firstKey = key("SAVE_DECISION_FLOW", keys);
            var first = save(http, request, firstKey);
            var changed = request.deepCopy().put("flowName", "变化");
            assertProblem(http.post(BASE + "/flows", ACTOR, SESSION, firstKey, changed), 409, "CONCURRENT_REQUEST");
            var edited = request.deepCopy().put("baseFlowVersionId", first.get("flowVersionId").asText()).put("expectedVersion", "1");
            save(http, edited, key("SAVE_DECISION_FLOW", keys));
            String flow = first.get("flowId").asText();
            String deleteKey = key("DELETE_DECISION_FLOW", keys);
            var deletion = http.deleteFlow(BASE + "/flows/" + flow + "?expectedVersion=2", ACTOR, SESSION, deleteKey);
            assertEquals(200, deletion.statusCode(), deletion.body());
            assertEquals("3", http.json(deletion).get("version").asText());
            assertEquals(first, save(http, request, firstKey));
            assertEquals(deletion.body(), http.deleteFlow(BASE + "/flows/" + flow + "?expectedVersion=2", ACTOR, SESSION, deleteKey).body());
            assertProblem(http.get(BASE + "/flows/" + flow, ACTOR, SESSION), 404, "RISK_DECISION_CONFIGURATION_NOT_FOUND");
            assertEquals("DELETED", get(http, BASE + "/flows/" + flow + "/versions/" + first.get("flowVersionId").asText()).get("flowStatus").asText());
            assertProblem(http.post(BASE + "/flows", ACTOR, SESSION, key("SAVE_DECISION_FLOW", keys), request), 409, "RISK_FLOW_CODE_EXISTS");
            assertEquals(2, flowTestMapper.versionCount(code));
        } finally { cleanup(code, keys); }
    }

    @Test
    void test_incompleteGraphValidationDoesNotWriteAndSaveReturnsFieldErrors() throws Exception {
        String code = decisionTestMapper.uniqueRuleSetCode();
        var keys = new ArrayList<OwnedCommandIdentity>();
        try (var http = new RiskDecisionHttpClient(port)) {
            var request = DecisionFlowRequestBuilder.template(code);
            ((ObjectNode) request.at("/graph/nodes/1")).putNull("decisionCode");
            var report = http.dictionary("POST", BASE + "/flows/validate", ACTOR, SESSION, request.toString());
            assertEquals(200, report.statusCode(), report.body());
            assertFalse(http.json(report).get("valid").asBoolean());
            assertEquals("finish", http.json(report).at("/issues/0/nodeKey").asText());
            var problem = assertProblem(http.post(BASE + "/flows", ACTOR, SESSION, key("SAVE_DECISION_FLOW", keys), request), 422, "VALIDATION_ERROR");
            assertEquals("graph.nodes[1].decisionCode", problem.at("/errors/0/field").asText());
            assertEquals(0, flowTestMapper.rootCount(code));
        } finally { cleanup(code, keys); }
    }

    @Test
    void test_unknownMembersScalarCoercionAndUndeclaredQueryAreRejected() throws Exception {
        String code = decisionTestMapper.uniqueRuleSetCode();
        var keys = new ArrayList<OwnedCommandIdentity>();
        try (var http = new RiskDecisionHttpClient(port)) {
            for (String invalid : List.of("unknown", "missing-null-key", "numeric-coordinate", "numeric-id")) {
                var body = DecisionFlowRequestBuilder.template(code);
                switch (invalid) {
                    case "unknown" -> body.put("actorId", "1");
                    case "missing-null-key" -> body.remove("baseFlowVersionId");
                    case "numeric-coordinate" -> ((ObjectNode) body.at("/graph/nodes/0/position")).put("x", 12);
                    case "numeric-id" -> body.put("expectedVersion", 1);
                }
                assertProblem(http.post(BASE + "/flows", ACTOR, SESSION, key("SAVE_DECISION_FLOW", keys), body), 400, "INVALID_REQUEST");
            }
            assertProblem(http.get(BASE + "/flows?pointCode=CHECKOUT_PREPARE&size=10&size=20", ACTOR, SESSION), 400, "INVALID_REQUEST");
            assertProblem(http.get(BASE + "/flows?pointCode=CHECKOUT_PREPARE&extra=1", ACTOR, SESSION), 400, "INVALID_REQUEST");
            assertEquals(0, flowTestMapper.rootCount(code));
        } finally { cleanup(code, keys); }
    }

    @Test
    void test_concurrentEditorsOnlyOneCanAdvanceTheSameBase() throws Exception {
        String code = decisionTestMapper.uniqueRuleSetCode();
        var keys = new ArrayList<OwnedCommandIdentity>();
        try (var http = new RiskDecisionHttpClient(port)) {
            var request = DecisionFlowRequestBuilder.template(code);
            var first = save(http, request, key("SAVE_DECISION_FLOW", keys));
            var edit = request.deepCopy().put("baseFlowVersionId", first.get("flowVersionId").asText()).put("expectedVersion", "1");
            String leftKey = key("SAVE_DECISION_FLOW", keys), rightKey = key("SAVE_DECISION_FLOW", keys);
            var ready = new CyclicBarrier(2);
            var left = CompletableFuture.supplyAsync(() -> concurrent(http, edit.deepCopy().put("flowName", "左侧"), leftKey, ready));
            var right = CompletableFuture.supplyAsync(() -> concurrent(http, edit.deepCopy().put("flowName", "右侧"), rightKey, ready));
            var statuses = java.util.stream.Stream.of(left.get(), right.get()).sorted().toList();
            assertEquals(List.of(200, 409), statuses);
            assertEquals(2, flowTestMapper.versionCount(code));
            assertEquals(2L, ((Number) flowTestMapper.root(code).get("version")).longValue());
        } finally { cleanup(code, keys); }
    }

    @Test
    void test_retiredExactReferenceMayRemainButNewOrDeletedReferenceCannotSave() throws Exception {
        String code = decisionTestMapper.uniqueRuleSetCode(), ruleCode = decisionTestMapper.uniqueRuleSetCode();
        var keys = new ArrayList<OwnedCommandIdentity>();
        try (var http = new RiskDecisionHttpClient(port)) {
            var ruleResponse = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, key("CREATE_RULE_SET_VERSION", keys),
                com.paytech.risk.test.builder.DecisionRuleSetRequestBuilder.currentFirstVersion(ruleCode));
            assertEquals(200, ruleResponse.statusCode(), ruleResponse.body());
            var rule = http.json(ruleResponse);
            String ruleVersionId = rule.get("ruleSetVersionId").asText();
            var request = DecisionFlowRequestBuilder.template(code);
            ((tools.jackson.databind.node.ArrayNode) request.at("/graph/nodes")).add(
                DecisionFlowRequestBuilder.node("rules", "RULE_SET", null).put("ruleSetVersionId", ruleVersionId));
            ((ObjectNode) request.at("/graph/edges/0")).put("targetNodeKey", "rules");
            ((tools.jackson.databind.node.ArrayNode) request.at("/graph/edges")).add(
                ((ObjectNode) request.at("/graph/edges/0")).deepCopy().put("edgeKey", "after_rules")
                    .put("sourceNodeKey", "rules").put("targetNodeKey", "finish"));
            var first = save(http, request, key("SAVE_DECISION_FLOW", keys));
            var nextRule = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, key("CREATE_RULE_SET_VERSION", keys),
                com.paytech.risk.test.builder.DecisionRuleSetRequestBuilder.currentNextVersion(ruleCode, ruleVersionId, rule.get("contentHash").asText()));
            assertEquals(200, nextRule.statusCode(), nextRule.body());
            var edit = request.deepCopy().put("baseFlowVersionId", first.get("flowVersionId").asText()).put("expectedVersion", "1");
            var second = save(http, edit, key("SAVE_DECISION_FLOW", keys));
            var changedNode = edit.deepCopy().put("baseFlowVersionId", second.get("flowVersionId").asText()).put("expectedVersion", "2");
            ((ObjectNode) changedNode.at("/graph/nodes/2")).put("nodeKey", "replacement");
            ((ObjectNode) changedNode.at("/graph/edges/0")).put("targetNodeKey", "replacement");
            ((ObjectNode) changedNode.at("/graph/edges/1")).put("sourceNodeKey", "replacement");
            assertProblem(http.post(BASE + "/flows", ACTOR, SESSION, key("SAVE_DECISION_FLOW", keys), changedNode),
                422, "RISK_FLOW_REFERENCE_UNAVAILABLE");
            var ruleList = get(http, "/decision-rule-sets/v1/rule-sets?pointCode=CHECKOUT_PREPARE&ruleSetCode=" + ruleCode);
            JsonNode root = ruleList.at("/items/0");
            var deleted = http.delete("/decision-rule-sets/v1/rule-sets/" + root.get("ruleSetId").asText(), ACTOR, SESSION,
                key("DELETE_RULE_SET", keys), java.util.Map.of("expectedVersion", root.get("managementVersion").asText()));
            assertEquals(200, deleted.statusCode(), deleted.body());
            var keepDeleted = edit.deepCopy().put("baseFlowVersionId", second.get("flowVersionId").asText()).put("expectedVersion", "2");
            assertProblem(http.post(BASE + "/flows", ACTOR, SESSION, key("SAVE_DECISION_FLOW", keys), keepDeleted),
                422, "RISK_FLOW_REFERENCE_UNAVAILABLE");
            var ref = get(http, BASE + "/rule-set-reference?sourceType=FLOW_VERSION&sourceVersionId="
                + first.get("flowVersionId").asText() + "&nodeKey=rules");
            assertEquals("DELETED", ref.get("ruleSetStatus").asText());
            assertEquals(ruleVersionId, ref.at("/ruleSet/ruleSetVersionId").asText());
            assertProblem(http.get(BASE + "/rule-set-reference?sourceType=FLOW_VERSION&sourceVersionId="
                + first.get("flowVersionId").asText() + "&nodeKey=finish", ACTOR, SESSION), 404, "RISK_DECISION_CONFIGURATION_NOT_FOUND");
            assertProblem(http.get("/decision-rule-sets/v1/rule-sets/" + ruleVersionId, ACTOR, SESSION), 404, "RISK_DECISION_CONFIGURATION_NOT_FOUND");
            assertEquals(2, flowTestMapper.versionCount(code));
        } finally {
            flowTestMapper.clean(code);
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(ruleCode), keys);
        }
    }

    @Test
    void test_flowVersionsReferenceNewAndLegacyRulesUsingTheDirectInputContract() throws Exception {
        String code = decisionTestMapper.uniqueRuleSetCode(), legacyCode = decisionTestMapper.uniqueRuleSetCode(), newCode = decisionTestMapper.uniqueRuleSetCode();
        var keys = new ArrayList<OwnedCommandIdentity>();
        try (var http = new RiskDecisionHttpClient(port)) {
            var old = new LegacyRuleSetTestMapper(jdbc, new TransactionTemplate(transactionManager), identifiers)
                .insertFirstVersion(legacyCode, Long.parseLong(ACTOR), key("CREATE_RULE_SET_VERSION", keys));
            var response = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, key("CREATE_RULE_SET_VERSION", keys),
                com.paytech.risk.test.builder.DecisionRuleSetRequestBuilder.currentFirstVersion(newCode));
            assertEquals(200, response.statusCode(), response.body());
            String newId = http.json(response).get("ruleSetVersionId").asText();
            var request = DecisionFlowRequestBuilder.template(code);
            var nodes = (tools.jackson.databind.node.ArrayNode) request.at("/graph/nodes");
            nodes.add(DecisionFlowRequestBuilder.node("old_rules", "RULE_SET", null).put("ruleSetVersionId", old.ruleSetVersionId()));
            nodes.add(DecisionFlowRequestBuilder.node("new_rules", "RULE_SET", null).put("ruleSetVersionId", newId));
            var edges = (tools.jackson.databind.node.ArrayNode) request.at("/graph/edges");
            var firstEdge = (ObjectNode) edges.get(0);
            firstEdge.put("targetNodeKey", "old_rules");
            edges.add(firstEdge.deepCopy().put("edgeKey", "after_old").put("sourceNodeKey", "old_rules").put("targetNodeKey", "new_rules"));
            edges.add(firstEdge.deepCopy().put("edgeKey", "after_new").put("sourceNodeKey", "new_rules").put("targetNodeKey", "finish"));
            jdbc.update("UPDATE t_rule_set_version SET input_contract_version=5 WHERE rule_set_version_id=?", newId);
            try {
                assertProblem(http.post(BASE + "/flows", ACTOR, SESSION, key("SAVE_DECISION_FLOW", keys), request), 422, "RISK_FLOW_REFERENCE_UNAVAILABLE");
            } finally { jdbc.update("UPDATE t_rule_set_version SET input_contract_version=4 WHERE rule_set_version_id=?", newId); }
            var first = save(http, request, key("SAVE_DECISION_FLOW", keys));
            var second = save(http, request.deepCopy().put("baseFlowVersionId", first.get("flowVersionId").asText()).put("expectedVersion", "1"), key("SAVE_DECISION_FLOW", keys));
            assertEquals(2, get(http, BASE + "/flows/" + first.get("flowId").asText()).get("versionNo").intValue());
            var historical = get(http, BASE + "/flows/" + first.get("flowId").asText() + "/versions/" + first.get("flowVersionId").asText());
            assertEquals(2, historical.get("ruleSetReferences").size());
            var oldRef = get(http, BASE + "/rule-set-reference?sourceType=FLOW_VERSION&sourceVersionId=" + first.get("flowVersionId").asText() + "&nodeKey=old_rules");
            var newRef = get(http, BASE + "/rule-set-reference?sourceType=FLOW_VERSION&sourceVersionId=" + second.get("flowVersionId").asText() + "&nodeKey=new_rules");
            assertEquals(old.pointVersionId(), oldRef.at("/ruleSet/pointVersionId").asText());
            assertTrue(newRef.at("/ruleSet/pointVersionId").isNull());
        } finally {
            flowTestMapper.clean(code);
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(legacyCode, newCode), keys);
        }
    }

    @Test
    void test_disabledReasonOnlyRetainsOriginalNodeCodeAndMessage() throws Exception {
        String code = decisionTestMapper.uniqueRuleSetCode(), reason = "RCO17_" + java.util.UUID.randomUUID().toString().replace("-", "").toUpperCase();
        var keys = new ArrayList<OwnedCommandIdentity>();
        try (var http = new RiskDecisionHttpClient(port)) {
            var created = http.dictionary("POST", "/decision-dictionaries/v1/rejection-reasons", ACTOR, SESSION,
                "{\"reasonCode\":\"" + reason + "\",\"name\":\"拒绝测试\",\"description\":null,\"defaultMessage\":\"人工说明\"}");
            assertEquals(200, created.statusCode(), created.body());
            var request = DecisionFlowRequestBuilder.template(code);
            ((ObjectNode) request.at("/graph/nodes/1")).put("decisionCode", "DENY").put("reasonCode", reason).put("reasonMessage", "初次说明");
            var first = save(http, request, key("SAVE_DECISION_FLOW", keys));
            var disabled = http.dictionary("POST", "/decision-dictionaries/v1/rejection-reasons/" + reason + "/status", ACTOR, SESSION,
                "{\"expectedVersion\":\"1\",\"status\":\"DISABLED\"}");
            assertEquals(200, disabled.statusCode(), disabled.body());
            var edit = request.deepCopy().put("baseFlowVersionId", first.get("flowVersionId").asText()).put("expectedVersion", "1");
            var second = save(http, edit, key("SAVE_DECISION_FLOW", keys));
            var changed = edit.deepCopy().put("baseFlowVersionId", second.get("flowVersionId").asText()).put("expectedVersion", "2");
            ((ObjectNode) changed.at("/graph/nodes/1")).put("reasonMessage", "已修改说明");
            assertProblem(http.post(BASE + "/flows", ACTOR, SESSION, key("SAVE_DECISION_FLOW", keys), changed), 422, "RISK_FLOW_REFERENCE_UNAVAILABLE");
            var all = get(http, "/decision-dictionaries/v1/rejection-reasons/" + reason + "/references");
            assertEquals(2, all.get("total").intValue());
            assertEquals("FLOW_END", all.at("/items/0/sourceType").asText());
            assertTrue(all.at("/items/0/ruleSetVersionId").isNull());
            assertTrue(all.at("/items/0/versionNo").isNull());
            assertEquals(2, all.at("/items/0/flowVersionNo").intValue());
            for (String scope : List.of("CURRENT", "HISTORY")) assertEquals(1, get(http,
                "/decision-dictionaries/v1/rejection-reasons/" + reason + "/references?scope=" + scope).get("total").intValue());
            var deleted = http.deleteFlow(BASE + "/flows/" + first.get("flowId").asText() + "?expectedVersion=2", ACTOR, SESSION, key("DELETE_DECISION_FLOW", keys));
            assertEquals(200, deleted.statusCode(), deleted.body());
            assertEquals(2, get(http, "/decision-dictionaries/v1/rejection-reasons/" + reason + "/references?scope=DELETED").get("total").intValue());
        } finally { cleanup(code, keys); dictionaryTestMapper.deleteOwned(reason); }
    }

    @Test
    void test_saveAndDeleteRaceHasOneWinnerAndNoOrphanVersion() throws Exception {
        String code = decisionTestMapper.uniqueRuleSetCode();
        var keys = new ArrayList<OwnedCommandIdentity>();
        try (var http = new RiskDecisionHttpClient(port)) {
            var request = DecisionFlowRequestBuilder.template(code);
            var first = save(http, request, key("SAVE_DECISION_FLOW", keys));
            var edit = request.deepCopy().put("baseFlowVersionId", first.get("flowVersionId").asText()).put("expectedVersion", "1");
            String saveKey = key("SAVE_DECISION_FLOW", keys), deleteKey = key("DELETE_DECISION_FLOW", keys);
            var ready = new CyclicBarrier(2);
            var saved = CompletableFuture.supplyAsync(() -> concurrent(http, edit, saveKey, ready));
            var deleted = CompletableFuture.supplyAsync(() -> {
                try { ready.await(10, java.util.concurrent.TimeUnit.SECONDS);
                    return http.deleteFlow(BASE + "/flows/" + first.get("flowId").asText() + "?expectedVersion=1", ACTOR, SESSION, deleteKey).statusCode();
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            });
            assertEquals(List.of(200, 409), java.util.stream.Stream.of(saved.get(), deleted.get()).sorted().toList());
            assertEquals(saved.get() == 200 ? 2 : 1, flowTestMapper.versionCount(code));
            assertEquals(2L, ((Number) flowTestMapper.root(code).get("version")).longValue());
        } finally { cleanup(code, keys); }
    }

    @Test
    void test_legacyProjectionAndPersistedBindingReadRemainAvailableAfterRuleDeletion() throws Exception {
        String ruleCode = decisionTestMapper.uniqueRuleSetCode();
        var keys = new ArrayList<OwnedCommandIdentity>();
        try (var http = new RiskDecisionHttpClient(port)) {
            var stored = new LegacyRuleSetTestMapper(jdbc, new TransactionTemplate(transactionManager), identifiers)
                .insertFirstVersion(ruleCode, Long.parseLong(ACTOR), key("CREATE_RULE_SET_VERSION", keys));
            var rule = get(http, "/decision-rule-sets/v1/rule-sets/" + stored.ruleSetVersionId());
            String pointVersionId = rule.get("pointVersionId").asText();
            var before = decisionTestMapper.runtimeFactCounts();
            var legacy = get(http, BASE + "/legacy-point-versions/" + pointVersionId);
            assertEquals("LEGACY_POINT", legacy.get("sourceType").asText());
            assertFalse(legacy.has("flowId")); assertEquals(3, legacy.get("nodes").size());
            assertEquals("REVIEW", legacy.get("defaultDecision").asText());
            String nodeKey = legacy.at("/ruleSetReferences/0/nodeKey").asText();
            assertEquals(rule.get("ruleSetVersionId").asText(), legacy.at("/nodes/1/ruleSetVersionId").asText());
            var listing = get(http, "/decision-rule-sets/v1/rule-sets?pointCode=CHECKOUT_PREPARE&ruleSetCode=" + ruleCode);
            var root = listing.at("/items/0");
            var deleted = http.delete("/decision-rule-sets/v1/rule-sets/" + root.get("ruleSetId").asText(), ACTOR, SESSION,
                key("DELETE_RULE_SET", keys), java.util.Map.of("expectedVersion", root.get("managementVersion").asText()));
            assertEquals(200, deleted.statusCode(), deleted.body());
            var ref = get(http, BASE + "/rule-set-reference?sourceType=LEGACY_POINT&sourceVersionId=" + pointVersionId + "&nodeKey=" + nodeKey);
            assertEquals("DELETED", ref.get("ruleSetStatus").asText());
            assertEquals(rule.get("ruleSetVersionId").asText(), ref.at("/ruleSet/ruleSetVersionId").asText());
            assertProblem(http.get(BASE + "/rule-set-reference?sourceType=LEGACY_POINT&sourceVersionId=" + pointVersionId + "&nodeKey=legacy_default", ACTOR, SESSION), 404, "RISK_DECISION_CONFIGURATION_NOT_FOUND");
            assertEquals(before, decisionTestMapper.runtimeFactCounts());
        } finally { decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(ruleCode), keys); }
    }

    @Test
    void test_reasonReferencesUseOneMixedPageAcrossRuleAndFlowVersions() throws Exception {
        String code = decisionTestMapper.uniqueRuleSetCode(), ruleCode = decisionTestMapper.uniqueRuleSetCode();
        String reason = "RCO17_" + java.util.UUID.randomUUID().toString().replace("-", "").toUpperCase();
        var keys = new ArrayList<OwnedCommandIdentity>();
        try (var http = new RiskDecisionHttpClient(port)) {
            var created = http.dictionary("POST", "/decision-dictionaries/v1/rejection-reasons", ACTOR, SESSION,
                "{\"reasonCode\":\"" + reason + "\",\"name\":\"混合来源\",\"description\":null,\"defaultMessage\":\"人工说明\"}");
            assertEquals(200, created.statusCode(), created.body());
            var rule = com.paytech.risk.test.builder.DecisionRuleSetRequestBuilder.currentFirstVersion(ruleCode);
            // 每个来源均通过公开写入生成，查询必须统一排序后再分页。
            var ruleJson = (ObjectNode) tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(rule);
            ((ObjectNode) ruleJson.at("/rules/0")).put("reasonCode", reason).put("reasonMessage", "原因说明");
            ((ObjectNode) ruleJson.at("/rules/0/action")).put("actionCode", "DENY_CHECKOUT");
            ((ObjectNode) ruleJson.at("/rules/0")).putArray("additionalActions");
            var savedRule = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, key("CREATE_RULE_SET_VERSION", keys), ruleJson);
            assertEquals(200, savedRule.statusCode(), savedRule.body());
            var request = DecisionFlowRequestBuilder.template(code);
            ((ObjectNode) request.at("/graph/nodes/1")).put("decisionCode", "DENY").put("reasonCode", reason).put("reasonMessage", "流程原因");
            for (int i = 0; i < 10; i++) {
                var saved = save(http, request, key("SAVE_DECISION_FLOW", keys));
                request.put("baseFlowVersionId", saved.get("flowVersionId").asText()).put("expectedVersion", saved.get("version").asText());
            }
            String path = "/decision-dictionaries/v1/rejection-reasons/" + reason + "/references?scope=ALL&size=10&page=";
            var first = get(http, path + "1"); var second = get(http, path + "2");
            assertEquals(11, first.get("total").intValue()); assertEquals(10, first.get("items").size());
            assertEquals("FLOW_END", first.at("/items/0/sourceType").asText());
            assertEquals(10, first.at("/items/0/flowVersionNo").intValue());
            assertEquals(1, second.get("items").size()); assertEquals("RULE_SET", second.at("/items/0/sourceType").asText());
            assertTrue(second.at("/items/0/flowVersionId").isNull());
            assertEquals(2, get(http, "/decision-dictionaries/v1/rejection-reasons/" + reason + "/references?scope=CURRENT").get("total").intValue());
        } finally { cleanup(code, keys); decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(ruleCode), List.of()); dictionaryTestMapper.deleteOwned(reason); }
    }

    private static int concurrent(RiskDecisionHttpClient http, ObjectNode body, String key, CyclicBarrier ready) {
        try {
            ready.await(10, java.util.concurrent.TimeUnit.SECONDS);
            return http.post(BASE + "/flows", ACTOR, SESSION, key, body).statusCode();
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private JsonNode save(RiskDecisionHttpClient http, ObjectNode body, String key) throws Exception {
        var response = http.post(BASE + "/flows", ACTOR, SESSION, key, body);
        assertEquals(200, response.statusCode(), response.body());
        return http.json(response);
    }

    private JsonNode get(RiskDecisionHttpClient http, String path) throws Exception {
        var response = http.get(path, ACTOR, SESSION);
        assertEquals(200, response.statusCode(), response.body());
        return http.json(response);
    }

    private String key(String operation, List<OwnedCommandIdentity> keys) {
        String key = decisionTestMapper.uniqueNamespace();
        keys.add(new OwnedCommandIdentity("OPERATIONS_ACCOUNT:" + ACTOR, operation, key));
        return key;
    }

    private void cleanup(String code, List<OwnedCommandIdentity> keys) {
        flowTestMapper.clean(code);
        for (OwnedCommandIdentity key : keys) decisionTestMapper.deleteOwnedCommand(key.callerScope(), key.operationType(), key.idempotencyKey());
    }
}
