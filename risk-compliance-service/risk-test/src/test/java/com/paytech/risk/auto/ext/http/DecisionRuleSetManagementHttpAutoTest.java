package com.paytech.risk.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.CreateRuleSetVersionResponseBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.FreezeRuleSetVersionRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.RuleSetDetailResponseBody;
import com.paytech.risk.test.base.RiskServiceAutoTestBase;
import com.paytech.risk.test.builder.DecisionRuleSetRequestBuilder;
import com.paytech.risk.test.decision.RiskDecisionHttpClient;
import com.paytech.risk.test.decision.RiskDecisionTestMapper.DecisionSnapshotCounts;
import com.paytech.risk.test.decision.RiskDecisionTestMapper.OwnedCommandIdentity;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 从真实 HTTP 入口验证 Rule Set 不可变快照与服务级幂等持久化。 */
class DecisionRuleSetManagementHttpAutoTest extends RiskServiceAutoTestBase {

    private static final String ACTOR = "910000000000000001";
    private static final String CALLER_SCOPE = "OPERATIONS_ACCOUNT:" + ACTOR;
    private static final String SESSION = "risk-auto-rule-set-session";

    @Test
    void test_authoringContextExposesOnlyApprovedCheckoutCatalog() throws Exception {
        try (RiskDecisionHttpClient http = new RiskDecisionHttpClient(port)) {
            HttpResponse<String> response = http.get(
                "/decision-rule-sets/v1/authoring-context/CHECKOUT_PREPARE", ACTOR, SESSION);
            assertEquals(200, response.statusCode(), response.body());
            var context = http.json(response);
            assertEquals("RISK_VARIABLE_AUTHORING", context.get("descriptorCode").asText());
            assertEquals("CHECKOUT_PREPARE", context.get("point").get("pointCode").asText());
            assertEquals(10, context.get("fields").size());
            assertEquals(11, context.get("operators").size());
            assertEquals(5, context.get("actions").size());
            assertEquals(0, context.get("features").size());
            assertEquals(1, context.get("targetBindings").size());
            assertEquals("CURRENT_CLIENT_V1", context.at("/targetBindings/0/bindingCode").asText());
            assertEquals(4, context.get("actionParameters").size());
            assertEquals(0, context.get("obligations").size());
            assertEquals(0, context.get("limitBindings").size());

            HttpResponse<String> missing = http.get(
                "/decision-rule-sets/v1/authoring-context/UNKNOWN_POINT", ACTOR, SESSION);
            RiskDecisionHttpClient.assertProblem(
                missing, 404, "RISK_DECISION_CONFIGURATION_NOT_FOUND");
        }
    }

    @Test
    void test_createPersistsCompleteSnapshotAndReplaysStableResponse() throws Exception {
        String ruleSetCode = decisionTestMapper.uniqueRuleSetCode();
        String idempotencyKey = decisionTestMapper.uniqueNamespace();
        var requestBody = DecisionRuleSetRequestBuilder.currentFirstVersion(ruleSetCode);
        try (RiskDecisionHttpClient http = new RiskDecisionHttpClient(port)) {
            HttpResponse<String> first = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION,
                idempotencyKey, requestBody);
            assertEquals(200, first.statusCode(), first.body());
            CreateRuleSetVersionResponseBody created = http.body(first, CreateRuleSetVersionResponseBody.class);

            HttpResponse<String> replay = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION,
                idempotencyKey, requestBody);
            assertEquals(200, replay.statusCode(), replay.body());
            assertEquals(first.body(), replay.body());

            HttpResponse<String> detailResponse = http.get(
                "/decision-rule-sets/v1/rule-sets/" + created.ruleSetVersionId(), ACTOR, SESSION);
            assertEquals(200, detailResponse.statusCode(), detailResponse.body());
            RuleSetDetailResponseBody detail = http.body(detailResponse, RuleSetDetailResponseBody.class);
            assertEquals(ruleSetCode, detail.ruleSetCode());
            var requestedRule = requestBody.rules().getFirst();
            var persistedRule = detail.rules().getFirst();
            assertEquals(requestedRule.ruleCode(), persistedRule.ruleCode());
            assertEquals(requestedRule.ruleName(), persistedRule.ruleName());
            assertEquals(requestedRule.priorityNo(), persistedRule.priorityNo());
            assertEquals(requestedRule.enabledFlag(), persistedRule.enabledFlag());
            assertEquals(requestedRule.reasonCode(), persistedRule.reasonCode());
            assertEquals(requestedRule.action(), persistedRule.action());
            assertEquals(requestedRule.conditionNodes().getFirst().sequenceNo(),
                persistedRule.conditionNodes().getFirst().sequenceNo());
            assertEquals(requestedRule.conditionNodes().getFirst().nodeType(),
                persistedRule.conditionNodes().getFirst().nodeType());
            assertEquals(requestedRule.conditionNodes().getFirst().comparisonOperator(),
                persistedRule.conditionNodes().getFirst().comparisonOperator());
            assertEquals(requestedRule.conditionNodes().getFirst().operands(),
                persistedRule.conditionNodes().getFirst().operands());

