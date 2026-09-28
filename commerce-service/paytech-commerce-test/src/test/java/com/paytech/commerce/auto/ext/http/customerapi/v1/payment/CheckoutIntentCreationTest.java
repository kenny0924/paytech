package com.paytech.commerce.auto.ext.http.customerapi.v1.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.paytech.commerce.customer.api.v1.checkout.CustomerCheckoutApi;
import com.paytech.commerce.customer.api.v1.payment.CustomerPaymentApi;
import com.paytech.commerce.customer.api.v1.payment.request.SubmitPaymentRequestBody;
import com.paytech.commerce.domain.checkout.CreateCheckoutContext;
import com.paytech.commerce.test.builder.customer.PaymentResponseBuilder;
import com.paytech.commerce.test.mocker.external.CardInstrumentMocker;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.commerce.integration.payment.PaymentTerminationClient;
import com.paytech.commerce.test.base.MockTestBase;
import com.paytech.commerce.test.builder.customer.SubmitPaymentRequestBuilder;
import com.paytech.commerce.test.checker.customer.CheckoutFlowResponseChecker;
import com.paytech.commerce.test.checker.customer.CheckoutPaymentChecker;
import com.paytech.commerce.test.mocker.external.CheckoutCardMocker;
import com.paytech.commerce.test.mocker.external.CheckoutIntentMocker;
import com.paytech.commerce.test.mocker.external.PaymentTerminationMocker;
import com.paytech.commerce.test.mocker.local.CheckoutPaymentDataMocker;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

/**
 * 创建 Checkout 与首次支付创建 Intent 的边界，通过真实 HTTP 和本地 MySQL 检查。
 * @see CustomerPaymentApi#submitPayment(String, SubmitPaymentRequestBody)
 * @see CustomerCheckoutApi#getCheckout(String, boolean)
 */
class CheckoutIntentCreationTest extends MockTestBase {
    @Autowired
    CheckoutPaymentDataMocker data;
    @Autowired
    CheckoutIntentMocker intents;
    @Autowired
    PaymentTerminationMocker terminations;
    @Autowired
    CheckoutPaymentChecker checker;
    @Autowired
    CheckoutCardMocker cards;
    @Autowired
    org.redisson.api.RedissonClient redisson;
    @Autowired
    com.paytech.commerce.test.checkout.PaymentTransactionProbe paymentTransactions;
    @Autowired
    CardInstrumentMocker instruments;
    private CreateCheckoutContext fixture;

    /** 每例准备订单与明确的外部响应。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = data.mockPrepared(true);
        terminations.mockResult(orderId(), new PaymentTerminationClient.Result(orderId(), PaymentTerminationClient.Status.TERMINATED));
        mockIntent();
        instruments.mockResponse(PaymentResponseBuilder.builder().expiresAt(adjustableUtcClock.instant().plusSeconds(60)).build().instrument());
        cards.mockResponse(
                new com.paytech.commerce.domain.customer.CheckoutSubmissionModels.PaymentResult(
                        0,
                        0,
                        0,
                        "123456",
                        "PROCESSING",
                        "4242",
                        null,
                        null,
                        adjustableUtcClock.instant()));
    }

    /** 创建响应只返回提供方定义的 ID 和状态。 */
    void mockIntent() {
        intents.mockResult(PaymentResponseBuilder.builder().intentId(Long.toString(orderId())).build().intent());
    }

    /** 验证 getReadyWithoutIntent 场景的支付边界。 */
    @Test
    void test_getReadyWithoutIntent() {
        getCheckout().assertReady(true);
        checker.assertUnstarted(orderId());
        assertTrue(intents.calls().isEmpty());
    }

