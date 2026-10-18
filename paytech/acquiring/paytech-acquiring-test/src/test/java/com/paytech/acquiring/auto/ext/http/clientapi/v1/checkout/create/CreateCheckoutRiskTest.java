package com.paytech.acquiring.auto.ext.http.clientapi.v1.checkout.create;

import static com.paytech.acquiring.test.builder.checkout.CreateCheckoutRequestBuilder.item;
import static com.paytech.acquiring.test.builder.checkout.CreateCheckoutRequestBuilder.template;
import static com.paytech.acquiring.test.mocker.external.CheckoutRiskMocker.body;
import static com.paytech.acquiring.test.mocker.external.CheckoutRiskMocker.delayed;
import static com.paytech.acquiring.test.mocker.external.CheckoutRiskMocker.disconnect;
import static com.paytech.acquiring.test.mocker.external.CheckoutRiskMocker.problem;
import static com.paytech.acquiring.test.mocker.external.CheckoutRiskMocker.raw;
import static com.paytech.acquiring.test.mocker.external.CheckoutRiskMocker.result;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import com.paytech.acquiring.api.client.v1.checkout.ClientCheckoutApi;
import com.paytech.acquiring.api.client.v1.checkout.request.CreateCheckoutRequestBody;
import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.acquiring.domain.enums.EntitySourceType;
import com.paytech.acquiring.domain.enums.RiskDecisionResult;
import com.paytech.acquiring.test.base.checkout.AbstractCreateCheckoutApiTest;
import com.paytech.acquiring.test.builder.external.CommercialFactsBuilder;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.risk.decision.api.enums.RiskDecisionEvaluationErrorCode;
import com.paytech.risk.decision.api.evaluation.request.CheckoutPrepareRiskInput;
import com.paytech.risk.decision.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.decision.api.evaluation.response.EvaluateDecisionResponseBody;

/**
 * 创建结账时经真实 Risk 客户端评估 CHECKOUT_PREPARE（需求 20261016 risk-结账点运行期评估，AC-5）：结论与错误转换、一次重试、请求映射。
 * <li>每个场景断言订单是否落库、Risk 调用次数与对外错误码；传输层不自动重试，次数完全由端口决定。</li>
 *
 * @see ClientCheckoutApi#createCheckout(long, long, String, CreateCheckoutRequestBody)
 */
class CreateCheckoutRiskTest extends AbstractCreateCheckoutApiTest {
    /** 超过测试读超时（500ms）的 Risk 处理耗时。 */
    private static final Duration SLOW = Duration.ofSeconds(1);

    /** 第二层 BeforeEach：本类共用 CCT 商业事实；Risk 响应由各场景配置。 */
    @BeforeEach
    void setUpClassMocks() {
        mockDefaultPreparation();
    }

    /** 放行：冻结 Risk 返回的评估 ID，业务身份为本订单 ID。 */
    @Test
    void test_allow() {
        riskMocker.replies(result(RiskDecisionResult.ALLOW, 7101L));

        invoke(template().build()).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "44.90", "2.00", "42.90");

