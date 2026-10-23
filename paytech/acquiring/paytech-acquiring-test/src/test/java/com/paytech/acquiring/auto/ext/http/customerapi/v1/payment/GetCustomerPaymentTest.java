package com.paytech.acquiring.auto.ext.http.customerapi.v1.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.acquiring.api.checkout.v1.enums.CustomerCheckoutErrorCode;
import com.paytech.acquiring.api.checkout.v1.enums.CustomerPaymentStatus;
import com.paytech.acquiring.api.checkout.v1.payment.CustomerPaymentApi;
import com.paytech.acquiring.domain.checkout.CreateCheckoutContext;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.acquiring.domain.enums.PaymentAttemptStatus;
import com.paytech.acquiring.test.base.MockTestBase;
import com.paytech.acquiring.test.checker.customer.CheckoutFlowResponseChecker;
import com.paytech.acquiring.test.dal.PaymentResultTestMapper;
import com.paytech.acquiring.test.mocker.external.PaymentSubmissionMocker;
import com.paytech.acquiring.test.mocker.local.CheckoutPaymentDataMocker;
import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.payment.api.execution.response.GetSubmissionResponseBody;

/**
 * Customer 查询原支付提交：真实 HTTP 入口经公共客户端调用 Payment 刷新接口（3DS 全链路 technique 3.3.6：处理中时由 Payment 限频查单确认）的
 * 测试接口，覆盖状态映射、REDIRECT 交互（AC-03）、确认中（AC-10）、未找到与不可用降级，以及订单到期后的结果确认（到期后支付结果确认）。
 *
 * @see CustomerPaymentApi#getPayment(String, String)
 */
class GetCustomerPaymentTest extends MockTestBase {
    private static final long SUBMISSION_ID = 7_000_001L;
    private static final String ATTEMPT_ID = "8000001";
    private static final String THREE_DS = "THREE_DS_CHALLENGE";
    private static final String REDIRECT = "REDIRECT";

    @Autowired
    CheckoutPaymentDataMocker data;
    @Autowired
    PaymentSubmissionMocker submissions;
    @Autowired
    PaymentResultTestMapper mapper;
    private CreateCheckoutContext fixture;

    /** 每例准备已绑定 Intent 的订单；Intent ID 与订单 ID 相同。 */
    @BeforeEach
    void setUpPaymentQuery() {
        fixture = data.mockPrepared(true);
        mapper.insertPayment(orderId(), orderId(), fixture.getOrder().getCreateTime());
    }

    /** 成功、失败与取消直接映射；失败代码透传，查询坐标来自本地订单、Intent 与 Client。 */
    @ParameterizedTest
    @CsvSource({"SUCCEEDED,SUCCEEDED,", "FAILED,FAILED,CARD_DECLINED", "CANCELLED,CANCELLED,"})
    void test_normalFlow(String paymentStatus, CustomerPaymentStatus expected, String failureCode) {
        var response = submission(paymentStatus);
        response.setFailureCode(failureCode);
        submissions.mockResponse(response);

        var body = query().assertStatus(200).assertRequestId().paymentBody();

        assertEquals(expected.getCode(), body.getStatus());
        assertEquals(ATTEMPT_ID, body.getAttemptId());
        assertEquals(Long.toString(SUBMISSION_ID), body.getSubmissionId());
        assertEquals(Instant.parse("2026-09-08T00:00:01Z"), body.getObservedAt());
        assertNull(body.getNextAction());
        assertNull(body.getPollAfterMs());
        assertEquals(failureCode, body.getFailure() == null ? null : body.getFailure().getCode());
        assertEquals(List.of(new PaymentSubmissionMocker.Call(Long.toString(orderId()), Long.toString(SUBMISSION_ID),
            Long.toString(fixture.getOrder().getClientId()), PaymentSubmissionMocker.REFRESH)), submissions.calls());
    }

    /** 渠道失败码不符合公开格式（如纯数字）时统一为 CHANNEL_DECLINED，不当作协议错误。 */
    @Test
    void test_nonConformingFailureCode() {
        var response = submission("FAILED");
        response.setFailureCode("05");
        submissions.mockResponse(response);

        var body = query().assertStatus(200).paymentBody();

        assertEquals(CustomerPaymentStatus.FAILED.getCode(), body.getStatus());
        assertEquals("CHANNEL_DECLINED", body.getFailure().getCode());
    }