    /** 验证 firstPaymentCreatesIntentAfterAcceptanceCommitted 场景的支付边界。 */
    @Test
    void test_firstPaymentCreatesIntentAfterAcceptanceCommitted() {
        intents.observe(request -> {
            checker.assertAccepted(orderId(), false);
            assertTransportHeaders();
        });
        instruments.observe(request -> {
            checker.assertAccepted(orderId(), true);
            assertTransportHeaders();
            assertEquals(Long.toString(fixture.getOrder().getClientId()), request.getClientId());
            assertEquals(Long.toString(orderId()), request.getIntentId());
            assertEquals("CARD_TEST", request.getPaymentMethodCode());
            var expectedCard = SubmitPaymentRequestBuilder.forContract(orderId()).getCard();
            var card = request.getCard();
            assertEquals(expectedCard.getPan(), card.getPan());
            assertEquals(expectedCard.getCvv(), card.getCvv());
            assertEquals(expectedCard.getExpiryMonth(), card.getExpiryMonth());
            assertEquals(expectedCard.getExpiryYear(), card.getExpiryYear());
            assertEquals(expectedCard.getCardholderName(), card.getCardholderName());
        });
        cards.observe(request -> {
            assertEquals("t".repeat(43), request.instrument().token());
            assertEquals("CARD_TEST", request.instrument().paymentMethodCode());
            assertEquals(Long.toString(request.receipt().submissionId()), instruments.calls().getFirst().sourceSubmissionId());
        });
        submit().assertProcessing();
        checker.assertAccepted(orderId(), true);
        assertEquals(1, intents.calls().size());
        var input = intents.calls().getFirst();
        assertAll(
                () -> assertEquals("COMMERCE_ORDER", input.getSourceType()),
                () -> assertEquals(Long.toString(orderId()), input.getSourceId()),
                () -> assertEquals("AE001", input.getEntityNo()),
                () -> assertEquals("1.00", input.getAmount()),
                () -> assertEquals(fixture.getOrder().getExpiresAt().toString(), input.getExpiresAt()),
                () -> assertEquals("104", input.getClientPricingVersionId()),
                () -> assertEquals(Long.toString(fixture.getOrder().getClientId()), input.getClientId()),
                () -> assertEquals("USD", input.getCurrency()),
                () -> assertEquals("RETAIL", input.getIndustryCode()),
                () -> assertEquals("5999", input.getMccCode()),
                () -> assertEquals("US", input.getConsumerCountryCode()),
                () -> assertNull(input.getPartnerShareVersionId()),
                () -> assertNull(input.getPlatformChargeVersionId()));
        assertEquals(List.of(false, false), paymentTransactions.transactionStates(), "Intent 和卡工具必须经过真实 HTTP 客户端且在事务外执行");
        assertEquals(List.of(false), cards.transactions());
    }

    /** 验证 secondPaymentReusesIntentAndAcceptances 场景的支付边界。 */
    @Test
    void test_secondPaymentReusesIntentAndAcceptances() {
        submit().assertProcessing();
        submit().assertProcessing();
        checker.assertAccepted(orderId(), true);
        assertEquals(1, intents.calls().size());
        assertEquals(2, instruments.calls().size());
        assertNotEquals(instruments.calls().getFirst().sourceSubmissionId(), instruments.calls().getLast().sourceSubmissionId());
        assertEquals(instruments.calls().getFirst().intentId(), instruments.calls().getLast().intentId());
    }

    /** 验证 createTimeoutRetryUsesSameOrderIdentity 场景的支付边界。 */
    @Test
    void test_createTimeoutRetryUsesSameOrderIdentity() {
        intents.mockFailure(MockHttpFault.disconnect());
        submit().assertProblem(503, "SERVICE_UNAVAILABLE");
        checker.assertAccepted(orderId(), false);
        assertTrue(cards.calls().isEmpty());
        mockIntent();
        submit().assertProcessing();
        checker.assertAccepted(orderId(), true);
        assertEquals(2, intents.calls().size());
        var first = intents.calls().getFirst();
        var second = intents.calls().getLast();
        assertEquals(objectMapper.valueToTree(first), objectMapper.valueToTree(second));
    }

    /** 验证 cancelPreparedWithoutIntent 场景的支付边界。 */
    @Test
    void test_cancelPreparedWithoutIntent() {
        cancel().assertStatus(200);
        assertTrue(intents.calls().isEmpty());
        checker.assertUnstarted(orderId());
    }

    /** 创建响应丢失后取消只调用终止端口，不再次创建或补写关联。 */
    /** 验证 cancelAfterCreateTimeoutDoesNotCreateIntent 场景的支付边界。 */
    @Test
    void test_cancelAfterCreateTimeoutDoesNotCreateIntent() {
        intents.mockFailure(MockHttpFault.disconnect());
        submit().assertProblem(503, "SERVICE_UNAVAILABLE");
        cancel().assertStatus(200);
        checker.assertAccepted(orderId(), false);
        assertEquals(1, intents.calls().size());
        submit().assertProblem(409, "CHECKOUT_CLOSED");
    }

