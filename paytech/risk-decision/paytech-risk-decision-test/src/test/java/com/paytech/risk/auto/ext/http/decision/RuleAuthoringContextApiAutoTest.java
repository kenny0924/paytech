package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.decision.api.DecisionRuleSetManagementApi;
import com.paytech.risk.test.base.RiskDecisionMockTestBase;
import com.paytech.risk.test.checker.decision.RuleAuthoringContextResponseChecker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;

/**
 * 规则配置能力目录查询
 * <li>正常流程：CHECKOUT_PREPARE 返回完整决策点描述、系统 INPUT 变量、运算符、动作、动作参数与主体绑定。</li>
 * <li>未找到：未批准的决策点编码返回 404。</li>
 * <li>参数：决策点编码格式非法、携带任何 Query 或 GET Body 时在查询前拒绝（400）。</li>
 * <li>目录漂移：持久化动作目录与工程注册表不一致时失败关闭（500），不返回默认动作。</li>
 *
 * @see DecisionRuleSetManagementApi#getAuthoringContext(String, String, String)
 */
class RuleAuthoringContextApiAutoTest extends RiskDecisionMockTestBase {
    private static final String PATH = "/decision-rule-sets/v1/authoring-context/%s";

    @Autowired
    private RuleSetMocker ruleSets;

    /** CHECKOUT_PREPARE 的完整配置能力目录。 */
    @Test
    void test_normalFlow() {
        read("CHECKOUT_PREPARE").assertCheckoutPrepareCatalog();
    }

    /** 格式合法但未批准的决策点编码。 */
    @ParameterizedTest(name = "pointCode={0}")
    @MethodSource("unknownPointCodes")
    void test_noDataFound(String pointCode) {
        read(pointCode).assertProblem(404, RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());
    }

    /** 决策点编码不是大写编码格式。 */
    @ParameterizedTest(name = "pointCode={0}")
    @MethodSource("invalidPointCodes")
    void test_invalidParameter(String pointCode) {
        read(pointCode).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 能力目录不声明 Query，未知、重复或空参数均在查询前拒绝。 */
    @ParameterizedTest(name = "query={0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter_query(String query) {
        read("CHECKOUT_PREPARE?" + query).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 携带 Body 的 GET 请求。 */
    @Test
    void test_invalidParameter_getWithBody() {
        String requestId = "risk-rule-authoring-" + UUID.randomUUID();

        new RuleAuthoringContextResponseChecker(http.exchange(HttpMethod.GET, PATH.formatted("CHECKOUT_PREPARE"),
            headers(requestId), "{}"), objectMapper, requestId).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 持久化动作名称与工程注册表漂移时整体失败，不返回目录。 */
    @Test
    void test_businessRuleViolation_actionCatalogDriftFailsClosed() {
        ruleSets.driftActionCatalog();

        read("CHECKOUT_PREPARE").assertProblem(500, INTERNAL_ERROR.getCode());
    }

    static Stream<String> unknownPointCodes() {
        return Stream.of("UNKNOWN_POINT", "CHECKOUT_PREPARE_V2", "PAYMENT_AUTHORIZE");
    }

    static Stream<String> invalidQueries() {
        return Stream.of("pointCode=CHECKOUT_PREPARE", "page=1&page=1", "page=");
    }

    static Stream<String> invalidPointCodes() {
        return Stream.of("checkout_prepare", "Checkout_Prepare", "1CHECKOUT", "_CHECKOUT", "CHECKOUT-PREPARE",
            "C".repeat(65));
    }

    private RuleAuthoringContextResponseChecker read(String pointCode) {
        String requestId = "risk-rule-authoring-" + UUID.randomUUID();
        return new RuleAuthoringContextResponseChecker(http.get(PATH.formatted(pointCode), headers(requestId)),
            objectMapper, requestId);
    }

    /** Gateway 转发的 Operations 可信身份。 */
    private static HttpHeaders headers(String requestId) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", "70001");
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }
}
