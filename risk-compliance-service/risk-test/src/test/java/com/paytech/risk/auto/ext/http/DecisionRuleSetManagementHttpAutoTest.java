package com.paytech.risk.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.paytech.risk.api.decision.response.CreateRuleSetVersionResponseBody;
import com.paytech.risk.api.decision.request.FreezeRuleSetVersionRequestBody;
import com.paytech.risk.api.decision.response.RuleSetDetailResponseBody;
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
    private static final tools.jackson.databind.json.JsonMapper OPERAND_JSON =
        tools.jackson.databind.json.JsonMapper.builder().build();


    private static final String ACTOR = "910000000000000001";
    private static final String CALLER_SCOPE = "OPERATIONS_ACCOUNT:" + ACTOR;
    private static final String SESSION = "risk-auto-rule-set-session";

    /** 新格式创建与冻结缺少任一必填键时在 HTTP 入口拒绝，不把缺失解释为显式 null。 */
    @Test
    void test_missingRequiredKeysAreRejected() throws Exception {
        try (RiskDecisionHttpClient http = new RiskDecisionHttpClient(port)) {
            String missingBaseHash = "{\"pointCode\":\"CHECKOUT_PREPARE\",\"ruleSetCode\":\"MISSING_KEY\","
                + "\"ruleSetName\":\"x\",\"baseRuleSetVersionId\":null,\"changeReason\":\"x\",\"rules\":[],"
                + "\"operandFormat\":\"VARIABLE_REFERENCE\"}";
            RiskDecisionHttpClient.assertProblem(http.configuration("POST", "/decision-rule-sets/v1/rule-sets",
                ACTOR, SESSION, missingBaseHash), 400, "INVALID_REQUEST");
            RiskDecisionHttpClient.assertProblem(http.configuration("POST", "/decision-rule-sets/v1/rule-sets/1/freeze",
                ACTOR, SESSION, "{}"), 400, "INVALID_REQUEST");
        }
    }

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
    void test_createPersistsCompleteSnapshotAndRejectsResubmission() throws Exception {
        String ruleSetCode = decisionTestMapper.uniqueRuleSetCode();
        String idempotencyKey = decisionTestMapper.uniqueNamespace();
        var requestBody = DecisionRuleSetRequestBuilder.currentFirstVersion(ruleSetCode);
        try (RiskDecisionHttpClient http = new RiskDecisionHttpClient(port)) {
            HttpResponse<String> first = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION,
                idempotencyKey, requestBody);
            assertEquals(200, first.statusCode(), first.body());
            CreateRuleSetVersionResponseBody created = http.body(first, CreateRuleSetVersionResponseBody.class);

            HttpResponse<String> resubmitted = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION,
                idempotencyKey, requestBody);
            RiskDecisionHttpClient.assertProblem(resubmitted, 409, "CONCURRENT_REQUEST");

            HttpResponse<String> detailResponse = http.get(
                "/decision-rule-sets/v1/rule-sets/" + created.getRuleSetVersionId(), ACTOR, SESSION);
            assertEquals(200, detailResponse.statusCode(), detailResponse.body());
            RuleSetDetailResponseBody detail = http.body(detailResponse, RuleSetDetailResponseBody.class);
            assertEquals(ruleSetCode, detail.getRuleSetCode());
            var requestedRule = requestBody.getRules().getFirst();
            var persistedRule = detail.getRules().getFirst();
            assertEquals(requestedRule.getRuleCode(), persistedRule.getRuleCode());
            assertEquals(requestedRule.getRuleName(), persistedRule.getRuleName());
            assertEquals(requestedRule.getPriorityNo(), persistedRule.getPriorityNo());
            assertEquals(requestedRule.getEnabledFlag(), persistedRule.getEnabledFlag());
            assertEquals(requestedRule.getReasonCode(), persistedRule.getReasonCode());
            assertEquals(requestedRule.getAction().getActionCode(), persistedRule.getAction().getActionCode());
            assertEquals(requestedRule.getAction().getActionVersionNo(), persistedRule.getAction().getActionVersionNo());
            assertEquals(requestedRule.getConditionNodes().getFirst().getSequenceNo(),
                persistedRule.getConditionNodes().getFirst().getSequenceNo());
            assertEquals(requestedRule.getConditionNodes().getFirst().getNodeType(),
                persistedRule.getConditionNodes().getFirst().getNodeType());
            assertEquals(requestedRule.getConditionNodes().getFirst().getComparisonOperator(),
                persistedRule.getConditionNodes().getFirst().getComparisonOperator());
            // HTTP 模型为普通 class，按 JSON 结构比较操作数的完整内容。
            assertEquals(OPERAND_JSON.valueToTree(requestedRule.getConditionNodes().getFirst().getOperands()),
                OPERAND_JSON.valueToTree(persistedRule.getConditionNodes().getFirst().getOperands()));

            DecisionSnapshotCounts counts = decisionTestMapper.snapshotCounts(ruleSetCode);
            assertEquals(new DecisionSnapshotCounts(1, 0, 0, 0, 1, 1, 2, 1), counts);
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
                    ruleSetCode, first.getRuleSetVersionId(), first.getContentHash()));
            assertEquals(200, nextResponse.statusCode(), nextResponse.body());
            CreateRuleSetVersionResponseBody next = http.body(
                nextResponse, CreateRuleSetVersionResponseBody.class);
            assertEquals(2, next.getVersionNo());
            assertEquals("DRAFT", next.getStatus());
            assertNotEquals(first.getRuleSetVersionId(), next.getRuleSetVersionId());
            org.junit.jupiter.api.Assertions.assertNull(first.getPointVersionId());
            org.junit.jupiter.api.Assertions.assertNull(next.getPointVersionId());
            assertNotEquals(first.getContentHash(), next.getContentHash());

            HttpResponse<String> retiredResponse = http.get(
                "/decision-rule-sets/v1/rule-sets/" + first.getRuleSetVersionId(), ACTOR, SESSION);
            assertEquals(200, retiredResponse.statusCode(), retiredResponse.body());
            assertEquals("RETIRED", http.body(retiredResponse, RuleSetDetailResponseBody.class).getStatus());

            HttpResponse<String> freezeResponse = http.post(
                "/decision-rule-sets/v1/rule-sets/" + next.getRuleSetVersionId() + "/freeze", ACTOR, SESSION,
                freezeKey, new FreezeRuleSetVersionRequestBody(next.getContentHash()));
            RiskDecisionHttpClient.assertProblem(freezeResponse, 400, "BUSINESS_CHECK_FAILED");

            HttpResponse<String> listResponse = http.get(
                "/decision-rule-sets/v1/rule-sets?page=1&size=10&pointCode=CHECKOUT_PREPARE&ruleSetCode="
                    + ruleSetCode,
                ACTOR, SESSION);
            assertEquals(200, listResponse.statusCode(), listResponse.body());
            var page = http.json(listResponse);
            assertEquals(1, page.get("total").asLong());
            assertEquals(1, page.get("items").size());
            assertEquals(next.getRuleSetVersionId(), page.get("items").get(0).get("ruleSetVersionId").asText());
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
            }
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(ruleSetCodes, commands);
        }
    }

    private static OwnedCommandIdentity command(String operationType, String idempotencyKey) {
        return new OwnedCommandIdentity(CALLER_SCOPE, operationType, idempotencyKey);
    }
}