    /** 不存在结果先完成取消后，晚到创建响应的本地绑定必须回滚，不能扣款。 */
    /** 验证 cancelWhileCreatingIntentPreventsCardSubmission 场景的支付边界。 */
    @Test
    void test_cancelWhileCreatingIntentPreventsCardSubmission() {
        intents.observe(
                request -> {
                    intents.observe(ignored -> {});
                    terminations.mockFailure(orderId(), new FailException(CommonErrorCode.NOT_FOUND, "支付意图不存在"));
                    cancel().assertStatus(200);
                });
        submit().assertProblem(409, "CONCURRENT_REQUEST");
        checker.assertAccepted(orderId(), false);
        assertEquals(1, intents.calls().size());
        assertTrue(cards.calls().isEmpty());
    }

    /** 终止超时保留待定，重提沿原订单取消，不重试创建。 */
    /** 验证 cancelRetriesWhenIntentOwnerUnavailable 场景的支付边界。 */
    @Test
    void test_cancelRetriesWhenIntentOwnerUnavailable() {
        intents.mockFailure(MockHttpFault.disconnect());
        submit().assertProblem(503, "SERVICE_UNAVAILABLE");
        terminations.mockFailure(orderId(), new ServiceUnavailableException(new java.net.SocketTimeoutException("termination timeout")));
        cancel().assertStatus(202);
        checker.assertAccepted(orderId(), false);
        assertTrue(cards.calls().isEmpty());
        terminations.mockResult(orderId(), new PaymentTerminationClient.Result(orderId(), PaymentTerminationClient.Status.TERMINATED));
        cancel().assertStatus(200);
        checker.assertAccepted(orderId(), false);
        assertEquals(1, intents.calls().size());
        assertEquals(2, terminations.calls().size());
    }

    /** 验证 preparationEndpointRemoved 场景的支付边界。 */
    @Test
    void test_preparationEndpointRemoved() {
        new CheckoutFlowResponseChecker(
                        httpTestInvoker.post(path() + "/preparation", headers(), null),
                        objectMapper,
                        REQUEST_ID)
                .assertStatus(404);
        checker.assertUnstarted(orderId());
        assertTrue(intents.calls().isEmpty());
    }

    /** 验证 noConsumerStillReadyButCannotPay 场景的支付边界。 */
    @Test
    void test_noConsumerStillReadyButCannotPay() {
        fixture = data.mockPrepared(false);
        getCheckout().assertReady(false);
        submit().assertProblem(409, "CHECKOUT_NOT_READY");
        checker.assertUnstarted(orderId());
        assertTrue(intents.calls().isEmpty());
    }

    /** 相同订单的锁竞争在法律接受、Intent 和卡提交前拒绝，释放后可以正常处理。 */
    /** 验证 sameOrderLockConflictDoesNotWriteOrCallPayment 场景的支付边界。 */
    @Test
    void test_sameOrderLockConflictDoesNotWriteOrCallPayment() {
        var lock = redisson.getSpinLock("lock:order:" + orderId());
        lock.lock();
        try {
            submit().assertProblem(409, "CONCURRENT_REQUEST");
            checker.assertUnstarted(orderId());
            assertTrue(intents.calls().isEmpty());
            assertTrue(cards.calls().isEmpty());
        } finally {
            lock.unlock();
        }
        submit().assertProcessing();
        assertFalse(lock.isLocked());
    }

    /** 另一个订单持锁不影响当前订单，不能按 Client 或全局串行。 */
    /** 验证 otherOrderLockDoesNotBlockPayment 场景的支付边界。 */
    @Test
    void test_otherOrderLockDoesNotBlockPayment() {
        var lock = redisson.getSpinLock("lock:order:" + (orderId() + 1));
        lock.lock();
        try {
            submit().assertProcessing();
            checker.assertAccepted(orderId(), true);
        } finally {
            lock.unlock();
        }
    }

    /** Intent 外呼期间仍持有资源锁；并发点击不会重复创建 Intent 或再次提交卡。 */
    /** 验证 concurrentPaymentRejectedWhileCreatingIntent 场景的支付边界。 */
    @Test
    void test_concurrentPaymentRejectedWhileCreatingIntent() {
        intents.observe(request -> submit().assertProblem(409, "CONCURRENT_REQUEST"));
        submit().assertProcessing();
        assertEquals(1, intents.calls().size());
        assertEquals(1, cards.calls().size());
        assertFalse(redisson.getSpinLock("lock:order:" + orderId()).isLocked());
    }