        long orderId = checkoutChecker.assertRiskAssessment(clientId(), CLIENT_ORDER_ID, 7101L);
        riskMocker.assertCalls(1);
        assertEquals(Long.toString(orderId), riskMocker.requests().getFirst().getBusinessKey());
    }

    /** 审核与拒绝都返回 422 CHECKOUT_NOT_ELIGIBLE，不落订单、不重试。 */
    @ParameterizedTest
    @EnumSource(value = RiskDecisionResult.class, names = {"REVIEW", "DENY"})
    void test_notAllowed(RiskDecisionResult decision) {
        riskMocker.replies(result(decision, 7102L));

        invoke(template().build()).assertProblem(422, CommerceCheckoutErrorCode.CHECKOUT_NOT_ELIGIBLE.getCode());

        checkoutChecker.assertAbsent(clientId(), CLIENT_ORDER_ID);
        riskMocker.assertCalls(1);
    }

    /** 首次读超时后用同一请求体重试，第二次放行后创建。 */
    @Test
    void test_timeoutThenAllow() {
        riskMocker.replies(delayed(SLOW, result(RiskDecisionResult.ALLOW, 7103L)),
            result(RiskDecisionResult.ALLOW, 7104L));

        invoke(template().build()).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "44.90", "2.00", "42.90");

        checkoutChecker.assertRiskAssessment(clientId(), CLIENT_ORDER_ID, 7104L);
        riskMocker.assertCalls(2);
        assertSameRequest(riskMocker.requests());
    }

    /** 两次超时：503 DEPENDENCY_UNAVAILABLE，不落订单。 */
    @Test
    void test_timeoutTwice() {
        var slow = delayed(SLOW, result(RiskDecisionResult.ALLOW, 7105L));
        riskMocker.replies(slow, slow);

        assertUnavailable(2);
        assertSameRequest(riskMocker.requests());
    }

    /** 首次 5xx 后重试放行。 */
    @Test
    void test_serverErrorThenAllow() {
        riskMocker.replies(problem(HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR.getCode()),
            result(RiskDecisionResult.ALLOW, 7106L));

        invoke(template().build()).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "44.90", "2.00", "42.90");

        checkoutChecker.assertRiskAssessment(clientId(), CLIENT_ORDER_ID, 7106L);
        riskMocker.assertCalls(2);
    }

    /** 两次 503（无生效发布）：只调 2 次，证明 HTTP 客户端未对 503 POST 自动重发。 */
    @Test
    void test_notConfiguredTwice() {
        var notConfigured = problem(HttpStatus.SERVICE_UNAVAILABLE,
            RiskDecisionEvaluationErrorCode.RISK_DECISION_NOT_CONFIGURED.getCode());
        riskMocker.replies(notConfigured, notConfigured);

        assertUnavailable(2);
    }

    /** 错误体不是 Problem 的 5xx（代理 HTML、空体）按结果未知处理：重试一次后仍失败返回 503。 */
    @Test
    void test_nonProblemServerErrorTwice() {
        riskMocker.replies(raw(HttpStatus.BAD_GATEWAY, "<html>Bad Gateway</html>"),
            raw(HttpStatus.INTERNAL_SERVER_ERROR, ""));

        assertUnavailable(2);
    }

    /** 首次 5xx 为非 Problem 错误体，重试后放行。 */
    @Test
    void test_nonProblemServerErrorThenAllow() {
        riskMocker.replies(raw(HttpStatus.BAD_GATEWAY, "<html>Bad Gateway</html>"),
            result(RiskDecisionResult.ALLOW, 7113L));

        invoke(template().build()).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "44.90", "2.00", "42.90");

        checkoutChecker.assertRiskAssessment(clientId(), CLIENT_ORDER_ID, 7113L);
        riskMocker.assertCalls(2);
    }

    /** 服务发现中没有 Risk 实例：真实负载均衡无法选址，按结果未知重试一次后返回 503，Mock 未收到请求。 */
    @Test
    void test_noInstance() {
        riskMocker.removeInstances();

        assertUnavailable(0);
    }

    /** 两次断连：503，2 次。 */
    @Test
    void test_disconnectTwice() {
        riskMocker.replies(disconnect(), disconnect());

        assertUnavailable(2);
    }

    /** 首次断连后重试放行。 */
    @Test
    void test_disconnectThenAllow() {
        riskMocker.replies(disconnect(), result(RiskDecisionResult.ALLOW, 7107L));

        invoke(template().build()).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "44.90", "2.00", "42.90");

        checkoutChecker.assertRiskAssessment(clientId(), CLIENT_ORDER_ID, 7107L);
        riskMocker.assertCalls(2);
    }

    /** 首次 409（并发首评）后重试放行。 */
    @Test
    void test_conflictThenAllow() {
        riskMocker.replies(problem(HttpStatus.CONFLICT, CommonErrorCode.CONCURRENT_REQUEST.getCode()),
            result(RiskDecisionResult.ALLOW, 7108L));

        invoke(template().build()).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "44.90", "2.00", "42.90");

        checkoutChecker.assertRiskAssessment(clientId(), CLIENT_ORDER_ID, 7108L);
        riskMocker.assertCalls(2);
        assertSameRequest(riskMocker.requests());
    }

    /** 两次 409：503，2 次。 */
    @Test
    void test_conflictTwice() {
        var conflict = problem(HttpStatus.CONFLICT, CommonErrorCode.CONCURRENT_REQUEST.getCode());
        riskMocker.replies(conflict, conflict);

        assertUnavailable(2);
    }

    /** 已落库的技术终态重放结果相同，不重试，直接 503。 */
    @ParameterizedTest
    @EnumSource(value = RiskDecisionResult.class, names = {"FAILED", "TIMED_OUT"})
    void test_technicalFailure(RiskDecisionResult decision) {
        riskMocker.replies(result(decision, 7109L));

        assertUnavailable(1);
    }

    /** Risk 400 / 422 是对接缺陷：502，不重试，不把 Risk 的 400 透给 Client。 */
    @ParameterizedTest
    @ValueSource(strings = {"INVALID_REQUEST", "VALIDATION_ERROR"})
    void test_invalidRequest(String code) {
        HttpStatus status = CommonErrorCode.INVALID_REQUEST.getCode().equals(code) ? HttpStatus.BAD_REQUEST
            : HttpStatus.UNPROCESSABLE_CONTENT;
        riskMocker.replies(problem(status, code));

        assertProtocolError();
    }

    /** 结论未知或评估 ID 非法：502，不重试。 */
    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN|7110", "ALLOW|0", "ALLOW|abc", "ALLOW|9999999999999999999", "ALLOW|"})
    void test_invalidResponse(String sample) {
        String[] parts = sample.split("\\|", -1);
        var response = new EvaluateDecisionResponseBody();
        response.setResult(parts[0]);
        response.setEvaluationId(parts[1].isEmpty() ? null : parts[1]);
        response.setOutputs(Map.of());
        riskMocker.replies(body(response));

        assertProtocolError();
    }

    /** PLATFORM 来源：合同版本、业务身份与 11 个输入；金额去尾零，行数为明细数，不传 Local Partner。 */
    @Test
    void test_requestMapping() {
        riskMocker.replies(result(RiskDecisionResult.ALLOW, 7111L));

        invoke(template().build()).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "44.90", "2.00", "42.90");

        long orderId = checkoutChecker.assertRiskAssessment(clientId(), CLIENT_ORDER_ID, 7111L);
        var request = riskMocker.requests().getFirst();
        var expected = new CheckoutPrepareRiskInput(Long.toString(clientId()), "AE001",
            EntitySourceType.PLATFORM.getCode(), null, "US", "USD", "44.9", "2", "42.9", "RETAIL", "5999", "2");
        assertAll(() -> assertEquals(CheckoutPrepareRiskInput.POINT_CODE, request.getPointCode()),
            () -> assertEquals(CheckoutPrepareRiskInput.CONTRACT_VERSION, request.getContractVersion()),
            () -> assertEquals(Long.toString(orderId), request.getBusinessKey()),
            () -> assertEquals(expected.inputs(), request.getInputs()),
            () -> assertEquals(11, request.getInputs().size()));
    }

    /** PARTNER 来源带 Local Partner ID；整额无小数时金额为整数字符串。 */
    @Test
    void test_partnerSource() {
        preparationMocker.mockFacts(clientId(), "US", "USD", CommercialFactsBuilder.template()
            .entitySourceType(EntitySourceType.PARTNER.getCode()).localPartnerId(300001L).partnerShareVersionId(401L)
            .platformChargeVersionId(402L).platformServicePartyId(403L).partnerAgreementId(404L)
            .partnerAgreementOperatingEntityId(405L).build());
        riskMocker.replies(result(RiskDecisionResult.ALLOW, 7112L));
        var items = List.of(item("single", "Single item", "1", "10", null));

        invoke(template().items(items).build()).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "10.00", "0.00",
            "10.00");

        checkoutChecker.assertRiskAssessment(clientId(), CLIENT_ORDER_ID, 7112L);
        var expected = new CheckoutPrepareRiskInput(Long.toString(clientId()), "AE001",
            EntitySourceType.PARTNER.getCode(), "300001", "US", "USD", "10", "0", "10", "RETAIL", "5999", "1");
        assertEquals(expected.inputs(), riskMocker.requests().getFirst().getInputs());
    }

    /** 两次调用的业务身份与输入完全相同（Risk 按业务身份重放）。 */
    private static void assertSameRequest(List<EvaluateDecisionRequestBody> requests) {
        assertEquals(2, requests.size());
        var first = requests.get(0);
        var second = requests.get(1);
        assertAll(() -> assertEquals(first.getBusinessKey(), second.getBusinessKey()),
            () -> assertEquals(first.getContractVersion(), second.getContractVersion()),
            () -> assertEquals(first.getInputs(), second.getInputs()));
    }

    /** 503 DEPENDENCY_UNAVAILABLE、不落订单，并核对 Risk 调用次数。 */
    private void assertUnavailable(int calls) {
        invoke(template().build()).assertProblem(503, CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE.getCode());
        checkoutChecker.assertAbsent(clientId(), CLIENT_ORDER_ID);
        riskMocker.assertCalls(calls);
    }

    /** 502 UPSTREAM_PROTOCOL_ERROR、不落订单、只调 1 次。 */
    private void assertProtocolError() {
        invoke(template().build()).assertProblem(502, CommerceCheckoutErrorCode.UPSTREAM_PROTOCOL_ERROR.getCode());
        checkoutChecker.assertAbsent(clientId(), CLIENT_ORDER_ID);
        riskMocker.assertCalls(1);
    }
}
