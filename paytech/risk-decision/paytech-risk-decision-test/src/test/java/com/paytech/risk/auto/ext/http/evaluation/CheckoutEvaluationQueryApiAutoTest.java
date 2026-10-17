package com.paytech.risk.auto.ext.http.evaluation;

import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.NOT_FOUND;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.api.evaluation.RiskEvaluationQueryApi;
import com.paytech.risk.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.EvaluationMode;
import com.paytech.risk.test.base.AbstractCheckoutRiskApiTest;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;
import com.paytech.risk.test.builder.evaluation.CheckoutRiskRequestBuilder;
import com.paytech.risk.test.checker.evaluation.EvaluationResponseChecker;
import com.paytech.risk.test.probe.HttpLogProbe;

import tools.jackson.databind.JsonNode;

/**
 * 结账评估的结论与解释查询：经真实配置入口准备结账流程并激活，经统一评估入口产生评估后查询
 * <li>正常流程：按评估 ID 与按“CHECKOUT_PREPARE + 订单 ID”查询一致，没有附加输出；解释给出 12 个输入原值（无 Local Partner 时为空）、路径、规则与 v5 输入变量取值。</li>
 * <li>拒绝：原因码与文案取自拒绝结束节点。</li>
 * <li>日志：结账评估与查询的访问日志只记元数据，请求体与输入原值不进入日志。</li>
 * <li>参数与无数据：订单 ID 格式错误或超过 BIGINT 上限返回 400；订单没有评估返回 404。</li>
 *
 * @see RiskEvaluationQueryApi
 */
class CheckoutEvaluationQueryApiAutoTest extends AbstractCheckoutRiskApiTest {
    private static final String EVALUATIONS = "/risk-decisions/v1/evaluations";

    private String reason;

    private String flowCode;

    /** 每例准备“受限国家拒绝、大额审核”的结账流程并激活。 */
    @BeforeEach
    void setUpClassMocks() {
        reason = rejectionReason();
        long block = frozenRuleSet(RuleSetCreateRequestBuilder.template(checkoutCode("BLOCK")).pointCode(CHECKOUT)
            .rules(rule("RESTRICTED_COUNTRY", "Restricted country", 10, reason, DENY_MESSAGE, action(DENY), List.of(),
                    predicate("root", null, 1, "EQ", variable("LEFT", 1, "INPUT_COUNTRY_CODE", "STRING"),
                        literal("RIGHT", 1, "STRING", "KP"))),
                rule("LARGE_NET", "Large net", 20, action(REVIEW), predicate("root", null, 1, "GT",
                    variableOperand("INPUT_NET_AMOUNT", "NUMBER"), money("1000"))))
            .build());
        flowCode = checkoutCode("FLOW");
        activeRelease(saveFlow(flowCode, blockGraph(block, reason)));
    }

    /** 放行：按 ID 与按订单查询一致；解释的 12 个输入原值、路径、规则与 v5 输入变量取值完整。 */
    @Test
    void test_normalFlow() {
        EvaluateDecisionRequestBody request = CheckoutRiskRequestBuilder.template().build();
        long evaluationId = assess(request).assertResult(DecisionValue.ALLOW.getCode()).evaluationId();

        JsonNode summary = getOk(EVALUATIONS + "/" + evaluationId);
        assertAll("结论", () -> assertEquals(CHECKOUT, summary.path("pointCode").asString()),
            () -> assertEquals(request.getBusinessKey(), summary.path("businessKey").asString()),
            () -> assertEquals(EvaluationMode.PRIMARY.getCode(), summary.path("mode").asString()),
            () -> assertEquals(DecisionValue.ALLOW.getCode(), summary.path("result").asString()),
            () -> assertEquals(0, summary.path("outputs").size(), "结账没有附加输出"),
            () -> assertEquals(flowCode, summary.path("flowCode").asString()),
            () -> assertTrue(summary.path("pathAvailable").asBoolean(), "有路径"));
        assertEquals(summary, getOk(EVALUATIONS + "?pointCode=" + CHECKOUT + "&businessKey=" + request.getBusinessKey()),
            "按订单查询");

        JsonNode explanation = getOk(EVALUATIONS + "/" + evaluationId + "/explanation");
        assertAll("解释", () -> assertEquals(12, explanation.path("inputs").size(), "输入字段数"),
            () -> assertEquals(request.getInputs().get("entityNo"), explanation.path("inputs").path("entityNo").asString()),
            () -> assertTrue(explanation.path("inputs").path("localPartnerId").isNull(), "无 Local Partner"),
            () -> assertEquals("100", explanation.path("inputs").path("netAmount").asString(), "金额去尾零"),
            () -> assertEquals("120", explanation.path("inputs").path("merchandiseAmount").asString()),
            () -> assertEquals("2", explanation.path("inputs").path("lineCount").asString()),
            () -> assertEquals(List.of("start", "block", "allow"), nodeKeys(explanation)),
            () -> assertEquals(List.of("RESTRICTED_COUNTRY", "LARGE_NET"), ruleCodes(explanation)),
            () -> assertEquals("AE", variableOf(explanation, "INPUT_COUNTRY_CODE").path("value").asString()),
            () -> assertEquals("100", variableOf(explanation, "INPUT_NET_AMOUNT").path("value").asString()),
            () -> assertEquals(0, explanation.path("shadows").size()));
    }

