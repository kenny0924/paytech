package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RISK_ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator;
import com.paytech.risk.decision.api.DecisionRuleSetManagementModels.ConditionNodeRequestBody;
import com.paytech.risk.decision.api.DecisionRuleSetManagementModels.OperandRequestBody;
import com.paytech.risk.decision.api.DecisionRuleSetManagementModels.RuleActionRequestBody;
import com.paytech.risk.decision.api.DecisionRuleSetManagementModels.RuleRequestBody;
import com.paytech.risk.decision.api.RiskVariableModels.VariableSummary;
import com.paytech.risk.decision.api.response.RuleSetDetailResponseBody;
import com.paytech.risk.decision.api.evaluation.response.EvaluationExplanationResponseBody;
import com.paytech.risk.decision.api.evaluation.response.EvaluationPathStep;
import com.paytech.risk.decision.api.evaluation.response.EvaluationPredicateItem;
import com.paytech.risk.decision.api.evaluation.response.EvaluationResponseBody;
import com.paytech.risk.decision.api.evaluation.response.EvaluationRuleItem;
import com.paytech.risk.decision.api.evaluation.response.EvaluationShadowItem;
import com.paytech.risk.decision.api.evaluation.response.EvaluationVariableItem;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 浏览器 HTTP 到授权及 Risk 评估查询接口的完整边界：按 ID / 业务身份查结论、查解释并组装条件树，
 * 规则集详情不可用时按规则降级，Risk 404/400 透传，回执不一致拒绝，Query 白名单与 PLT-I02 授权，日志不含受限原值。
 *
 * @see com.paytech.operations.console.bff.ext.rest.RiskEvaluationController
 */
