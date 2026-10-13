package com.paytech.risk.auto.ext.http;

import static org.junit.jupiter.api.Assertions.*;

import com.paytech.risk.test.base.RiskServiceAutoTestBase;
import com.paytech.risk.test.builder.DecisionRuleSetRequestBuilder;
import com.paytech.risk.test.decision.RiskDecisionHttpClient;
import com.paytech.risk.test.decision.RiskDecisionTestMapper.OwnedCommandIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 普通规则配置不再依赖旧顺序编排；由真实 HTTP 验证保存、读取和生命周期。 */
class RuleSetInputContractHttpAutoTest extends RiskServiceAutoTestBase {
    private static final String ACTOR = "910000000000000093";
    private static final String SESSION = "risk-auto-input-contract";

    @Test
    void test_saveReadRetireFreezeAndReplayWithoutCreatingLegacyComposition() throws Exception {
        String code = decisionTestMapper.uniqueRuleSetCode();
        var commands = new ArrayList<OwnedCommandIdentity>();
        try (var http = new RiskDecisionHttpClient(port)) {
            var body = DecisionRuleSetRequestBuilder.currentFirstVersion(code);
            String createKey = key("CREATE_RULE_SET_VERSION", commands);
            var first = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, createKey, body);
            assertEquals(200, first.statusCode(), first.body());
            var firstRoot = http.json(first);
            assertTrue(firstRoot.has("pointVersionId"));
            assertTrue(firstRoot.get("pointVersionId").isNull());
            String firstId = firstRoot.path("ruleSetVersionId").asText();
            var second = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION,
                key("CREATE_RULE_SET_VERSION", commands), DecisionRuleSetRequestBuilder.currentNextVersion(code,
                    firstId, firstRoot.path("contentHash").asText()));
            assertEquals(200, second.statusCode(), second.body());
            var root = http.json(second);
            String secondId = root.path("ruleSetVersionId").asText();
            var history = http.get("/decision-rule-sets/v1/rule-sets/" + firstId, ACTOR, SESSION);
            assertEquals(200, history.statusCode(), history.body());
            assertEquals("RETIRED", http.json(history).path("status").asText());
            com.paytech.risk.test.decision.RiskDecisionHttpClient.assertProblem(http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, createKey, body), 409, "CONCURRENT_REQUEST");
            String freezeKey = key("FREEZE_RULE_SET_VERSION", commands);
            var freezeBody = Map.of("expectedContentHash", root.path("contentHash").asText());
            var frozen = http.post("/decision-rule-sets/v1/rule-sets/" + secondId + "/freeze", ACTOR, SESSION, freezeKey, freezeBody);
            RiskDecisionHttpClient.assertProblem(frozen, 400, "BUSINESS_CHECK_FAILED");
            var current = http.get("/decision-rule-sets/v1/rule-sets/" + secondId, ACTOR, SESSION);
            assertEquals("DRAFT", http.json(current).path("status").asText());
            var candidate = http.post("/decision-releases/v1/candidates", ACTOR, SESSION,
                key("CREATE_TEST_DECISION_RELEASE", commands), Map.of("ruleSetVersionId", secondId,
                    "expectedRuleSetContentHash", root.path("contentHash").asText()));
            assertEquals(400, candidate.statusCode(), candidate.body());
            assertEquals("BUSINESS_CHECK_FAILED", http.json(candidate).path("code").asText());
            var counts = decisionTestMapper.snapshotCounts(code);
            assertEquals(2, counts.ruleSetVersions());
            assertEquals(0, counts.pointVersions());
            assertEquals(0, counts.inputFields());
            assertEquals(0, counts.pointRuleSetBindings());
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(code), commands);
        }
    }

    private String key(String operation, List<OwnedCommandIdentity> commands) {
        String key = decisionTestMapper.uniqueNamespace();
        commands.add(new OwnedCommandIdentity("OPERATIONS_ACCOUNT:" + ACTOR, operation, key));
        return key;
    }
}
