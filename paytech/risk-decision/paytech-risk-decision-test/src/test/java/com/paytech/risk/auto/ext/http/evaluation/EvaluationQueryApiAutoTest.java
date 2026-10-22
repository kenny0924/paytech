package com.paytech.risk.auto.ext.http.evaluation;

import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.NOT_FOUND;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.action;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.predicate;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.rule;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.decision.api.DecisionRuleSetManagementModels.OperandRequestBody;
import com.paytech.risk.decision.api.request.CreateVariableRequestBody;
import com.paytech.risk.decision.api.request.StartShadowRunRequestBody;
import com.paytech.risk.decision.api.evaluation.RiskEvaluationQueryApi;
import com.paytech.risk.decision.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.decision.api.evaluation.request.PaymentAttemptRiskInput;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.domain.enums.AuthenticationRequirement;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.EvaluationMode;
import com.paytech.risk.decision.domain.enums.FlowEdgeType;
import com.paytech.risk.decision.domain.enums.FlowNodeType;
import com.paytech.risk.decision.domain.enums.ShadowRunStatus;
import com.paytech.risk.decision.domain.enums.TechnicalStatus;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import com.paytech.risk.decision.domain.enums.VelocityMetric;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.runtime.FeatureComputationException;
import com.paytech.risk.test.base.AbstractPaymentRiskApiTest;
import com.paytech.risk.test.builder.decision.FlowBuilder.EdgeSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.GraphSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.NodeSpec;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;
import com.paytech.risk.test.builder.evaluation.PaymentRiskRequestBuilder;
import com.paytech.risk.test.checker.evaluation.EvaluationResponseChecker;
import com.paytech.risk.test.probe.HttpLogProbe;

import tools.jackson.databind.JsonNode;

/**
 * 评估结论与解释查询：经真实配置入口准备流程并激活，经统一评估入口产生评估后查询
 * <li>流程：开始 → 拦截规则集 → 节点拒绝/审核走对应结束，否则 → 认证规则集 → 有命中走“放行（需要 3DS）”，否则 → 再次经过拦截规则集（复用结果）→ 放行。</li>
 * <li>正常流程：放行、放行并要求 3DS、拒绝（原因码与文案）、审核；按 ID 与按业务身份查询一致；解释给出输入原值、路径（含复用）、规则与谓词结果、变量取值；常量改值后标注为当前值；影子评估可单独查询并在基线解释中列出。</li>
 * <li>边界：上线前的评估没有路径；超时保留路径与轨迹；名单命中 Feature 失败时记 FEATURE_ERROR，解释标出失败变量。</li>
 * <li>参数与无数据：ID、风控点、业务身份格式错误或 Query 未声明返回 400；评估或业务身份不存在返回 404。</li>
 *
 * @see RiskEvaluationQueryApi
 */
class EvaluationQueryApiAutoTest extends AbstractPaymentRiskApiTest {
    private static final String EVALUATIONS = "/risk-decisions/v1/evaluations";

    private static final String DENY_MESSAGE = "支付被风控拒绝";

    private static final String AUTHENTICATION = PaymentAttemptRiskInput.AUTHENTICATION_REQUIREMENT;

    private static final long SHADOW_WAIT_MILLIS = 15_000;

    private String reason;

    private String blockedIpList;

    private String ipBlocked;

    private String cardCount;

    private String cardLimit;

    private long blockRuleSet;

    private String flowCode;