    /** Intent 返回后发生版本竞争时，绑定和资格更新必须一起回滚。 */
    /** 验证 orderConflictRollsBackIntentBinding 场景的支付边界。 */
    @Test
    void test_orderConflictRollsBackIntentBinding() {
        intents.observe(request -> data.changeVersion(orderId()));
        submit().assertProblem(409, "CONCURRENT_REQUEST");
        checker.assertAccepted(orderId(), false);
        assertTrue(cards.calls().isEmpty());

        intents.observe(request -> {});
        submit().assertProcessing();
        checker.assertAccepted(orderId(), true);
        assertEquals(2, intents.calls().size());
    }

    /** 验证 wrongIntentResponseIsNotBound 场景的支付边界。 */
    @Test
    void test_wrongIntentResponseIsNotBound() {
        intents.mockResult(PaymentResponseBuilder.builder().intentId("9223372036854775808").build().intent());
        submit().assertProblem(502, "UPSTREAM_PROTOCOL_ERROR");
        checker.assertAccepted(orderId(), false);
    }

    /** 验证 cardTimeoutReturnsSubmissionForQuery 场景的支付边界。 */
    @Test
    void test_cardTimeoutReturnsSubmissionForQuery() {
        cards.mockFailure(
                new ServiceUnavailableException(
                        new java.net.SocketTimeoutException("mock card response lost")));
        submit().assertProcessing();
        checker.assertAccepted(orderId(), true);
        assertEquals(1, cards.calls().size());
    }

    /** 原 Intent 返回终态时，不绑定未支付投影，也不申请工具。 */
    @ParameterizedTest
    @CsvSource({"SUCCEEDED,409,CHECKOUT_CLOSED", "CANCELLED,409,CHECKOUT_CLOSED", "EXPIRED,410,CHECKOUT_EXPIRED"})
    void test_terminalIntentStopsPayment(String status, int httpStatus, String code) {
        intents.mockResult(PaymentResponseBuilder.builder().intentId(Long.toString(orderId())).status(status).build().intent());
        submit().assertProblem(httpStatus, code);
        checker.assertAccepted(orderId(), false);
        assertTrue(instruments.calls().isEmpty());
        assertTrue(cards.calls().isEmpty());
    }

