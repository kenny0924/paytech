package com.paytech.risk.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paytech.risk.test.base.RiskServiceAutoTestBase;
import com.paytech.risk.test.builder.DecisionRuleSetRequestBuilder;
import com.paytech.risk.test.decision.RiskDecisionHttpClient;
import com.paytech.risk.test.decision.RiskDecisionTestMapper.OwnedCommandIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 配置命令必须完整保存附加动作，但不能因此获得执行资格。 */
class RuleActionConfigurationHttpAutoTest extends RiskServiceAutoTestBase {

    private static final String ACTOR = "910000000000000091";
    private static final String SESSION = "risk-auto-action-configuration";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void test_actionsMayBeOmittedInConfigurationButCannotFreeze() throws Exception {
        String code = decisionTestMapper.uniqueRuleSetCode();
        var identities = new java.util.ArrayList<OwnedCommandIdentity>();
        ObjectNode request = configured(code);
        ((ObjectNode) request.at("/rules/0")).putNull("action");
        try (RiskDecisionHttpClient http = new RiskDecisionHttpClient(port)) {
            String commandKey = key("CREATE_RULE_SET_VERSION", identities);
            var saved = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, commandKey, request);
            assertEquals(200, saved.statusCode(), saved.body());
            var result = http.json(saved);
            String versionId = result.path("ruleSetVersionId").asText();
            var detail = http.get("/decision-rule-sets/v1/rule-sets/" + versionId, ACTOR, SESSION);
            assertEquals(200, detail.statusCode(), detail.body());
            assertTrue(http.json(detail).at("/rules/0/action").isNull());
            assertEquals(0, http.json(detail).at("/rules/0/additionalActions").size());
            com.paytech.risk.test.decision.RiskDecisionHttpClient.assertProblem(http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, commandKey, request), 409, "CONCURRENT_REQUEST");
            var frozen = http.post("/decision-rule-sets/v1/rule-sets/" + versionId + "/freeze", ACTOR, SESSION,
                key("FREEZE_RULE_SET_VERSION", identities),
                java.util.Map.of("expectedContentHash", result.path("contentHash").asText()));
            assertEquals(400, frozen.statusCode(), frozen.body());
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(code), identities);
        }
    }

    @Test
    void test_terminalConfigurationRetainsManualMessageButHasNoLegacyCandidate() throws Exception {
        String code = decisionTestMapper.uniqueRuleSetCode();
        String reasonCode = dictionaryCode("REASON");
        var identities = new java.util.ArrayList<OwnedCommandIdentity>();
        ObjectNode request = configured(code);
        ObjectNode rule = (ObjectNode) request.at("/rules/0");
        ((ObjectNode) rule.get("action")).put("actionCode", "DENY_CHECKOUT");
        rule.put("reasonCode", reasonCode); rule.put("reasonMessage", "人工填写拒绝说明");
        try (RiskDecisionHttpClient http = new RiskDecisionHttpClient(port)) {
            prepareDictionary(http, "rejection-reasons", reasonCode);
            String key = key("CREATE_RULE_SET_VERSION", identities);
            var saved = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, key, request);
            assertEquals(200, saved.statusCode(), saved.body());
            var root = http.json(saved); String id = root.path("ruleSetVersionId").asText();
            var detail = http.get("/decision-rule-sets/v1/rule-sets/" + id, ACTOR, SESSION);
            assertEquals("人工填写拒绝说明", http.json(detail).at("/rules/0/reasonMessage").asText());
            var changed = request.deepCopy(); ((ObjectNode) changed.at("/rules/0")).put("reasonMessage", "另一说明");
            assertEquals(409, http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, key, changed).statusCode());
            var frozen = http.post("/decision-rule-sets/v1/rule-sets/" + id + "/freeze", ACTOR, SESSION,
                key("FREEZE_RULE_SET_VERSION", identities), java.util.Map.of("expectedContentHash", root.path("contentHash").asText()));
            RiskDecisionHttpClient.assertProblem(frozen, 400, "BUSINESS_CHECK_FAILED");
            var candidate = http.post("/decision-releases/v1/candidates", ACTOR, SESSION,
                key("CREATE_TEST_DECISION_RELEASE", identities), java.util.Map.of("ruleSetVersionId", id,
                    "expectedRuleSetContentHash", root.path("contentHash").asText()));
            assertEquals(400, candidate.statusCode(), candidate.body());
            assertEquals("BUSINESS_CHECK_FAILED", http.json(candidate).path("code").asText());
            com.paytech.risk.test.decision.RiskDecisionHttpClient.assertProblem(http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, key, request), 409, "CONCURRENT_REQUEST");
        } finally { decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(code), identities); dictionaryTestMapper.deleteOwned(reasonCode); }
    }

    @Test
    void test_invalidTargetDurationReasonAndActionShapesNeverCreateConfiguration() throws Exception {
        for (String invalid : List.of("legacy-empty", "unknown-target", "consumer-target", "zero-duration", "negative-duration",
            "too-long", "number-literal", "target-literal", "sequence-gap", "terminal-as-extra", "unknown-parameter",
            "missing-reason", "missing-message", "non-reject-message")) {
            String code = decisionTestMapper.uniqueRuleSetCode();
            var identities = new java.util.ArrayList<OwnedCommandIdentity>();
            ObjectNode body = configured(code); ObjectNode rule = (ObjectNode) body.at("/rules/0");
            var extra = JSON.createObjectNode().put("sequenceNo", 1).put("actionCode", "APPLY_COOLDOWN")
                .put("actionVersionNo", 1).put("targetBindingCode", "CURRENT_CLIENT_V1").put("subjectVariableCode", "INPUT_CLIENT_ID");
            var parameter = JSON.createObjectNode().put("parameterCode", "DURATION").put("valueSequence", 1)
                .put("sourceType", "LITERAL").putNull("fieldCode").put("valueType", "DURATION").put("literalValue", "1800000");
            extra.set("parameters", JSON.createArrayNode().add(parameter));
            rule.set("additionalActions", JSON.createArrayNode().add(extra)); rule.putNull("action");
            switch (invalid) {
                case "legacy-empty" -> { body.putNull("operandFormat"); rule.remove("additionalActions"); rule.remove("reasonMessage"); }
                case "unknown-target" -> extra.put("targetBindingCode", "UNKNOWN_V1");
                case "consumer-target" -> extra.put("targetBindingCode", "CURRENT_CONSUMER_V1");
                case "zero-duration" -> parameter.put("literalValue", "0");
                case "negative-duration" -> parameter.put("literalValue", "-1");
                case "too-long" -> parameter.put("literalValue", "31536000001");
                case "number-literal" -> parameter.put("literalValue", 1800000);
                case "target-literal" -> { parameter.put("parameterCode", "SUBJECT_ID"); parameter.put("valueType", "STRING"); }
                case "sequence-gap" -> extra.put("sequenceNo", 2);
                case "terminal-as-extra" -> extra.put("actionCode", "DENY_CHECKOUT");
                case "unknown-parameter" -> parameter.put("parameterCode", "OTHER");
                case "missing-reason", "missing-message" -> {
                    rule.set("action", JSON.createObjectNode().put("actionCode", "DENY_CHECKOUT").put("actionVersionNo", 1));
                    rule.put("reasonCode", "MANUAL_REASON"); rule.put("reasonMessage", "说明");
                    rule.putNull(invalid.equals("missing-reason") ? "reasonCode" : "reasonMessage");
                }
                case "non-reject-message" -> rule.put("reasonMessage", "禁止隐式清空");
                default -> throw new AssertionError(invalid);
            }
            try (RiskDecisionHttpClient http = new RiskDecisionHttpClient(port)) {
                var result = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION,
                    key("CREATE_RULE_SET_VERSION", identities), body);
                assertEquals(400, result.statusCode(), invalid + ": " + result.body());
                assertEquals(0, decisionTestMapper.ruleSetVersionCount(code), invalid);
            } finally { decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(code), identities); }
        }
    }

    @Test
    void test_duplicateTagCodesNormalizeAndReplayWithTheSameHash() throws Exception {
        String firstTag = dictionaryCode("A"); String secondTag = dictionaryCode("B");
        String code = decisionTestMapper.uniqueRuleSetCode(); var identities = new java.util.ArrayList<OwnedCommandIdentity>();
        ObjectNode body = configured(code); ObjectNode rule = (ObjectNode) body.at("/rules/0");
        rule.set("additionalActions", JSON.readTree("""
            [{"sequenceNo":1,"actionCode":"ADD_RISK_TAG","actionVersionNo":1,"targetBindingCode":"CURRENT_CLIENT_V1","subjectVariableCode":"INPUT_CLIENT_ID",
              "parameters":[{"parameterCode":"TAG","valueSequence":1,"sourceType":"LITERAL","fieldCode":null,"valueType":"STRING","literalValue":"SECOND"},
              {"parameterCode":"TAG","valueSequence":2,"sourceType":"LITERAL","fieldCode":null,"valueType":"STRING","literalValue":"FIRST"},
              {"parameterCode":"TAG","valueSequence":3,"sourceType":"LITERAL","fieldCode":null,"valueType":"STRING","literalValue":"SECOND"}]}]
            """.replace("SECOND", secondTag).replace("FIRST", firstTag)));
        try (RiskDecisionHttpClient http = new RiskDecisionHttpClient(port)) {
            prepareDictionary(http, "subject-tags", firstTag); prepareDictionary(http, "subject-tags", secondTag);
            String key = key("CREATE_RULE_SET_VERSION", identities);
            var saved = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, key, body);
            assertEquals(200, saved.statusCode(), saved.body());
            var detail = http.json(http.get("/decision-rule-sets/v1/rule-sets/" + http.json(saved).path("ruleSetVersionId").asText(), ACTOR, SESSION));
            var parameters = detail.at("/rules/0/additionalActions/0/parameters");
            assertEquals(2, parameters.size()); assertEquals(firstTag, parameters.get(0).path("literalValue").asText());
            rule.set("additionalActions", detail.at("/rules/0/additionalActions"));
            com.paytech.risk.test.decision.RiskDecisionHttpClient.assertProblem(http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, key, body), 409, "CONCURRENT_REQUEST");
        } finally { decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(code), identities); dictionaryTestMapper.deleteOwned(firstTag); dictionaryTestMapper.deleteOwned(secondTag); }
    }

    private ObjectNode configured(String code) {
        ObjectNode body = JSON.valueToTree(DecisionRuleSetRequestBuilder.currentFirstVersion(code));
        ObjectNode rule = (ObjectNode) body.at("/rules/0"); rule.putNull("reasonMessage");
        rule.set("additionalActions", JSON.createArrayNode()); return body;
    }

    private String key(String operation, List<OwnedCommandIdentity> identities) {
        String key = decisionTestMapper.uniqueNamespace();
        identities.add(new OwnedCommandIdentity("OPERATIONS_ACCOUNT:" + ACTOR, operation, key)); return key;
    }

    @Test
    void test_additionalActionsRoundTripAndReplayWithoutExecution() throws Exception {
        String code = decisionTestMapper.uniqueRuleSetCode();
        String tag = dictionaryCode("TAG");
        String key = decisionTestMapper.uniqueNamespace();
        String freezeKey = decisionTestMapper.uniqueNamespace();
        JsonMapper json = JsonMapper.builder().build();
        ObjectNode body = json.valueToTree(DecisionRuleSetRequestBuilder.currentFirstVersion(code));
        ObjectNode rule = (ObjectNode) body.get("rules").get(0);
        rule.putNull("action");
        rule.putNull("reasonMessage");
        rule.set("additionalActions", json.readTree("""
            [{"sequenceNo":1,"actionCode":"APPLY_COOLDOWN","actionVersionNo":1,
              "targetBindingCode":"CURRENT_CLIENT_V1","subjectVariableCode":"INPUT_CLIENT_ID","parameters":[
                {"parameterCode":"DURATION","valueSequence":1,"sourceType":"LITERAL",
                 "fieldCode":null,"valueType":"DURATION","literalValue":"1800000"}]},
             {"sequenceNo":2,"actionCode":"ADD_RISK_TAG","actionVersionNo":1,
              "targetBindingCode":"CURRENT_CLIENT_V1","subjectVariableCode":"INPUT_CLIENT_ID","parameters":[
                {"parameterCode":"TAG","valueSequence":1,"sourceType":"LITERAL",
                 "fieldCode":null,"valueType":"STRING","literalValue":"REQUIRES_ATTENTION"}]}]
            """.replace("REQUIRES_ATTENTION", tag)));
        var runtimeBefore = decisionTestMapper.runtimeFactCounts();
        try (RiskDecisionHttpClient http = new RiskDecisionHttpClient(port)) {
            prepareDictionary(http, "subject-tags", tag);
            var created = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, key, body);
            assertEquals(200, created.statusCode(), created.body());
            var replay = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, key, body);
            com.paytech.risk.test.decision.RiskDecisionHttpClient.assertProblem(replay, 409, "CONCURRENT_REQUEST");
            var result = http.json(created);
            String id = result.get("ruleSetVersionId").asText();
            var detail = http.get("/decision-rule-sets/v1/rule-sets/" + id, ACTOR, SESSION);
            assertEquals(200, detail.statusCode(), detail.body());
            var saved = http.json(detail).get("rules").get(0);
            assertTrue(saved.get("action").isNull());
            assertEquals(rule.get("additionalActions"), saved.get("additionalActions"));
            var freeze = http.post("/decision-rule-sets/v1/rule-sets/" + id + "/freeze", ACTOR,
                SESSION, freezeKey, java.util.Map.of("expectedContentHash", result.get("contentHash").asText()));
            assertEquals(400, freeze.statusCode(), freeze.body());
            var after = http.get("/decision-rule-sets/v1/rule-sets/" + id, ACTOR, SESSION);
            assertEquals("DRAFT", http.json(after).get("status").asText());
            assertEquals(runtimeBefore, decisionTestMapper.runtimeFactCounts());
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(code), List.of(
                new OwnedCommandIdentity("OPERATIONS_ACCOUNT:" + ACTOR, "CREATE_RULE_SET_VERSION", key),
                new OwnedCommandIdentity("OPERATIONS_ACCOUNT:" + ACTOR, "FREEZE_RULE_SET_VERSION", freezeKey)));
            dictionaryTestMapper.deleteOwned(tag);
        }
    }
    /** 生成本用例独占的字典编码，避免使用或清理运营目录。 */
    private String dictionaryCode(String label) {
        return "RCO16_" + label + "_" + java.util.UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
    }
    /** 通过公开 HTTP 准备新动作选择需要的启用字典，不直接插入业务数据。 */
    private void prepareDictionary(RiskDecisionHttpClient http, String kind, String code) throws Exception {
        ObjectNode body = JSON.createObjectNode().put(kind.equals("rejection-reasons") ? "reasonCode" : "tagCode", code)
            .put("name", "测试配置字典").putNull("description");
        if (kind.equals("rejection-reasons")) body.put("defaultMessage", "字典默认文案");
        var response = http.dictionary("POST", "/decision-dictionaries/v1/" + kind, ACTOR, SESSION, body.toString());
        assertEquals(200, response.statusCode(), response.body());
    }

}