    /** 拒绝：原因码与文案取自拒绝结束节点，路径止于拒绝结束。 */
    @Test
    void test_businessRuleViolation() {
        EvaluateDecisionRequestBody request = CheckoutRiskRequestBuilder.template().consumerCountryCode("KP").build();
        long evaluationId = assess(request).assertResult(DecisionValue.DENY.getCode()).evaluationId();

        JsonNode summary = getOk(EVALUATIONS + "?pointCode=" + CHECKOUT + "&businessKey=" + request.getBusinessKey());
        JsonNode explanation = getOk(EVALUATIONS + "/" + evaluationId + "/explanation");

        assertAll(() -> assertEquals(DecisionValue.DENY.getCode(), summary.path("result").asString()),
            () -> assertEquals(reason, summary.path("reasonCode").asString()),
            () -> assertEquals(DENY_MESSAGE, summary.path("reasonMessage").asString()),
            () -> assertEquals(List.of("start", "block", "deny"), nodeKeys(explanation)),
            () -> assertEquals("KP", explanation.path("inputs").path("consumerCountryCode").asString()));
    }

    /** 结账评估与查询的访问日志只记元数据：请求体、输入原值不出现在日志中。 */
    @Test
    void test_edgeCase_inputsNotLogged() {
        EvaluateDecisionRequestBody request = CheckoutRiskRequestBuilder.template().partner("377777")
            .industryCode("T_LOG_PROBE_INDUSTRY").mccCode("7777").build();

        try (HttpLogProbe probe = HttpLogProbe.start()) {
            long evaluationId = assess(request).assertResult(DecisionValue.ALLOW.getCode()).evaluationId();
            JsonNode explanation = getOk(EVALUATIONS + "/" + evaluationId + "/explanation");

            assertAll(() -> assertEquals("T_LOG_PROBE_INDUSTRY", explanation.path("inputs").path("industryCode").asString(),
                    "响应含原值"),
                () -> assertFalse(probe.rendered().contains("T_LOG_PROBE_INDUSTRY"), "行业"),
                () -> assertFalse(probe.rendered().contains("377777"), "Local Partner"),
                () -> assertFalse(probe.rendered().contains("\"mccCode\""), "请求体"));
        }
    }

    /** 订单没有评估：404；BIGINT 上限本身是合法订单 ID。 */
    @Test
    void test_noDataFound() {
        assertProblem(EVALUATIONS + "?pointCode=" + CHECKOUT + "&businessKey=123456789", 404, NOT_FOUND.getCode());
        assertProblem(EVALUATIONS + "?pointCode=" + CHECKOUT + "&businessKey=" + Long.MAX_VALUE, 404,
            NOT_FOUND.getCode());
    }

    /** 订单 ID 不符合结账业务身份格式或超过 BIGINT 上限：400。 */
    @Test
    void test_invalidParameter() {
        assertProblem(EVALUATIONS + "?pointCode=" + CHECKOUT + "&businessKey=0", 400, INVALID_REQUEST.getCode());
        assertProblem(EVALUATIONS + "?pointCode=" + CHECKOUT + "&businessKey=abc", 400, INVALID_REQUEST.getCode());
        assertProblem(EVALUATIONS + "?pointCode=" + CHECKOUT + "&businessKey=9223372036854775808", 400,
            INVALID_REQUEST.getCode());
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
        return TestRequestHeaders.internal("risk-checkout-query-" + UUID.randomUUID());
    }

    /** 路径中的节点键。 */
    private static List<String> nodeKeys(JsonNode explanation) {
        List<String> keys = new ArrayList<>();
        explanation.path("path").forEach(step -> keys.add(step.path("nodeKey").asString()));
        return keys;
    }

    /** 解释中的规则编码，按执行顺序。 */
    private static List<String> ruleCodes(JsonNode explanation) {
        List<String> codes = new ArrayList<>();
        explanation.path("rules").forEach(rule -> codes.add(rule.path("ruleCode").asString()));
        return codes;
    }

    /** 按编码取变量；不存在时失败。 */
    private static JsonNode variableOf(JsonNode explanation, String code) {
        for (JsonNode item : explanation.path("variables")) {
            if (code.equals(item.path("variableCode").asString())) {
                return item;
            }
        }
        return fail("解释中没有变量: " + code);
    }
}