@ExtendWith(OutputCaptureExtension.class)
class RiskEvaluationBrowserAutoTest extends OperationsBffHttpTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ROOT = "/risk-decisions/v1/evaluations";
    private static final String RULE_SETS = "/decision-rule-sets/v1/rule-sets/";
    private static final String REQUEST_ID = "risk-evaluation-browser";
    private static final String TIME = RiskDecisionConfigurationSimulator.TIME;
    private static final String POINT = "PAYMENT_ATTEMPT_RISK";
    private static final String EVALUATION_ID = "236600000000000001";
    private static final String SHADOW_ID = "236600000000000002";
    private static final String BUSINESS_KEY = "235900000000000123";
    private static final String RULE_SET = "31001";
    private static final String MISSING_RULE_SET = "31002";
    private static final String IP_CANARY = "203.0.113.77";
    private static final String USER_AGENT_CANARY = "Mozilla/5.0 private-user-agent-canary";
    private static final String FINGERPRINT_CANARY = "fp_private_fingerprint_canary";
    private static final String CONSUMER_CANARY = "player-private-consumer-canary";
    private static final String QUERY = "?pointCode=" + POINT + "&businessKey=" + BUSINESS_KEY;

    @Autowired private PlatformOperationsSimulator platform;
    @Autowired private RiskDecisionConfigurationSimulator risk;
    @LocalServerPort private int port;

    @BeforeEach
    void prepareRiskEvaluation() {
        platform.reset();
        platform.useRiskIdentity();
        risk.reset();
        risk.addRuleSet(ruleSet());
        risk.addEvaluation(evaluation(EVALUATION_ID, "PRIMARY"), explanation());
        risk.addEvaluation(evaluation(SHADOW_ID, "SHADOW"), shadowExplanation());
    }

    @AfterEach
    void cleanRiskEvaluation() {
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
        var detail = body(send("/" + EVALUATION_ID, "detail"));
        assertEquals(EVALUATION_ID, detail.path("evaluationId").asString());
        assertEquals("ALLOW", detail.path("result").asString());
        assertEquals("THREE_DS_REQUIRED", detail.path("outputs").path("AUTHENTICATION_REQUIREMENT").asString());
        assertTrue(detail.path("reasonCode").isNull());
        assertEquals(3, detail.path("flowVersionNo").asInt());
        assertTrue(detail.path("pathAvailable").asBoolean());
        assertEquals(EVALUATION_ID, body(send(QUERY, "find")).path("evaluationId").asString());
        assertEquals("SHADOW", body(send("/" + SHADOW_ID, "detail")).path("mode").asString());

        var explanation = body(send("/" + EVALUATION_ID + "/explanation", "explanation"));
        assertEquals(IP_CANARY, explanation.path("inputs").path("clientIpAddress").asString());
        assertTrue(explanation.path("inputs").path("cardBin").isNull());
        assertEquals(List.of("START", "RULE_SET", "END"), explanation.path("path").valueStream().map(s -> s.path("nodeType").asString()).toList());
        assertEquals("其他情况", explanation.path("path").get(1).path("exitEdgeName").asString());
        assertEquals("CURRENT", explanation.path("variables").get(2).path("valueBasis").asString());
        assertTrue(explanation.path("shadows").get(0).path("decisionChanged").asBoolean());
        assertTrue(explanation.path("rules").get(0).path("predicates").isMissingNode(), "谓词轨迹只用于组装，不返回页面");

        assertEquals(6, risk.requests().size(), "规则集详情同次请求内只读一次，缺失的版本也只读一次");
        assertEquals(1, risk.requests().stream().filter(call -> call.path().equals(RULE_SETS + RULE_SET)).count());
        for (var call : risk.requests()) {
            assertEquals(REQUEST_ID, call.header("X-Request-Id"));
            assertNull(call.header("Idempotency-Key"));
            boolean ruleSetCall = call.path().startsWith(RULE_SETS);
            assertEquals(ruleSetCall ? RISK_ACCOUNT_ID : null, call.header("X-Gateway-Actor-Subject"), "评估查询为内部直连，不带身份头");
        }
        for (String canary : List.of(IP_CANARY, USER_AGENT_CANARY, FINGERPRINT_CANARY, CONSUMER_CANARY)) {
            assertFalse(output.getAll().contains(canary), "受限原值不得进入 BFF 日志");
        }
        assertTrue(output.getAll().contains(REQUEST_ID), "必须实际捕获本请求的日志，不能空日志通过");
    }

    @Test
    void test_conditionTreeAssembly() throws Exception {
        var rules = body(send("/" + EVALUATION_ID + "/explanation", "explanation")).path("rules");
        var large = rules.get(0);
        assertTrue(large.path("conditionAvailable").asBoolean());
        var root = large.path("condition");
        assertEquals("9001", root.path("nodeKey").asString());
        assertEquals("AND", root.path("groupOperator").asString());
        assertTrue(root.path("result").asBoolean());
        var amount = root.path("children").get(0);
        assertEquals("PREDICATE", amount.path("nodeType").asString());
        assertEquals("GT", amount.path("comparisonOperator").asString());
        assertTrue(amount.path("result").asBoolean());
        assertEquals("支付金额", amount.path("operands").get(0).path("variableName").asString());
        assertEquals("5000", amount.path("operands").get(1).path("literalValue").asString());
        assertEquals("MONEY", amount.path("operands").get(1).path("numericUnit").asString());
        assertEquals("PAY_INPUT_CURRENCY", amount.path("operands").get(1).path("currencyVariableCode").asString());
        var or = root.path("children").get(1);
        assertEquals("OR", or.path("groupOperator").asString());
        assertTrue(or.path("result").asBoolean());
        assertFalse(or.path("children").get(0).path("result").asBoolean());
        assertEquals("卡拒付次数（1小时）", or.path("children").get(0).path("operands").get(0).path("variableName").asString(),
            "解释变量表没有名称时取规则集详情的当前变量");
        assertTrue(or.path("children").get(1).path("operands").get(0).path("variableName").isNull(), "两处都没有名称时为空");
        var not = root.path("children").get(2);
        assertEquals("NOT", not.path("groupOperator").asString());
        assertTrue(not.path("result").asBoolean());
        assertFalse(not.path("children").get(0).path("result").asBoolean());
        assertEquals(0, not.path("operands").size());

        var shortCircuit = rules.get(1);
        assertTrue(shortCircuit.path("conditionAvailable").asBoolean());
        assertTrue(shortCircuit.path("condition").path("result").isNull(), "任一子节点未求值时分组无结果");
        assertFalse(shortCircuit.path("condition").path("children").get(0).path("result").asBoolean());
        assertTrue(shortCircuit.path("condition").path("children").get(1).path("result").isNull(), "未求值的谓词为 null");
    }

    @Test
    void test_edgeCase() throws Exception {
        var response = send("/" + EVALUATION_ID + "/explanation", "explanation");
        var rules = body(response).path("rules");
        assertEquals(List.of("LARGE_AMOUNT", "SHORT_CIRCUIT", "NODE_MISMATCH", "MISSING_RULE", "BLOCK_RULE"),
            rules.valueStream().map(rule -> rule.path("ruleCode").asString()).toList());
        for (int index = 2; index < 5; index++) {
            assertFalse(rules.get(index).path("conditionAvailable").asBoolean(), rules.get(index).path("ruleCode").asString());
            assertTrue(rules.get(index).path("condition").isNull());
            assertTrue(rules.get(index).path("matched").isBoolean(), "降级只影响条件，规则其余字段照常");
        }
        assertEquals(1, risk.requests().stream().filter(call -> call.path().equals(RULE_SETS + MISSING_RULE_SET)).count());

        risk.corruptWire(wire -> {
            if (wire.has("rules") && !wire.has("evaluationId")) {
                wire.put("privateOwnerState", "canary");
            }
        });
        var degraded = body(send("/" + EVALUATION_ID + "/explanation", "explanation"));
        assertFalse(degraded.path("rules").get(0).path("conditionAvailable").asBoolean(), "规则集详情不符合协议时同样降级");
        assertEquals(EVALUATION_ID, degraded.path("evaluationId").asString());
    }

    @Test
    void test_noDataFound() throws Exception {
        assertProblem(send("/236600000000000999", "detail"), 404, CommonErrorCode.NOT_FOUND.getCode());
        assertProblem(send("/236600000000000999/explanation", "explanation"), 404, CommonErrorCode.NOT_FOUND.getCode());
        assertProblem(send("?pointCode=" + POINT + "&businessKey=235900000000000999", "find"), 404, CommonErrorCode.NOT_FOUND.getCode());
        assertProblem(send("?pointCode=CHECKOUT_PREPARE&businessKey=" + BUSINESS_KEY, "find"), 400, CommonErrorCode.INVALID_REQUEST.getCode());
        assertProblem(send("?pointCode=" + POINT + "&businessKey=not-a-number", "find"), 400, CommonErrorCode.INVALID_REQUEST.getCode());
        assertEquals(5, risk.requests().size());
        assertTrue(risk.requests().stream().noneMatch(call -> call.path().startsWith(RULE_SETS)), "解释失败时不读取规则集详情");
    }

    @Test
    void test_businessRuleViolation() throws Exception {
        risk.corruptWire(wire -> wire.put("evaluationId", "236600000000000003"));
        assertProblem(send("/" + EVALUATION_ID, "detail"), 502, CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode());
        assertProblem(send("/" + EVALUATION_ID + "/explanation", "explanation"), 502, CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode());
        risk.corruptWire(wire -> wire.put("mode", "SHADOW"));
        assertProblem(send(QUERY, "find"), 502, CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode());
        risk.corruptWire(wire -> wire.put("businessKey", "235900000000000456"));
        assertProblem(send(QUERY, "find"), 502, CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode());
        risk.corruptWire(wire -> wire.put("pointCode", "CHECKOUT_PREPARE"));
        assertProblem(send(QUERY, "find"), 502, CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode());
        risk.corruptWire(wire -> wire.put("privateOwnerState", "canary"));
        var unknown = send("/" + EVALUATION_ID, "detail");
        assertProblem(unknown, 502, CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode());
        assertFalse(unknown.body().contains("canary"));
        assertTrue(risk.requests().stream().noneMatch(call -> call.path().startsWith(RULE_SETS)), "解释回执不一致时不读取规则集详情");
    }

    @Test
    void test_invalidParameter() throws Exception {
        for (String path : List.of("/abc", "/0", "/" + EVALUATION_ID + "?trace=1")) {
            assertProblem(send(path, "detail"), 400, CommonErrorCode.INVALID_REQUEST.getCode());
        }
        assertProblem(send("/abc/explanation", "explanation"), 400, CommonErrorCode.INVALID_REQUEST.getCode());
        assertProblem(send("/" + EVALUATION_ID + "/explanation?verbose=true", "explanation"), 400, CommonErrorCode.INVALID_REQUEST.getCode());
        for (String query : List.of("?pointCode=" + POINT, "?businessKey=" + BUSINESS_KEY, "?pointCode=payment&businessKey=" + BUSINESS_KEY,
            QUERY + "&mode=SHADOW", QUERY + "&businessKey=" + BUSINESS_KEY, "?pointCode=" + POINT + "&businessKey=")) {
            assertEquals(400, send(query, "find").statusCode(), query);
        }
        assertTrue(risk.requests().isEmpty());
    }

    @Test
    void test_permissionDenied() throws Exception {
        platform.grantPermissions("900000000000000536");
        assertEquals(403, send("/" + EVALUATION_ID, "detail").statusCode());
        assertEquals(403, send(QUERY, "find").statusCode());
        assertEquals(403, send("/" + EVALUATION_ID + "/explanation", "explanation").statusCode());
        assertTrue(risk.requests().isEmpty());
        platform.grantPermissions(PlatformOperationsSimulator.RISK_EVALUATION_READ_PERMISSION_ID);
        assertEquals(200, send("/" + EVALUATION_ID, "detail").statusCode());
    }

    /** 正式或影子评估的结论 DTO。 */
    private static EvaluationResponseBody evaluation(String evaluationId, String mode) {
        var value = new EvaluationResponseBody();
        value.setEvaluationId(evaluationId); value.setPointCode(POINT); value.setBusinessKey(BUSINESS_KEY); value.setMode(mode);
        value.setResult(mode.equals("PRIMARY") ? "ALLOW" : "REVIEW");
        value.setOutputs(mode.equals("PRIMARY") ? Map.of("AUTHENTICATION_REQUIREMENT", "THREE_DS_REQUIRED") : Map.of());
        value.setReleaseId("236518950932942848"); value.setEnvironment("TEST"); value.setFlowId("236518950786142208");
        value.setFlowCode("PAY_MAIN"); value.setFlowVersionId("236518950786142209"); value.setFlowVersionNo(3);
        value.setEvaluationTime(TIME); value.setElapsedMillis(18); value.setPathAvailable(true);
        if (mode.equals("SHADOW")) {
            value.setBaselineEvaluationId(EVALUATION_ID); value.setShadowRunId("95001");
        }
        return value;
    }

    /** 正式评估的解释：两条规则引用可用规则集，三条规则分别对不上节点、找不到规则、规则集详情缺失。 */
    private static EvaluationExplanationResponseBody explanation() {
        var value = new EvaluationExplanationResponseBody();
        value.setEvaluationId(EVALUATION_ID); value.setInputs(inputs()); value.setPathAvailable(true);
        value.setPath(List.of(step(1, "start", "START", null), step(2, "n_block", "RULE_SET", RULE_SET), step(3, "end_3ds", "END", null)));
        value.setRules(List.of(rule(RULE_SET, "LARGE_AMOUNT", 10, true, predicate("9002", true), predicate("9004", false),
                predicate("9005", true), predicate("9007", false)),
            rule(RULE_SET, "SHORT_CIRCUIT", 20, false, predicate("9102", false)),
            rule(RULE_SET, "NODE_MISMATCH", 30, false, predicate("9999", true)),
            rule(RULE_SET, "MISSING_RULE", 40, false),
            rule(MISSING_RULE_SET, "BLOCK_RULE", 50, false, predicate("9201", false))));
        value.setVariables(List.of(variable("PAY_INPUT_AMOUNT", "支付金额", "INPUT", "EVALUATION"),
            variable("CARD_DECLINES_1H", null, "VELOCITY", "EVALUATION"), variable("LARGE_AMOUNT_LIMIT", "大额阈值", "CONSTANT", "CURRENT")));
        var shadow = new EvaluationShadowItem();
        shadow.setEvaluationId(SHADOW_ID); shadow.setShadowRunId("95001"); shadow.setResult("REVIEW");
        shadow.setDecisionChanged(true); shadow.setSeverity("MEDIUM");
        value.setShadows(List.of(shadow));
        return value;
    }

    /** 影子评估的解释：没有规则执行。 */
    private static EvaluationExplanationResponseBody shadowExplanation() {
        var value = new EvaluationExplanationResponseBody();
        value.setEvaluationId(SHADOW_ID); value.setInputs(inputs()); value.setPathAvailable(false);
        value.setPath(List.of()); value.setRules(List.of()); value.setVariables(List.of()); value.setShadows(List.of());
        return value;
    }

    /** 冻结输入；受限原值使用 Canary 以便核对日志。 */
    private static Map<String, String> inputs() {
        Map<String, String> inputs = new HashMap<>();
        inputs.put("paymentIntentId", "235800000000000001"); inputs.put("amount", "600.00"); inputs.put("currency", "USD");
        inputs.put("clientIpAddress", IP_CANARY); inputs.put("userAgent", USER_AGENT_CANARY);
        inputs.put("cardFingerprint", FINGERPRINT_CANARY); inputs.put("consumerReference", CONSUMER_CANARY);
        inputs.put("cardBin", null);
        return inputs;
    }

    /** 流程路径节点。 */
    private static EvaluationPathStep step(int stepNo, String nodeKey, String nodeType, String ruleSetVersionId) {
        var value = new EvaluationPathStep();
        value.setStepNo(stepNo); value.setNodeKey(nodeKey); value.setNodeType(nodeType); value.setNodeName(nodeKey);
        if (ruleSetVersionId != null) {
            value.setRuleSetVersionId(ruleSetVersionId); value.setRuleSetCode("PAY_BLOCK"); value.setRuleSetVersionNo(2);
            value.setMatched(true); value.setReused(false); value.setExitEdgeName("其他情况");
        }
        value.setExitEdgeKey(nodeType.equals("END") ? null : "e" + stepNo);
        value.setDecision(nodeType.equals("END") ? "ALLOW" : null);
        return value;
    }

    /** 执行过的规则与谓词轨迹。 */
    private static EvaluationRuleItem rule(String ruleSetVersionId, String ruleCode, int priorityNo, boolean matched,
        EvaluationPredicateItem... predicates) {
        var value = new EvaluationRuleItem();
        value.setRuleSetVersionId(ruleSetVersionId); value.setRuleId(Integer.toString(80000 + priorityNo)); value.setRuleCode(ruleCode);
        value.setRuleName(ruleCode); value.setPriorityNo(priorityNo); value.setMatched(matched); value.setTerminal(matched);
        value.setAction("REVIEW_PAYMENT"); value.setPredicates(List.of(predicates));
        return value;
    }

    /** 谓词结果。 */
    private static EvaluationPredicateItem predicate(String nodeId, boolean result) {
        var value = new EvaluationPredicateItem();
        value.setNodeId(nodeId); value.setResult(result);
        return value;
    }

    /** 变量取值。 */
    private static EvaluationVariableItem variable(String code, String name, String sourceType, String basis) {
        var value = new EvaluationVariableItem();
        value.setVariableCode(code); value.setVariableName(name); value.setSourceType(sourceType); value.setValueType("NUMBER");
        value.setValue("600.00"); value.setValueBasis(basis);
        return value;
    }

    /**
     * 规则集版本 31001 的详情
     * <li>LARGE_AMOUNT：AND(金额 GT 5000, OR(拒付次数 EQ 3, 国家集合 EQ 1), NOT(拒付次数 GT 9))。</li>
     * <li>SHORT_CIRCUIT：AND(两个谓词)，只有第一个被求值。NODE_MISMATCH 的轨迹节点不在详情中；没有 MISSING_RULE。</li>
     */
    private static RuleSetDetailResponseBody ruleSet() {
        var large = new RuleRequestBody("LARGE_AMOUNT", "大额审核", 10, true, null, List.of(
            group("9001", null, 1, "AND"),
            predicate("9002", "9001", 1, "GT", variableOperand("PAY_INPUT_AMOUNT"), moneyLiteral("5000")),
            group("9003", "9001", 2, "OR"),
            predicate("9004", "9003", 1, "EQ", variableOperand("CARD_DECLINES_1H"), literal("3")),
            predicate("9005", "9003", 2, "EQ", variableOperand("RISK_COUNTRY_SET"), literal("1")),
            group("9006", "9001", 3, "NOT"),
            predicate("9007", "9006", 1, "GT", variableOperand("CARD_DECLINES_1H"), literal("9"))),
            new RuleActionRequestBody("REVIEW_PAYMENT", 1), null, List.of());
        var shortCircuit = new RuleRequestBody("SHORT_CIRCUIT", "短路规则", 20, true, null, List.of(group("9101", null, 1, "AND"),
            predicate("9102", "9101", 1, "GT", variableOperand("PAY_INPUT_AMOUNT"), literal("100")),
            predicate("9103", "9101", 2, "GT", variableOperand("CARD_DECLINES_1H"), literal("1"))),
            new RuleActionRequestBody("REVIEW_PAYMENT", 1), null, List.of());
        var mismatch = new RuleRequestBody("NODE_MISMATCH", "节点不一致", 30, true, null, List.of(group("9301", null, 1, "AND"),
            predicate("9302", "9301", 1, "GT", variableOperand("PAY_INPUT_AMOUNT"), literal("1"))),
            new RuleActionRequestBody("REVIEW_PAYMENT", 1), null, List.of());
        var declines = new VariableSummary("CARD_DECLINES_1H", "卡拒付次数（1小时）", "VELOCITY", "NUMBER", POINT, List.of("RULE_CONDITION"),
            true, "ACTIVE", "1", null, null, null, "INTEGER", null, null, null, List.of(), TIME);
        return new RuleSetDetailResponseBody(RULE_SET, "20001", POINT, "PAY_BLOCK", "拦截规则", 2, "FROZEN",
            RiskDecisionConfigurationSimulator.HASH, "10001", "初始版本", TIME, "10001", TIME, List.of(large, shortCircuit, mismatch),
            "VARIABLE_REFERENCE", List.of(declines), TIME, List.of());
    }

    /** 分组节点。 */
    private static ConditionNodeRequestBody group(String key, String parent, int sequence, String operator) {
        return new ConditionNodeRequestBody(key, parent, sequence, "GROUP", operator, null, List.of());
    }

    /** 谓词节点。 */
    private static ConditionNodeRequestBody predicate(String key, String parent, int sequence, String operator,
        OperandRequestBody left, OperandRequestBody right) {
        return new ConditionNodeRequestBody(key, parent, sequence, "PREDICATE", null, operator, List.of(left, right));
    }

    /** 变量左操作数。 */
    private static OperandRequestBody variableOperand(String code) {
        return new OperandRequestBody("LEFT", 1, "VARIABLE", null, "NUMBER", null, code, "SCALAR", null, null);
    }

    /** 数值字面量右操作数。 */
    private static OperandRequestBody literal(String value) {
        return new OperandRequestBody("RIGHT", 1, "LITERAL", null, "NUMBER", value, null, "SCALAR", null, null);
    }

    /** 金额字面量右操作数，币种取自支付币种变量。 */
    private static OperandRequestBody moneyLiteral(String value) {
        return new OperandRequestBody("RIGHT", 1, "LITERAL", null, "NUMBER", value, null, "MONEY", null, "PAY_INPUT_CURRENCY");
    }

    private static JsonNode body(HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), response.body());
        return JSON.readTree(response.body());
    }

    private static void assertProblem(HttpResponse<String> response, int status, String code) {
        assertEquals(status, response.statusCode(), response.body());
        var problem = JSON.readTree(response.body());
        assertEquals(code, problem.path("code").asString(), response.body());
        assertEquals(REQUEST_ID, problem.path("requestId").asString());
        assertFalse(response.body().contains(RiskDecisionConfigurationSimulator.UPSTREAM_DETAIL_CANARY));
    }

    private HttpResponse<String> send(String path, String route) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + ROOT + path)).timeout(Duration.ofSeconds(8));
        Map.ofEntries(Map.entry("Origin", "https://operations-console.local.test"), Map.entry("X-Request-Id", REQUEST_ID),
            Map.entry("X-Gateway-Realm", "OPERATIONS"), Map.entry("X-Gateway-Channel", "OPERATIONS"),
            Map.entry("X-Gateway-Actor-Type", "PLATFORM_USER"), Map.entry("X-Gateway-Actor-Subject", RISK_ACCOUNT_ID),
            Map.entry("X-Gateway-Session-Id", SESSION_ID), Map.entry("X-Gateway-Route-Code", "operations-risk-evaluation-" + route),
            Map.entry("X-Gateway-Context-Type", "TENANT"), Map.entry("X-Gateway-Tenant-Type", "INTERNAL"),
            Map.entry("X-Gateway-Tenant-Id", "tenant_10001"), Map.entry("X-Gateway-Tenant-Member-Id", "member_10001")).forEach(builder::header);
        builder.GET();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