            DecisionSnapshotCounts counts = decisionTestMapper.snapshotCounts(ruleSetCode);
            assertEquals(new DecisionSnapshotCounts(1, 0, 0, 0, 1, 1, 2, 1), counts);
            assertEquals(1, decisionTestMapper.ownedCommandCount(
                "OPERATIONS_ACCOUNT:" + ACTOR, "CREATE_RULE_SET_VERSION", idempotencyKey));
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(ruleSetCode),
                List.of(command("CREATE_RULE_SET_VERSION", idempotencyKey)));
        }
    }

    @Test
    void test_nextVersionRetiresPreviousSnapshotAndCannotFreezeVariableConfiguration() throws Exception {
        String ruleSetCode = decisionTestMapper.uniqueRuleSetCode();
        String createFirstKey = decisionTestMapper.uniqueNamespace();
        String createNextKey = decisionTestMapper.uniqueNamespace();
        String freezeKey = decisionTestMapper.uniqueNamespace();
        try (RiskDecisionHttpClient http = new RiskDecisionHttpClient(port)) {
            HttpResponse<String> firstResponse = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION,
                createFirstKey, DecisionRuleSetRequestBuilder.currentFirstVersion(ruleSetCode));
            assertEquals(200, firstResponse.statusCode(), firstResponse.body());
            CreateRuleSetVersionResponseBody first = http.body(
                firstResponse, CreateRuleSetVersionResponseBody.class);

            HttpResponse<String> nextResponse = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION,
                createNextKey, DecisionRuleSetRequestBuilder.currentNextVersion(
                    ruleSetCode, first.ruleSetVersionId(), first.contentHash()));
            assertEquals(200, nextResponse.statusCode(), nextResponse.body());
            CreateRuleSetVersionResponseBody next = http.body(
                nextResponse, CreateRuleSetVersionResponseBody.class);
            assertEquals(2, next.versionNo());
            assertEquals("DRAFT", next.status());
            assertNotEquals(first.ruleSetVersionId(), next.ruleSetVersionId());
            org.junit.jupiter.api.Assertions.assertNull(first.pointVersionId());
            org.junit.jupiter.api.Assertions.assertNull(next.pointVersionId());
            assertNotEquals(first.contentHash(), next.contentHash());

            HttpResponse<String> retiredResponse = http.get(
                "/decision-rule-sets/v1/rule-sets/" + first.ruleSetVersionId(), ACTOR, SESSION);
            assertEquals(200, retiredResponse.statusCode(), retiredResponse.body());
            assertEquals("RETIRED", http.body(retiredResponse, RuleSetDetailResponseBody.class).status());

            HttpResponse<String> freezeResponse = http.post(
                "/decision-rule-sets/v1/rule-sets/" + next.ruleSetVersionId() + "/freeze", ACTOR, SESSION,
                freezeKey, new FreezeRuleSetVersionRequestBody(next.contentHash()));
            RiskDecisionHttpClient.assertProblem(freezeResponse, 400, "BUSINESS_CHECK_FAILED");
            assertEquals(0, decisionTestMapper.ownedCommandCount(CALLER_SCOPE, "FREEZE_RULE_SET_VERSION", freezeKey));

            HttpResponse<String> listResponse = http.get(
                "/decision-rule-sets/v1/rule-sets?page=1&size=10&pointCode=CHECKOUT_PREPARE&ruleSetCode="
                    + ruleSetCode,
                ACTOR, SESSION);
            assertEquals(200, listResponse.statusCode(), listResponse.body());
            var page = http.json(listResponse);
            assertEquals(1, page.get("total").asLong());
            assertEquals(1, page.get("items").size());
            assertEquals(next.ruleSetVersionId(), page.get("items").get(0).get("ruleSetVersionId").asText());
            assertEquals("DRAFT", page.get("items").get(0).get("status").asText());
            org.junit.jupiter.api.Assertions.assertTrue(page.get("items").get(0).hasNonNull("ruleSetId"));
            assertEquals("2", page.get("items").get(0).get("managementVersion").asText());
            assertFalse(page.get("items").get(0).get("inUse").asBoolean());

            assertEquals(new DecisionSnapshotCounts(2, 0, 0, 0, 2, 2, 4, 2),
                decisionTestMapper.snapshotCounts(ruleSetCode));
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(ruleSetCode), List.of(
                command("CREATE_RULE_SET_VERSION", createFirstKey),
                command("CREATE_RULE_SET_VERSION", createNextKey),
                command("FREEZE_RULE_SET_VERSION", freezeKey)));
        }
    }

    @Test
    void test_sameKeyRejectsDifferentPayloadWhileCaseVariantRemainsDistinct() throws Exception {
        String firstRuleSetCode = decisionTestMapper.uniqueRuleSetCode();
        String secondRuleSetCode = decisionTestMapper.uniqueRuleSetCode();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String lowerKey = "risk-auto-key" + suffix;
        String caseVariantKey = "risk-auto-Key" + suffix;
        try (RiskDecisionHttpClient http = new RiskDecisionHttpClient(port)) {
            HttpResponse<String> first = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION,
                lowerKey, DecisionRuleSetRequestBuilder.currentFirstVersion(firstRuleSetCode, "AE"));
            assertEquals(200, first.statusCode(), first.body());

            HttpResponse<String> conflict = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION,
                lowerKey, DecisionRuleSetRequestBuilder.currentFirstVersion(firstRuleSetCode, "SA"));
            RiskDecisionHttpClient.assertProblem(conflict, 409, "CONCURRENT_REQUEST");

            HttpResponse<String> caseVariant = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION,
                caseVariantKey, DecisionRuleSetRequestBuilder.currentFirstVersion(secondRuleSetCode));
            assertEquals(200, caseVariant.statusCode(), caseVariant.body());
            assertEquals(1, decisionTestMapper.ownedCommandBinaryCount(
                "OPERATIONS_ACCOUNT:" + ACTOR, "CREATE_RULE_SET_VERSION", lowerKey));
            assertEquals(1, decisionTestMapper.ownedCommandBinaryCount(
                "OPERATIONS_ACCOUNT:" + ACTOR, "CREATE_RULE_SET_VERSION", caseVariantKey));
            assertEquals(new DecisionSnapshotCounts(1, 0, 0, 0, 1, 1, 2, 1),
                decisionTestMapper.snapshotCounts(firstRuleSetCode));
            assertEquals(new DecisionSnapshotCounts(1, 0, 0, 0, 1, 1, 2, 1),
                decisionTestMapper.snapshotCounts(secondRuleSetCode));
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(firstRuleSetCode, secondRuleSetCode),
                List.of(command("CREATE_RULE_SET_VERSION", lowerKey),
                    command("CREATE_RULE_SET_VERSION", caseVariantKey)));
        }
    }

    @Test
    void test_unsafeChangeReasonReturns400WithoutEchoOrDatabaseWrites() throws Exception {
        List<String> rejected = List.of(
            "release\u0000reason",
            "release\u200Breason",
            "{\"ticket\":\"OPS-123\"}",
            "[\"rotate\",\"key\"]",
            "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature",
            "api_key=sk_test_1234567890",
            "client-secret: value123",
            "password = hunter2",
            "access_token='opaque-value'",
            "card candidate 4111-1111-1111-1111",
            "Bearer abcdefghijklmnop",
            "card candidate 4111\u00A01111\u00A01111\u00A01111",
            "card candidate 4111\u20111111\u20111111\u20111111",
            "card candidate 4111\u20131111\u20131111\u20131111",
            "contact alice@example.com",
            "phone=0501234567",
            "电话: 0501234567",
            "手机=13800138000",
            "手机号: +86 138 0013 8000",
            "call +14155552671",
            "call 050-123-4567",
            "call (415) 555-2671"
        );
        ArrayList<String> ruleSetCodes = new ArrayList<>();
        ArrayList<OwnedCommandIdentity> commands = new ArrayList<>();
        try (RiskDecisionHttpClient http = new RiskDecisionHttpClient(port)) {
            for (String raw : rejected) {
                String ruleSetCode = decisionTestMapper.uniqueRuleSetCode();
                String idempotencyKey = decisionTestMapper.uniqueNamespace();
                ruleSetCodes.add(ruleSetCode);
                commands.add(command("CREATE_RULE_SET_VERSION", idempotencyKey));
                HttpResponse<String> response = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION,
                    idempotencyKey,
                    DecisionRuleSetRequestBuilder.currentFirstVersionWithChangeReason(ruleSetCode, raw));

                RiskDecisionHttpClient.assertProblem(response, 400, "INVALID_REQUEST");
                assertFalse(response.body().contains(raw));
                assertEquals(0, decisionTestMapper.snapshotCounts(ruleSetCode).total());
                assertEquals(0, decisionTestMapper.ownedCommandCount(
                    CALLER_SCOPE, "CREATE_RULE_SET_VERSION", idempotencyKey));
            }
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(ruleSetCodes, commands);
        }
    }

    private static OwnedCommandIdentity command(String operationType, String idempotencyKey) {
        return new OwnedCommandIdentity(CALLER_SCOPE, operationType, idempotencyKey);
    }
}