    /** 处理中带交互映射为待消费者操作：动作 ID 为尝试 ID，查询结果没有 3DS 跳转地址。 */
    @Test
    void test_requiresActionWithoutRedirect() {
        var response = submission(PaymentAttemptStatus.PROCESSING.getCode());
        var interaction = new GetSubmissionResponseBody.Interaction();
        interaction.setType(THREE_DS);
        interaction.setExpiresAt("2026-09-08T00:10:00Z");
        response.setInteraction(interaction);
        submissions.mockResponse(response);

        var body = query().assertStatus(200).paymentBody();

        assertEquals(CustomerPaymentStatus.REQUIRES_ACTION.getCode(), body.getStatus());
        assertEquals(ATTEMPT_ID, body.getNextAction().getActionId());
        assertEquals(THREE_DS, body.getNextAction().getType());
        assertEquals(Instant.parse("2026-09-08T00:10:00Z"), body.getNextAction().getExpiresAt());
        assertNull(body.getNextAction().getRedirectUrl());
    }

    /** 渠道普通跳转（REDIRECT）同样映射为待消费者操作（AC-03）。 */
    @Test
    void test_requiresRedirectAction() {
        var response = submission(PaymentAttemptStatus.PROCESSING.getCode());
        var interaction = new GetSubmissionResponseBody.Interaction();
        interaction.setType(REDIRECT);
        interaction.setExpiresAt("2026-09-08T00:10:00Z");
        response.setInteraction(interaction);
        submissions.mockResponse(response);

        var body = query().assertStatus(200).paymentBody();

        assertEquals(CustomerPaymentStatus.REQUIRES_ACTION.getCode(), body.getStatus());
        assertEquals(REDIRECT, body.getNextAction().getType());
        assertEquals(PaymentSubmissionMocker.REFRESH, submissions.calls().getFirst().operation());
    }

    /** 处理中无交互（交互已过期、仍在确认，AC-10）保持处理中并继续轮询。 */
    @Test
    void test_processing() {
        submissions.mockResponse(submission(PaymentAttemptStatus.PROCESSING.getCode()));

        var body = query().assertStatus(200).paymentBody();

        assertEquals(CustomerPaymentStatus.PROCESSING.getCode(), body.getStatus());
        assertNull(body.getNextAction());
        assertEquals(2000, body.getPollAfterMs());
    }

    /** Payment 返回 404 时为支付提交不存在，不推断支付失败。 */
    @Test
    void test_noDataFound() {
        submissions.mockNotFound();

        query().assertProblem(404, CustomerCheckoutErrorCode.PAYMENT_SUBMISSION_NOT_FOUND.getCode());
        assertEquals(1, submissions.calls().size());
    }

    /** Payment 不可用时降级为处理中并继续轮询，不伪造观察时间或结果。 */
    @Test
    void test_paymentUnavailable() {
        submissions.mockFailure(MockHttpFault.disconnect());

        var body = query().assertStatus(200).paymentBody();

        assertEquals(CustomerPaymentStatus.PROCESSING.getCode(), body.getStatus());
        assertNull(body.getAttemptId());
        assertNull(body.getObservedAt());
        assertEquals(2000, body.getPollAfterMs());
    }

    /** 响应归属与本地坐标不一致或状态未知时视为上游协议错误。 */
    @ParameterizedTest
    @CsvSource({"intent", "status"})
    void test_edgeCase(String invalid) {
        var response = submission(PaymentAttemptStatus.SUCCEEDED.getCode());
        if ("intent".equals(invalid)) {
            response.setIntentId(Long.toString(orderId() + 1));
        } else {
            response.setStatus(PaymentAttemptStatus.UNKNOWN.getCode());
        }
        submissions.mockResponse(response);

        query().assertProblem(502, CommerceCheckoutErrorCode.UPSTREAM_PROTOCOL_ERROR.getCode());
    }