    /** 每例准备名单、累计与常量变量、拦截规则集，以及认证阈值 500 的生效流程。 */
    @BeforeEach
    void setUpClassMocks() {
        reason = rejectionReason();
        blockedIpList = newCode("T_BLOCKED_IP");
        createList(blockedIpList, RiskSubjectType.IP_ADDRESS.getCode(), "BLOCK");
        ipBlocked = newCode("T_IP_BLOCKED");
        createListVariable(ipBlocked, blockedIpList, RiskSubjectType.IP_ADDRESS.getCode());
        cardCount = newCode("T_CARD_COUNT");
        createVelocityVariable(cardCount, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED.getCode(),
            RiskSubjectType.CARD_FINGERPRINT.getCode(), VelocityMetric.COUNT.getCode(), null, 600, false);
        cardLimit = newCode("T_CARD_LIMIT");
        CreateVariableRequestBody constant = featureVariable(cardLimit, VariableSource.CONSTANT.getCode(), "NUMBER");
        constant.setNumericUnit("SCALAR");
        constant.setCurrentValue("2");
        postOk("/decision-variables/v1/variables", constant);
        blockRuleSet = frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_BLOCK")).pointCode(PAYMENT)
            .rules(rule("BLOCKED_IP", "Blocked IP", 10, reason, DENY_MESSAGE, action("DENY_PAYMENT"), List.of(),
                    predicate("root", null, 1, "EQ", variableOperand(ipBlocked, "BOOLEAN"), bool(true))),
                rule("CARD_REPEAT", "Card repeat", 20, reason, DENY_MESSAGE, action("DENY_PAYMENT"), List.of(),
                    predicate("root", null, 1, "GTE", variableOperand(cardCount, "NUMBER"),
                        constantOperand(cardLimit))),
                rule("LARGE_AMOUNT", "Large amount", 30, action("REVIEW_PAYMENT"),
                    predicate("root", null, 1, "GT", variableOperand("PAY_INPUT_AMOUNT", "NUMBER"), money("1000"))))
            .build());
        flowCode = newCode("T_FLOW");
        activeRelease(saveFlow(flowCode, recheckGraph(blockRuleSet, authRuleSet("500"))));
    }

    /** 放行：按 ID 与按业务身份查询一致；解释的输入原值、路径（含复用）、规则与谓词、变量取值完整；常量改值后显示当前值。 */
    @Test
    void test_normalFlow() {
        EvaluateDecisionRequestBody request = PaymentRiskRequestBuilder.template().amount("100").build();
        long evaluationId = assess(request).assertResult(DecisionValue.ALLOW.getCode(),
            AuthenticationRequirement.NOT_REQUIRED.getCode()).evaluationId();

        JsonNode summary = getOk(EVALUATIONS + "/" + evaluationId);
        assertAll("结论", () -> assertEquals(Long.toString(evaluationId), summary.path("evaluationId").asString()),
            () -> assertEquals(PAYMENT, summary.path("pointCode").asString()),
            () -> assertEquals(request.getBusinessKey(), summary.path("businessKey").asString()),
            () -> assertEquals(EvaluationMode.PRIMARY.getCode(), summary.path("mode").asString()),
            () -> assertEquals(DecisionValue.ALLOW.getCode(), summary.path("result").asString()),
            () -> assertTrue(summary.path("reasonCode").isNull(), "放行无原因码"),
            () -> assertEquals(AuthenticationRequirement.NOT_REQUIRED.getCode(),
                summary.path("outputs").path(AUTHENTICATION).asString()),
            () -> assertTrue(summary.path("failureCode").isNull(), "无失败码"),
            () -> assertEquals(DecisionEnvironment.TEST.getCode(), summary.path("environment").asString()),
            () -> assertEquals(flowCode, summary.path("flowCode").asString()),
            () -> assertEquals(1, summary.path("flowVersionNo").asInt()),
            () -> assertTrue(summary.path("pathAvailable").asBoolean(), "有路径"));
        assertEquals(summary, getOk(EVALUATIONS + "?pointCode=" + PAYMENT + "&businessKey=" + request.getBusinessKey()),
            "按业务身份查询");

        JsonNode explanation = getOk(EVALUATIONS + "/" + evaluationId + "/explanation");
        assertAll("解释", () -> assertEquals(request.getInputs().get("clientIpAddress"),
                explanation.path("inputs").path("clientIpAddress").asString()),
            () -> assertEquals(request.getInputs().get("cardFingerprint"),
                explanation.path("inputs").path("cardFingerprint").asString()),
            () -> assertEquals("100", explanation.path("inputs").path("amount").asString()),
            () -> assertEquals(request.getInputs().get("cardBrand"),
                explanation.path("inputs").path("cardBrand").asString()),
            () -> assertEquals(20, explanation.path("inputs").size(), "输入字段数"),
            () -> assertEquals(List.of("start", "block", "auth", "recheck", "allow"), nodeKeys(explanation)),
            () -> assertEquals(List.of("enter", "to_auth", "to_recheck", "finish"), exitEdges(explanation)),
            () -> assertFalse(explanation.path("path").path(1).path("reused").asBoolean(), "首次执行"),
            () -> assertTrue(explanation.path("path").path(3).path("reused").asBoolean(), "再次经过复用结果"),
            () -> assertEquals(FlowNodeType.END.getCode(), explanation.path("path").path(4).path("nodeType").asString()),
            () -> assertEquals(List.of("BLOCKED_IP", "CARD_REPEAT", "LARGE_AMOUNT", "THREE_DS_AMOUNT"),
                ruleCodes(explanation)),
            () -> assertEquals(4, store.traces(evaluationId, "PREDICATE").size(), "谓词轨迹"),
            () -> assertFalse(explanation.path("rules").path(0).path("predicates").path(0).path("result").asBoolean()),
            () -> assertEquals("false", variable(explanation, ipBlocked).path("value").asString()),
            () -> assertEquals("0", variable(explanation, cardCount).path("value").asString()),
            () -> assertEquals("EVALUATION", variable(explanation, cardCount).path("valueBasis").asString()),
            () -> assertEquals("100", variable(explanation, "PAY_INPUT_AMOUNT").path("value").asString()),
            () -> assertEquals("2", variable(explanation, cardLimit).path("value").asString()),
            () -> assertEquals("CURRENT", variable(explanation, cardLimit).path("valueBasis").asString()),
            () -> assertEquals(0, explanation.path("shadows").size()));

        payments.mockConstantChanged(cardLimit, "5");
        assertEquals("5", variable(getOk(EVALUATIONS + "/" + evaluationId + "/explanation"), cardLimit)
            .path("value").asString(), "常量显示当前值");
    }

    /** 放行并要求 3DS：输出取已保存的认证要求，路径止于“放行（需要 3DS）”。 */
    @Test
    void test_normalFlow_threeDs() {
        long evaluationId = assess(PaymentRiskRequestBuilder.template().amount("600").build())
            .assertResult(DecisionValue.ALLOW.getCode(), AuthenticationRequirement.THREE_DS_REQUIRED.getCode())
            .evaluationId();

        JsonNode summary = getOk(EVALUATIONS + "/" + evaluationId);
        JsonNode explanation = getOk(EVALUATIONS + "/" + evaluationId + "/explanation");

        assertAll(() -> assertEquals(AuthenticationRequirement.THREE_DS_REQUIRED.getCode(),
                summary.path("outputs").path(AUTHENTICATION).asString()),
            () -> assertEquals(List.of("start", "block", "auth", "allow_3ds"), nodeKeys(explanation)),
            () -> assertTrue(explanation.path("path").path(2).path("matched").asBoolean(), "认证节点命中"),
            () -> assertEquals(DecisionValue.ALLOW.getCode(), explanation.path("path").path(2).path("decision").asString()),
            () -> assertEquals("Authentication", explanation.path("path").path(2).path("exitEdgeName").asString()));
    }

    /** 拒绝与审核：原因码与文案取自到达的结束节点；拦截节点记录命中与决定，后续节点不执行。 */
    @Test
    void test_businessRuleViolation() {
        EvaluateDecisionRequestBody denied = PaymentRiskRequestBuilder.template().clientIpAddress("203.0.113.9").build();
        addListEntry(blockedIpList, "203.0.113.9");
        long denyId = assess(denied).assertResult(DecisionValue.DENY.getCode(), null).evaluationId();
        long reviewId = assess(PaymentRiskRequestBuilder.template().amount("1500").build())
            .assertResult(DecisionValue.REVIEW.getCode(), null).evaluationId();

        JsonNode deny = getOk(EVALUATIONS + "/" + denyId);
        JsonNode denyPath = getOk(EVALUATIONS + "/" + denyId + "/explanation");
        JsonNode review = getOk(EVALUATIONS + "/" + reviewId);

        assertAll("拒绝", () -> assertEquals(DecisionValue.DENY.getCode(), deny.path("result").asString()),
            () -> assertEquals(reason, deny.path("reasonCode").asString()),
            () -> assertEquals(DENY_MESSAGE, deny.path("reasonMessage").asString()),
            () -> assertEquals(0, deny.path("outputs").size(), "拒绝没有输出"),
            () -> assertEquals(List.of("start", "block", "deny"), nodeKeys(denyPath)),
            () -> assertTrue(denyPath.path("path").path(1).path("matched").asBoolean()),
            () -> assertEquals(DecisionValue.DENY.getCode(), denyPath.path("path").path(1).path("decision").asString()),
            () -> assertEquals("Denied", denyPath.path("path").path(1).path("exitEdgeName").asString()),
            () -> assertEquals(List.of("BLOCKED_IP"), ruleCodes(denyPath)),
            () -> assertTrue(denyPath.path("rules").path(0).path("terminal").asBoolean(), "终态命中"),
            () -> assertEquals("DENY_PAYMENT", denyPath.path("rules").path(0).path("action").asString()),
            () -> assertEquals("true", variable(denyPath, ipBlocked).path("value").asString()));
        assertAll("审核", () -> assertEquals(DecisionValue.REVIEW.getCode(), review.path("result").asString()),
            () -> assertTrue(review.path("reasonCode").isNull(), "审核结束节点未配置原因"),
            () -> assertEquals(0, review.path("outputs").size()));
    }

    /** 上线前的评估没有流程轨迹：标为路径不可用，原因码为空，正式评估输出仍取已保存的值；规则与变量照常返回。 */
    @Test
    void test_edgeCase_withoutPath() {
        long evaluationId = assess(PaymentRiskRequestBuilder.template().amount("600").build())
            .assertResult(DecisionValue.ALLOW.getCode(), AuthenticationRequirement.THREE_DS_REQUIRED.getCode())
            .evaluationId();
        payments.mockEvaluationWithoutPath(evaluationId);

        JsonNode summary = getOk(EVALUATIONS + "/" + evaluationId);
        JsonNode explanation = getOk(EVALUATIONS + "/" + evaluationId + "/explanation");

        assertAll(() -> assertFalse(summary.path("pathAvailable").asBoolean()),
            () -> assertEquals(AuthenticationRequirement.THREE_DS_REQUIRED.getCode(),
                summary.path("outputs").path(AUTHENTICATION).asString()),
            () -> assertFalse(explanation.path("pathAvailable").asBoolean()),
            () -> assertEquals(0, explanation.path("path").size()),
            () -> assertEquals(4, explanation.path("rules").size(), "规则轨迹不受影响"));
    }

    /** 超时：评估无结论，但保留 Feature、规则轨迹与路径供解释。 */
    @Test
    void test_edgeCase_timedOut() {
        payments.mockEvaluationTimeout();
        long evaluationId = assess(PaymentRiskRequestBuilder.template().build())
            .assertResult(TechnicalStatus.TIMED_OUT.getCode(), null).evaluationId();

        JsonNode summary = getOk(EVALUATIONS + "/" + evaluationId);
        JsonNode explanation = getOk(EVALUATIONS + "/" + evaluationId + "/explanation");

        assertAll(() -> assertEquals(TechnicalStatus.TIMED_OUT.getCode(), summary.path("result").asString()),
            () -> assertEquals("EVALUATION_TIMEOUT", summary.path("failureCode").asString()),
            () -> assertTrue(summary.path("reasonCode").isNull()),
            () -> assertEquals(0, summary.path("outputs").size()),
            () -> assertTrue(explanation.path("pathAvailable").asBoolean(), "超时保留路径"),
            () -> assertEquals(List.of("start", "block", "auth", "recheck", "allow"), nodeKeys(explanation)),
            () -> assertEquals(4, explanation.path("rules").size()),
            () -> assertEquals("SUCCEEDED", variable(explanation, cardCount).path("technicalStatus").asString()));
    }

    /** 名单命中 Feature 读取名单失败：FAILED + FEATURE_ERROR；已完成的累计变量有值，失败变量标出，流程未执行。 */
    @Test
    void test_businessRuleViolation_featureFailed() {
        payments.mockListCorrupted(blockedIpList);
        long evaluationId = assess(PaymentRiskRequestBuilder.template().build())
            .assertResult(TechnicalStatus.FAILED.getCode(), null).evaluationId();

        JsonNode summary = getOk(EVALUATIONS + "/" + evaluationId);
        JsonNode explanation = getOk(EVALUATIONS + "/" + evaluationId + "/explanation");

        JsonNode failed = variable(explanation, ipBlocked);
        assertAll(() -> assertEquals(TechnicalStatus.FAILED.getCode(), summary.path("result").asString()),
            () -> assertEquals(FeatureComputationException.FEATURE_ERROR, summary.path("failureCode").asString()),
            () -> assertEquals(TechnicalStatus.FAILED.getCode(), failed.path("technicalStatus").asString()),
            () -> assertEquals(FeatureComputationException.FEATURE_ERROR, failed.path("failureCode").asString()),
            () -> assertTrue(failed.path("value").isNull(), "失败变量无值"),
            () -> assertEquals("0", variable(explanation, cardCount).path("value").asString(), "此前完成的变量"),
            () -> assertFalse(explanation.path("pathAvailable").asBoolean(), "流程未执行"),
            () -> assertEquals(0, explanation.path("rules").size()));
    }

    /** 影子运行：影子评估可按 ID 查询结论与解释，输出取结束节点配置；基线解释列出影子并标出认证要求变化。 */
    @Test
    void test_normalFlow_shadow() {
        EvaluateDecisionRequestBody request = PaymentRiskRequestBuilder.template().amount("100").build();
        long baselineId = assess(request).assertResult(DecisionValue.ALLOW.getCode(),
            AuthenticationRequirement.NOT_REQUIRED.getCode()).evaluationId();
        long candidate = validatedRelease(saveFlow(newCode("T_FLOW"), recheckGraph(blockRuleSet, authRuleSet("50"))));
        runShadow(candidate);

        JsonNode baseline = getOk(EVALUATIONS + "/" + baselineId + "/explanation");
        assertEquals(1, baseline.path("shadows").size(), "基线的影子评估");
        JsonNode shadowItem = baseline.path("shadows").path(0);
        String shadowId = shadowItem.path("evaluationId").asString();
        JsonNode shadow = getOk(EVALUATIONS + "/" + shadowId);
        JsonNode shadowExplanation = getOk(EVALUATIONS + "/" + shadowId + "/explanation");

        assertAll("影子", () -> assertEquals(DecisionValue.ALLOW.getCode(), shadowItem.path("result").asString()),
            () -> assertFalse(shadowItem.path("decisionChanged").asBoolean()),
            () -> assertTrue(shadowItem.path("outputChanged").asBoolean(), "认证要求变化"),
            () -> assertEquals(EvaluationMode.SHADOW.getCode(), shadow.path("mode").asString()),
            () -> assertEquals(Long.toString(baselineId), shadow.path("baselineEvaluationId").asString()),
            () -> assertEquals(request.getBusinessKey(), shadow.path("businessKey").asString()),
            () -> assertEquals(AuthenticationRequirement.THREE_DS_REQUIRED.getCode(),
                shadow.path("outputs").path(AUTHENTICATION).asString()),
            () -> assertEquals(List.of("start", "block", "auth", "allow_3ds"), nodeKeys(shadowExplanation)),
            () -> assertEquals(request.getInputs().get("clientIpAddress"),
                shadowExplanation.path("inputs").path("clientIpAddress").asString(), "影子取基线输入"),
            () -> assertEquals(0, shadowExplanation.path("shadows").size()));
    }

    /** 解释响应含输入原值，访问日志只记元数据，不出现 IP、User-Agent、卡指纹与消费者引用。 */
    @Test
    void test_edgeCase_restrictedValuesNotLogged() {
        EvaluateDecisionRequestBody request = PaymentRiskRequestBuilder.template().build();
        long evaluationId = assess(request).assertResult(DecisionValue.ALLOW.getCode(),
            AuthenticationRequirement.NOT_REQUIRED.getCode()).evaluationId();

        try (HttpLogProbe probe = HttpLogProbe.start()) {
            JsonNode explanation = getOk(EVALUATIONS + "/" + evaluationId + "/explanation");

            assertAll(() -> assertEquals(request.getInputs().get("userAgent"),
                    explanation.path("inputs").path("userAgent").asString(), "响应含原值"),
                () -> assertFalse(probe.rendered().contains(request.getInputs().get("clientIpAddress")), "IP"),
                () -> assertFalse(probe.rendered().contains(request.getInputs().get("userAgent")), "User-Agent"),
                () -> assertFalse(probe.rendered().contains(request.getInputs().get("cardFingerprint")), "卡指纹"),
                () -> assertFalse(probe.rendered().contains(request.getInputs().get("consumerReference")), "消费者引用"));
        }
    }

    /** 评估或业务身份不存在：404。 */
    @Test
    void test_noDataFound() {
        assertProblem(EVALUATIONS + "/123456789", 404, NOT_FOUND.getCode());
        assertProblem(EVALUATIONS + "/123456789/explanation", 404, NOT_FOUND.getCode());
        assertProblem(EVALUATIONS + "?pointCode=" + PAYMENT + "&businessKey=123456789", 404, NOT_FOUND.getCode());
    }

    /** ID、风控点、业务身份格式错误，缺少参数或 Query 未声明：400。 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/abc", "/0", "/99999999999999999999", "/1?probe=x", "/1/explanation?probe=x",
        "?pointCode=PAYMENT_ATTEMPT_RISK", "?businessKey=1", "?pointCode=UNKNOWN_POINT&businessKey=1",
        "?pointCode=payment&businessKey=1", "?pointCode=PAYMENT_ATTEMPT_RISK&businessKey=abc",
        "?pointCode=PAYMENT_ATTEMPT_RISK&businessKey=1&probe=x", "?pointCode=PAYMENT_ATTEMPT_RISK&businessKey=",
        "?pointCode=PAYMENT_ATTEMPT_RISK&businessKey=9223372036854775808"})
    void test_invalidParameter(String suffix) {
        assertProblem(EVALUATIONS + suffix, 400, INVALID_REQUEST.getCode());
    }

    /**
     * 带复用的流程：开始 → 拦截 → 拒绝/审核结束，否则 → 认证 → 有命中走“放行（需要 3DS）”，否则 → 再次经过拦截（复用）→ 放行
     *
     * @param block 拦截规则集版本
     * @param auth 认证规则集版本
     * @return 配置图
     */
    private GraphSpec recheckGraph(long block, long auth) {
        return new GraphSpec(List.of(
            new NodeSpec("start", FlowNodeType.START, "Start", 0, 0, null, null, null, null),
            new NodeSpec("block", FlowNodeType.RULE_SET, "Block", 120, 0, block, null, null, null),
            new NodeSpec("auth", FlowNodeType.RULE_SET, "Auth", 240, 0, auth, null, null, null),
            new NodeSpec("recheck", FlowNodeType.RULE_SET, "Recheck", 360, 0, block, null, null, null),
            new NodeSpec("deny", FlowNodeType.END, "Deny", 240, 120, null, DecisionValue.DENY, reason, DENY_MESSAGE),
            new NodeSpec("review", FlowNodeType.END, "Review", 240, -120, null, DecisionValue.REVIEW, null, null),
            new NodeSpec("allow", FlowNodeType.END, "Allow", 480, 0, null, DecisionValue.ALLOW, null, null),
            new NodeSpec("allow_3ds", FlowNodeType.END, "Allow with 3DS", 360, 120, null, DecisionValue.ALLOW, null, null,
                threeDsRequired())),
            List.of(new EdgeSpec("enter", "start", "block", FlowEdgeType.UNCONDITIONAL, 0, "Enter", null),
                new EdgeSpec("to_deny", "block", "deny", FlowEdgeType.CONDITIONAL, 1, "Denied",
                    nodeDecision(DecisionValue.DENY)),
                new EdgeSpec("to_review", "block", "review", FlowEdgeType.CONDITIONAL, 2, "Reviewed",
                    nodeDecision(DecisionValue.REVIEW)),
                new EdgeSpec("to_auth", "block", "auth", FlowEdgeType.ELSE, 0, "Otherwise", null),
                new EdgeSpec("to_3ds", "auth", "allow_3ds", FlowEdgeType.CONDITIONAL, 1, "Authentication", nodeMatched()),
                new EdgeSpec("to_recheck", "auth", "recheck", FlowEdgeType.ELSE, 0, "Recheck", null),
                new EdgeSpec("finish", "recheck", "allow", FlowEdgeType.UNCONDITIONAL, 0, "Finish", null)));
    }

    /** 金额超过阈值时命中（放行动作）的认证规则集。 */
    private long authRuleSet(String threshold) {
        return frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_AUTH")).pointCode(PAYMENT)
            .rules(rule("THREE_DS_AMOUNT", "3DS amount", 10, action("ALLOW_PAYMENT"),
                predicate("root", null, 1, "GT", variableOperand("PAY_INPUT_AMOUNT", "NUMBER"), money(threshold))))
            .build());
    }

    /** 引用常量变量的右操作数。 */
    private static OperandRequestBody constantOperand(String code) {
        return new OperandRequestBody("RIGHT", 1, "VARIABLE", null, "NUMBER", null, code, null, null, null);
    }

    /** 启动影子运行并等待完成。 */
    private void runShadow(long releaseId) {
        StartShadowRunRequestBody body = new StartShadowRunRequestBody();
        body.setSampleTarget(10);
        postOk(EXECUTIONS + "/" + releaseId + "/shadow-runs", body);
        long deadline = System.currentTimeMillis() + SHADOW_WAIT_MILLIS;
        while (!ShadowRunStatus.COMPLETED.getCode().equals(release(releaseId).path("shadowRun").path("status").asString())) {
            if (System.currentTimeMillis() > deadline) {
                fail("影子回放未在期限内完成: " + releaseId);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail("等待影子回放被中断");
            }
        }
    }

    /** 读取执行发布详情。 */
    private JsonNode release(long releaseId) {
        EntityExchangeResult<byte[]> result = http.get(EXECUTIONS + "/" + releaseId, headers());
        assertEquals(200, result.getStatus().value(), () -> new String(result.getResponseBody()));
        return objectMapper.readTree(result.getResponseBody());
    }

    /** 内部直连的 GET，要求 200。 */
    private JsonNode getOk(String path) {
        EntityExchangeResult<byte[]> result = http.get(path, internalHeaders());
        assertEquals(200, result.getStatus().value(), () -> path + " " + new String(result.getResponseBody()));
        return objectMapper.readTree(result.getResponseBody());
    }

    /** 断言 Problem 状态与错误码。 */
    private void assertProblem(String path, int status, String code) {
        HttpHeaders headers = internalHeaders();
        new EvaluationResponseChecker(http.get(path, headers), objectMapper, headers.getFirst(ApiHeaderNames.REQUEST_ID))
            .assertProblem(status, code);
    }

    /** 内部直连请求头：只有 Request ID，不带 Gateway 身份。 */
    private static HttpHeaders internalHeaders() {
        return TestRequestHeaders.internal("risk-evaluation-query-" + UUID.randomUUID());
    }

    /** 路径中的节点键。 */
    private static List<String> nodeKeys(JsonNode explanation) {
        List<String> keys = new ArrayList<>();
        explanation.path("path").forEach(step -> keys.add(step.path("nodeKey").asString()));
        return keys;
    }

    /** 路径中各非结束节点所走的连线键。 */
    private static List<String> exitEdges(JsonNode explanation) {
        List<String> keys = new ArrayList<>();
        explanation.path("path").forEach(step -> {
            if (step.hasNonNull("exitEdgeKey")) {
                keys.add(step.path("exitEdgeKey").asString());
            }
        });
        return keys;
    }

    /** 执行过的规则编码，按执行顺序。 */
    private static List<String> ruleCodes(JsonNode explanation) {
        List<String> codes = new ArrayList<>();
        explanation.path("rules").forEach(rule -> codes.add(rule.path("ruleCode").asString()));
        return codes;
    }

    /** 按编码取变量；不存在时失败。 */
    private static JsonNode variable(JsonNode explanation, String code) {
        for (JsonNode item : explanation.path("variables")) {
            if (code.equals(item.path("variableCode").asString())) {
                return item;
            }
        }
        return fail("解释中没有变量: " + code);
    }
}