    /** 不完整或未知的创建结果不能绑定本地订单。 */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"UNKNOWN", "PROCESSING"})
    void test_invalidIntentStatus(String status) {
        intents.mockResult(PaymentResponseBuilder.builder().status(status).build().intent());
        submit().assertProblem(502, "UPSTREAM_PROTOCOL_ERROR");
        checker.assertAccepted(orderId(), false);
        assertTrue(instruments.calls().isEmpty());
    }

    /** 非法 ID 不能进入本地关联；溢出另由单独场景验证。 */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "-1", "01", "abc"})
    void test_invalidIntentId(String id) {
        intents.mockResult(PaymentResponseBuilder.builder().intentId(id).build().intent());
        submit().assertProblem(502, "UPSTREAM_PROTOCOL_ERROR");
        checker.assertAccepted(orderId(), false);
        assertTrue(instruments.calls().isEmpty());
    }

    /** 工具申请断连不进入 pay；重提复用已绑定 Intent，用新的提交申请工具。 */
    /** 验证 instrumentDisconnectKeepsIntentAndDoesNotPay 场景的支付边界。 */
    @Test
    void test_instrumentDisconnectKeepsIntentAndDoesNotPay() {
        instruments.mockFailure(MockHttpFault.disconnect());
        submit().assertProblem(503, "SERVICE_UNAVAILABLE");
        checker.assertAccepted(orderId(), true);
        assertTrue(cards.calls().isEmpty());
        instruments.mockResponse(PaymentResponseBuilder.builder().expiresAt(adjustableUtcClock.instant().plusSeconds(60)).build().instrument());
        submit().assertProcessing();
        assertEquals(1, intents.calls().size());
        assertEquals(2, instruments.calls().size());
        assertNotEquals(instruments.calls().getFirst().sourceSubmissionId(), instruments.calls().getLast().sourceSubmissionId());
        assertEquals(1, cards.calls().size());
    }

    /** 工具 Token 必须符合正式合同，不能以空值继续 pay。 */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid token", "short"})
    void test_invalidInstrumentToken(String token) {
        instruments.mockResponse(PaymentResponseBuilder.builder().token(token).expiresAt(adjustableUtcClock.instant().plusSeconds(60)).build().instrument());
        submit().assertProblem(502, "UPSTREAM_PROTOCOL_ERROR");
        checker.assertAccepted(orderId(), true);
        assertTrue(cards.calls().isEmpty());
    }

    /** 工具期限必须完整、仍有效且不得越过订单期限。 */
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 3600})
    void test_invalidInstrumentExpiry(int seconds) {
        var expiry = seconds == 3600 ? fixture.getOrder().getExpiresAt().plusSeconds(1)
            : adjustableUtcClock.instant().plusSeconds(seconds);
        instruments.mockResponse(PaymentResponseBuilder.builder().expiresAt(expiry).build().instrument());
        submit().assertProblem(502, "UPSTREAM_PROTOCOL_ERROR");
        assertTrue(cards.calls().isEmpty());
    }

    /** 网络等待期间到期时，按原订单期限阻止后续支付。 */
    /** 验证 orderExpiresWhileApplyingInstrument 场景的支付边界。 */
    @Test
    void test_orderExpiresWhileApplyingInstrument() {
        instruments.observe(request -> adjustableUtcClock.advance(java.time.Duration.ofHours(1)));
        submit().assertProblem(410, "CHECKOUT_EXPIRED");
        assertTrue(cards.calls().isEmpty());
    }

    /** 提供方业务拒绝、并发冲突和不可用复用公共 Problem 解码，不继续 pay。 */
    @ParameterizedTest
    @CsvSource({"400,BUSINESS_CHECK_FAILED", "409,CONCURRENT_REQUEST", "503,SERVICE_UNAVAILABLE"})
    void test_instrumentProblem(int status, String code) {
        var problem = new ApiProblemResponse(ApiProblemProfile.TYPE, HttpStatus.valueOf(status).getReasonPhrase(),
            "合成故障", code, "payment-test", "2026-09-08T00:00:00.000000Z", null);
        instruments.mockFailure(MockHttpFault.json(status, problem));
        submit().assertProblem(status, code);
        assertEquals(1, instruments.calls().size());
        assertTrue(cards.calls().isEmpty());
    }

    /** 畸形 JSON 交给公共客户端判定，不能自动重试命令。 */
    /** 验证 malformedIntentResponse 场景的支付边界。 */
    @Test
    void test_malformedIntentResponse() {
        intents.mockFailure(MockHttpFault.raw(200, "{broken"));
        submit().assertProblem(502, "UPSTREAM_INVALID_RESPONSE");
        checker.assertAccepted(orderId(), false);
        assertEquals(1, intents.calls().size());
        assertTrue(instruments.calls().isEmpty());
    }

    /** 在独立远端线程检查公共 Request ID 传播和未引入 HTTP 幂等键。 */
    void assertTransportHeaders() {
        var attributes = (org.springframework.web.context.request.ServletRequestAttributes)
            org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
        assertEquals(REQUEST_ID, attributes.getRequest().getHeader(ApiHeaderNames.REQUEST_ID));
        assertNull(attributes.getRequest().getHeader(ApiHeaderNames.IDEMPOTENCY_KEY));
    }

    /** 本例原订单身份。 */
    long orderId() {
        return fixture.getOrder().getOrderId();
    }

    /** 公开 Checkout 支付入口。 */
    String path() {
        return "/customer-gateway-api/v1/checkouts/" + fixture.getSession().getPublicToken();
    }

    /** 构造当前请求的追踪头。 */
    HttpHeaders headers() {
        var h = new HttpHeaders();
        h.set(ApiHeaderNames.REQUEST_ID, REQUEST_ID);
        return h;
    }

    /** 通过 HTTP 加载 Checkout。 */
    CheckoutFlowResponseChecker getCheckout() {
        return new CheckoutFlowResponseChecker(
                httpTestInvoker.get(path(), headers()), objectMapper, REQUEST_ID);
    }

    /** 通过 HTTP 提交本例合成卡。 */
    CheckoutFlowResponseChecker submit() {
        return new CheckoutFlowResponseChecker(
                httpTestInvoker.post(
                        path() + "/payments",
                        headers(),
                        SubmitPaymentRequestBuilder.forContract(orderId())),
                objectMapper,
                REQUEST_ID);
    }

    /** 通过 Client HTTP 取消原订单。 */
    CheckoutFlowResponseChecker cancel() {
        var h =
                TestRequestHeaders.client(
                        REQUEST_ID, Long.toString(fixture.getOrder().getClientId()), "1");
        h.set(ApiHeaderNames.IDEMPOTENCY_KEY, "gateway-placeholder");
        return new CheckoutFlowResponseChecker(
                httpTestInvoker.post(
                        "/client-gateway-api/v1/orders/"
                                + fixture.getOrder().getOrderNo()
                                + "/cancel",
                        h,
                        null),
                objectMapper,
                REQUEST_ID);
    }
}