    /** 到期前已提交的支付在到期后仍刷新 Payment 并返回真实结果：3DS 挑战中、成功与失败（AC-01、AC-02）。 */
    @ParameterizedTest
    @CsvSource({"PROCESSING,REQUIRES_ACTION", "SUCCEEDED,SUCCEEDED", "FAILED,FAILED"})
    void test_expiredResultQueried(PaymentAttemptStatus paymentStatus, CustomerPaymentStatus expected) {
        var response = submission(paymentStatus.getCode());
        if (paymentStatus == PaymentAttemptStatus.PROCESSING) {
            var interaction = new GetSubmissionResponseBody.Interaction();
            interaction.setType(THREE_DS);
            interaction.setExpiresAt("2026-09-08T00:40:00Z");
            response.setInteraction(interaction);
        }
        submissions.mockResponse(response);
        expire();

        var body = query().assertStatus(200).paymentBody();

        assertEquals(expected.getCode(), body.getStatus());
        assertEquals(List.of(new PaymentSubmissionMocker.Call(Long.toString(orderId()), Long.toString(SUBMISSION_ID),
            Long.toString(fixture.getOrder().getClientId()), PaymentSubmissionMocker.REFRESH)), submissions.calls());
    }

    /** 到期后渠道仍处理中时返回确认中并继续轮询（AC-03）。 */
    @Test
    void test_expiredProcessing() {
        submissions.mockResponse(submission(PaymentAttemptStatus.PROCESSING.getCode()));
        expire();

        var body = query().assertStatus(200).paymentBody();

        assertEquals(CustomerPaymentStatus.PROCESSING.getCode(), body.getStatus());
        assertNull(body.getNextAction());
        assertEquals(2000, body.getPollAfterMs());
    }

    /** 订单已被过期收尾写成 EXPIRED 时仍返回 Payment 的最终失败结果（AC-02、B4）。 */
    @Test
    void test_expiredOrderStatus() {
        submissions.mockResponse(submission(PaymentAttemptStatus.FAILED.getCode()));
        mapper.expireOrder(orderId());

        var body = query().assertStatus(200).paymentBody();

        assertEquals(CustomerPaymentStatus.FAILED.getCode(), body.getStatus());
        assertEquals(OrderStatus.EXPIRED.getCode(), mapper.order(orderId()).get("order_status"));
    }

    /** 到期时没有支付记录视同未提交，返回 CHECKOUT_EXPIRED 且不调用 Payment（AC-04）。 */
    @Test
    void test_expiredWithoutPayment() {
        mapper.deletePayment(orderId());
        expire();

        query().assertProblem(410, CustomerCheckoutErrorCode.CHECKOUT_EXPIRED.getCode());
        assertEquals(0, submissions.calls().size());
    }

    /** 到期后 Payment 未找到该提交时视同未提交，返回 CHECKOUT_EXPIRED（D1）。 */
    @Test
    void test_expiredSubmissionNotFound() {
        submissions.mockNotFound();
        expire();

        query().assertProblem(410, CustomerCheckoutErrorCode.CHECKOUT_EXPIRED.getCode());
        assertEquals(1, submissions.calls().size());
    }

    /** 到期后 Payment 不可用仍降级为确认中，不返回到期（B3）。 */
    @Test
    void test_expiredPaymentUnavailable() {
        submissions.mockFailure(MockHttpFault.disconnect());
        expire();

        var body = query().assertStatus(200).paymentBody();

        assertEquals(CustomerPaymentStatus.PROCESSING.getCode(), body.getStatus());
        assertEquals(2000, body.getPollAfterMs());
    }

    /** 把时钟拨过订单到期时刻（创建后 30 分钟）。 */
    void expire() {
        adjustableUtcClock.advance(Duration.ofMinutes(31));
    }

    /** 本例的 Payment 查询响应。 */
    GetSubmissionResponseBody submission(String status) {
        return PaymentSubmissionMocker.submission(orderId(), SUBMISSION_ID, ATTEMPT_ID, status);
    }

    /** 本例原订单身份。 */
    long orderId() {
        return fixture.getOrder().getOrderId();
    }

    /** 通过 HTTP 查询本例提交。 */
    CheckoutFlowResponseChecker query() {
        var headers = new HttpHeaders();
        headers.set(ApiHeaderNames.REQUEST_ID, REQUEST_ID);
        return new CheckoutFlowResponseChecker(httpTestInvoker.get("/customer-gateway-api/v1/checkouts/"
            + fixture.getSession().getPublicToken() + "/payment-submissions/" + SUBMISSION_ID, headers), objectMapper,
            REQUEST_ID);
    }
}
