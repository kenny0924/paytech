package com.paytech.acquiring.auto.ext.http.clientapi.v1.order.cancel;

import static com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.NOW;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;

import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.acquiring.api.client.v1.order.ClientOrderApi;
import com.paytech.acquiring.domain.enums.CheckoutSessionStatus;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.acquiring.domain.enums.PaymentIntentStatus;
import com.paytech.acquiring.integration.payment.PaymentTerminationClient.Status;
import com.paytech.acquiring.integration.payment.PaymentTerminationClient.TerminationReason;
import com.paytech.acquiring.test.base.MockTestBase;
import com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.Fixture;
import com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.Payment;
import com.paytech.acquiring.test.checker.order.CancelOrderResponseChecker;
import com.paytech.acquiring.test.checker.order.OrderCancellationChecker;
import com.paytech.acquiring.test.checker.refund.RefundQueryChecker;
import com.paytech.acquiring.test.mocker.external.CheckoutIntentMocker;
import com.paytech.acquiring.test.checkout.PaymentTransactionProbe;
import com.paytech.acquiring.test.mocker.external.PaymentTerminationMocker;
import com.paytech.acquiring.test.mocker.external.PaymentTerminationMocker.SourceQuery;
import com.paytech.acquiring.test.mocker.external.PaymentTerminationMocker.Termination;
import com.paytech.acquiring.test.mocker.local.CheckoutPaymentDataMocker;
import com.paytech.acquiring.test.mocker.local.OrderCancellationDataMocker;
import com.paytech.acquiring.test.mocker.local.OrderQueryDataMocker;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

/**
 * 未支付订单取消、原取消事实重用、Client 隔离和支付创建中断后的取消。
 * Payment 终止链路经真实 HTTP 客户端访问 Mock 接口：有投影直接终止，无投影先按来源查询；只有 404 视为 Intent 不存在。
 *
 * @see ClientOrderApi#cancelOrder(long, long, String, String)
 */
class CancelOrderTest extends MockTestBase {
    private static final String REQUEST_ID = "Commerce.CancelOrder.Test";

    @Autowired
    private RefundQueryChecker snapshots;
    @Autowired
    private CheckoutIntentMocker intents;
    @Autowired
    private OrderQueryDataMocker orders;
    @Autowired
    private PaymentTerminationMocker terminations;
    @Autowired
    private OrderCancellationChecker checker;
    @Autowired
    private CheckoutPaymentDataMocker paymentData;
    @Autowired
    private OrderCancellationDataMocker data;
    @Autowired
    private PaymentTransactionProbe paymentTransactions;

    private Fixture fixture;

    /** 已初始化但尚未受理支付的新订单。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = orders.template().ready().build();
    }

    /** 新旧 Checkout 及无会话 Direct Pay 均按实际本地关联一起取消。 */
    @ParameterizedTest
    @CsvSource({"true,true,true", "true,true,false", "false,false,true", "false,true,true"})
    void test_cancelWithoutStartingPayment(boolean hosted, boolean hasSession, boolean ready) {
        var builder = orders.template().session(hasSession);
        if (!hosted) {
            builder.directPay().session(hasSession);
        }
        if (ready) {
            builder.ready();
        }
        fixture = builder.build();
        data.mock(fixture);

        invoke().assertCancelled(orderNo(), hasSession, NOW);

        checker.assertCancelled(orderId(), hasSession, NOW);
        assertAll(() -> assertEquals(9, checker.order(orderId()).getVersion()),
            () -> assertEquals(NOW, checker.order(orderId()).getTerminationRequestedAt()),
            () -> assertNull(checker.payment(orderId())));
        if (hasSession) {
            assertEquals(9, checker.session(orderId()).getVersion());
        }
        terminations.assertNoCalls();
        assertTrue(intents.calls().isEmpty());
    }

    /** 重提取消保留第一次时刻与版本，不再执行下游。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_repeatCancellation(boolean hasSession) {
        fixture = orders.template().directPay().session(hasSession).ready().build();
        data.mock(fixture);
        invoke().assertCancelled(orderNo(), hasSession, NOW);
        var before = snapshots.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        adjustableUtcClock.advance(Duration.ofHours(1));

        invoke().assertCancelled(orderNo(), hasSession, NOW);

        snapshots.assertUnchanged(before, checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        terminations.assertNoCalls();
        assertTrue(intents.calls().isEmpty());
    }

    /** 已有 Intent 时按投影 Intent 直接终止（不按来源查询），调用在事务外，成功后本地取消。 */
    @ParameterizedTest
    @EnumSource(value = PaymentIntentStatus.class, names = {"REQUIRES_PAYMENT", "PROCESSING", "CANCELLED"})
    void test_cancelWithExistingIntent(PaymentIntentStatus status) {
        fixture.setPayment(Payment.builder().intentStatus(status).build());
        data.mock(fixture);
        terminations.mockDecision(orderId(), Status.TERMINATED);
        var paymentBefore = objectMapper.valueToTree(checker.payment(orderId()));

        invoke().assertCancelled(orderNo(), true, NOW);

        checker.assertCancelled(orderId(), true, NOW);
        assertEquals(paymentBefore, objectMapper.valueToTree(checker.payment(orderId())), "取消不能伪造 Payment 投影事件");
        assertEquals(List.of(Termination.of(orderId(), clientId(), TerminationReason.ORDER_CANCELLED)),
            terminations.calls());
        assertTrue(terminations.sourceQueries().isEmpty(), "已有投影不能再按来源查询");
        assertTrue(intents.calls().isEmpty());
        invoke().assertCancelled(orderNo(), true, NOW);
        assertEquals(1, terminations.calls().size(), "终态重提不能再次终止 Intent");
    }

    /** 已接受法律文件但尚无支付关联（Intent 响应可能丢失）时保持待定，重提不重复更新终止时间。 */
    @Test
    void test_pendingAfterAcceptanceWithoutPayment() {
        var accepted = paymentData.mockPrepared(false);
        long id = accepted.getOrder().getOrderId();
        String no = accepted.getOrder().getOrderNo();
        data.mockAcceptances(id, NOW);
        long version = checker.order(id).getVersion();
        terminations.mockSource(id, intentIdOf(id));
        terminations.mockDecision(intentIdOf(id), Status.PENDING);

        invoke(no).assertPending(no);
        var before = snapshots.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        adjustableUtcClock.advance(Duration.ofSeconds(10));
        invoke(no).assertPending(no);

        snapshots.assertUnchanged(before, checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        assertAll(() -> assertEquals(OrderStatus.OPEN, checker.order(id).getOrderStatus()),
            () -> assertEquals(NOW, checker.order(id).getTerminationRequestedAt()),
            () -> assertEquals(version + 1, checker.order(id).getVersion()),
            () -> assertNull(checker.order(id).getCancelledAt()),
            () -> assertEquals(CheckoutSessionStatus.OPEN, checker.session(id).getSessionStatus()));
        assertTrue(intents.calls().isEmpty());
        assertEquals(List.of(SourceQuery.of(id, clientId()), SourceQuery.of(id, clientId())),
            terminations.sourceQueries());
        assertEquals(List.of(Termination.of(intentIdOf(id), clientId(), TerminationReason.ORDER_CANCELLED),
            Termination.of(intentIdOf(id), clientId(), TerminationReason.ORDER_CANCELLED)), terminations.calls());
    }

    /** 取消仅定位并终止；Payment 503 保留原终止意图，重提成功也不创建或绑定 Intent。 */
    @Test
    void test_retryAfterTerminationUnavailable() {
        var accepted = paymentData.mockPrepared(false);
        long id = accepted.getOrder().getOrderId();
        String no = accepted.getOrder().getOrderNo();
        data.mockAcceptances(id, NOW);
        terminations.mockSource(id, intentIdOf(id));
        terminations.mockTerminationFault(intentIdOf(id), problem(HttpStatus.SERVICE_UNAVAILABLE, CommonErrorCode.SERVICE_UNAVAILABLE));

        invoke(no).assertPending(no);
        assertAll(() -> assertEquals(OrderStatus.OPEN, checker.order(id).getOrderStatus()),
            () -> assertEquals(NOW, checker.order(id).getTerminationRequestedAt()),
            () -> assertNull(checker.payment(id)), () -> assertNull(checker.order(id).getCancelledAt()));
        terminations.mockDecision(intentIdOf(id), Status.TERMINATED);
        adjustableUtcClock.advance(Duration.ofSeconds(5));

        invoke(no).assertCancelled(no, true, NOW.plusSeconds(5));

        checker.assertCancelled(id, true, NOW.plusSeconds(5));
        assertEquals(NOW, checker.order(id).getTerminationRequestedAt());
        assertEquals(3, checker.order(id).getVersion());
        assertNull(checker.payment(id));
        assertTrue(intents.calls().isEmpty(), "取消不能创建 Intent");
        var expected = Termination.of(intentIdOf(id), clientId(), TerminationReason.ORDER_CANCELLED);
        assertEquals(List.of(expected, expected), terminations.calls());
        assertEquals(2, terminations.sourceQueries().size());
    }

    /** 无投影时按来源查询 404：Intent 不存在，完成本地取消且不终止；重复取消不重新外呼。 */
    @Test
    void test_sourceNotFoundCancelsWithoutCreatingIntent() {
        var accepted = paymentData.mockPrepared(false);
        long id = accepted.getOrder().getOrderId();
        String no = accepted.getOrder().getOrderNo();
        data.mockAcceptances(id, NOW);
        terminations.mockSourceNotFound(id);

        invoke(no).assertCancelled(no, true, NOW);
        invoke(no).assertCancelled(no, true, NOW);

        checker.assertCancelled(id, true, NOW);
        assertNull(checker.payment(id));
        assertTrue(intents.calls().isEmpty());
        assertEquals(List.of(SourceQuery.of(id, clientId())), terminations.sourceQueries());
        assertTrue(terminations.calls().isEmpty(), "来源不存在时不能终止");
    }

    /** 无投影但有接受证据：先按来源定位 Intent，再以取消原因终止，确认后本地取消。 */
    @Test
    void test_sourceFoundTerminatesLocatedIntent() {
        var accepted = paymentData.mockPrepared(false);
        long id = accepted.getOrder().getOrderId();
        String no = accepted.getOrder().getOrderNo();
        data.mockAcceptances(id, NOW);
        terminations.mockSource(id, intentIdOf(id));
        terminations.mockDecision(intentIdOf(id), Status.TERMINATED);

        invoke(no).assertCancelled(no, true, NOW);

        checker.assertCancelled(id, true, NOW);
        assertNull(checker.payment(id), "取消不能补写支付投影");
        assertTrue(intents.calls().isEmpty());
        assertEquals(List.of(SourceQuery.of(id, clientId())), terminations.sourceQueries());
        assertEquals(List.of(Termination.of(intentIdOf(id), clientId(), TerminationReason.ORDER_CANCELLED)),
            terminations.calls());
    }

    /** 有投影时终止返回 404：Intent 不存在，完成本地取消。 */
    @Test
    void test_terminationNotFoundCancels() {
        fixture.setPayment(Payment.builder().build());
        data.mock(fixture);
        terminations.mockTerminationNotFound(orderId());

        invoke().assertCancelled(orderNo(), true, NOW);

        checker.assertCancelled(orderId(), true, NOW);
        assertEquals(1, terminations.calls().size());
        assertTrue(terminations.sourceQueries().isEmpty());
    }

    /** Payment 此前已终止（ALREADY_TERMINATED）与本次终止同样完成本地取消。 */
    @Test
    void test_alreadyTerminatedCancels() {
        fixture.setPayment(Payment.builder().build());
        data.mock(fixture);
        terminations.mockDecision(orderId(), PaymentTerminationMocker.ALREADY_TERMINATED);

        invoke().assertCancelled(orderNo(), true, NOW);

        checker.assertCancelled(orderId(), true, NOW);
        assertEquals(1, terminations.calls().size());
    }

    /** 非 404 错误（如跨 Client 的 400）不能被当作不存在；已保存终止意图不回滚，订单尚未取消。 */
    @Test
    void test_otherBusinessErrorDoesNotCancel() {
        fixture.setPayment(Payment.builder().build());
        data.mock(fixture);
        terminations.mockTerminationFault(orderId(), problem(HttpStatus.BAD_REQUEST, CommonErrorCode.BUSINESS_CHECK_FAILED));

        invoke().assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode());

        assertEquals(OrderStatus.OPEN, checker.order(orderId()).getOrderStatus());
        assertNotNull(checker.order(orderId()).getTerminationRequestedAt());
        assertNull(checker.order(orderId()).getCancelledAt());
        assertTrue(intents.calls().isEmpty());
    }

    /** 未决返回 202，需要撤销或 Payment 已收款返回 409 REVERSAL_REQUIRED，均不关闭本地订单。 */
    @ParameterizedTest
    @EnumSource(value = Status.class, names = {"PENDING", "REVERSAL_REQUIRED", "ALREADY_PAID"})
    void test_terminationNotCompleted(Status status) {
        fixture.setPayment(Payment.builder().build());
        data.mock(fixture);
        terminations.mockDecision(orderId(), status);

        var response = invoke();
        if (status == Status.PENDING) {
            response.assertPending(orderNo());
        } else {
            response.assertProblem(409, CommerceCheckoutErrorCode.REVERSAL_REQUIRED.getCode());
        }
        assertEquals(OrderStatus.OPEN, checker.order(orderId()).getOrderStatus());
        assertNull(checker.order(orderId()).getCancelledAt());
        assertTrue(intents.calls().isEmpty());
    }

    /** 终止响应 Intent 错归属或决定未知时返回协议错误，不能据此关闭原订单。 */
    @ParameterizedTest
    @ValueSource(strings = {"foreign-intent", "unknown-decision"})
    void test_terminationRejectsInvalidResponse(String fault) {
        fixture.setPayment(Payment.builder().build());
        data.mock(fixture);
        if (fault.equals("foreign-intent")) {
            terminations.mockTermination(Long.toString(orderId()),
                PaymentTerminationMocker.termination(Long.toString(orderId() + 1), Status.TERMINATED.getCode()));
        } else {
            terminations.mockDecision(orderId(), "UNKNOWN");
        }

        invoke().assertProblem(502, CommerceCheckoutErrorCode.UPSTREAM_PROTOCOL_ERROR.getCode());

        assertEquals(OrderStatus.OPEN, checker.order(orderId()).getOrderStatus());
        assertNull(checker.order(orderId()).getCancelledAt());
        assertTrue(intents.calls().isEmpty());
    }

    /** 本地成功支付的任一种证据都要求撤销或退款，不能调用终止后强行取消。 */
    @ParameterizedTest
    @ValueSource(strings = {"order-status", "paid-at", "successful-attempt", "captured", "intent-succeeded"})
    void test_successfulPaymentRequiresReversal(String fact) {
        var payment = Payment.builder();
        switch (fact) {
            case "order-status" -> fixture.getOrder().setOrderStatus(OrderStatus.PAID);
            case "paid-at" -> fixture.getOrder().setPaidAt(NOW);
            case "successful-attempt" -> payment.successfulAttemptId(orderId());
            case "captured" -> payment.capturedAmount(new BigDecimal("0.01"));
            case "intent-succeeded" -> payment.intentStatus(PaymentIntentStatus.SUCCEEDED);
            default -> throw new AssertionError(fact);
        }
        fixture.setPayment(payment.build());
        data.mock(fixture);
        assertRejected(409, CommerceCheckoutErrorCode.REVERSAL_REQUIRED.getCode());
    }

    /** Order 或 Session 的过期终态不能被取消覆盖。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_expiredState(boolean orderExpired) {
        if (orderExpired) {
            fixture.getOrder().setOrderStatus(OrderStatus.EXPIRED);
        } else {
            fixture.getSession().setSessionStatus(CheckoutSessionStatus.EXPIRED);
        }
        data.mock(fixture);
        assertRejected(409, CommerceCheckoutErrorCode.ORDER_NOT_CANCELLABLE.getCode());
    }

    /** Hosted 缺会话、取消终态不完整等损坏事实返回系统错误且不推进业务。 */
    @ParameterizedTest
    @ValueSource(strings = {"missing-session", "completed-session", "cancelled-session", "missing-cancel-time",
        "cancelled-with-paid-time", "cancelled-with-open-session", "missing-session-cancel-time"})
    void test_inconsistentFacts(String fact) {
        if (fact.startsWith("missing-cancel") || fact.startsWith("cancelled-with")
            || fact.equals("missing-session-cancel-time")) {
            fixture.getOrder().setOrderStatus(OrderStatus.CANCELLED);
            fixture.getOrder().setCancelledAt(NOW.minusSeconds(1));
            fixture.getSession().setSessionStatus(CheckoutSessionStatus.CANCELLED);
            fixture.getSession().setCancelledAt(NOW.minusSeconds(1));
        }
        switch (fact) {
            case "missing-session" -> fixture.setSession(null);
            case "completed-session" -> fixture.getSession().setSessionStatus(CheckoutSessionStatus.COMPLETED);
            case "cancelled-session" -> fixture.getSession().setSessionStatus(CheckoutSessionStatus.CANCELLED);
            case "missing-cancel-time" -> fixture.getOrder().setCancelledAt(null);
            case "cancelled-with-paid-time" -> fixture.getOrder().setPaidAt(NOW);
            case "cancelled-with-open-session" -> fixture.getSession().setSessionStatus(CheckoutSessionStatus.OPEN);
            case "missing-session-cancel-time" -> fixture.getSession().setCancelledAt(null);
            default -> throw new AssertionError(fact);
        }
        data.mock(fixture);
        assertRejected(500, CommonErrorCode.INTERNAL_ERROR.getCode());
    }

    /** Client 归属先于业务操作，未知与他人的订单均不泄漏存在性。 */
    @Test
    void test_unknownAndForeignOrder() {
        data.mock(fixture);
        var before = snapshots.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        invoke("ord_unknown").assertProblem(404, CommerceCheckoutErrorCode.ORDER_NOT_FOUND.getCode());
        invoke(Long.toString(checkoutDataMocker.otherClientId()), "1", orderNo()).assertProblem(404,
            CommerceCheckoutErrorCode.ORDER_NOT_FOUND.getCode());
        snapshots.assertUnchanged(before, checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        terminations.assertNoCalls();
    }

    /** 缺失、非法、非正数及溢出的可信 Header 在 HTTP 绑定阶段拒绝。 */
    @ParameterizedTest
    @MethodSource("invalidIdentities")
    void test_invalidIdentity(boolean client, String value) {
        data.mock(fixture);
        var before = snapshots.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        invoke(client ? value : Long.toString(checkoutDataMocker.clientId()), client ? "1" : value, orderNo())
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST.getCode());
        snapshots.assertUnchanged(before, checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        terminations.assertNoCalls();
    }

    /** Gateway 过渡期仍可能转发 Idempotency-Key；Commerce 忽略它，同一 Key 取消两个订单互不影响。 */
    @Test
    void test_forwardedIdempotencyKeyIgnored() {
        data.mock(fixture);
        var second = orders.template().clientOrderId("second-order").ready().build();
        data.mock(second);
        var client = Long.toString(checkoutDataMocker.clientId());

        invoke(client, "1", orderNo(), "forwarded-key").assertCancelled(orderNo(), true, NOW);
        invoke(client, "1", second.getOrder().getOrderNo(), "forwarded-key")
            .assertCancelled(second.getOrder().getOrderNo(), true, NOW);

        checker.assertCancelled(orderId(), true, NOW);
        checker.assertCancelled(second.getOrder().getOrderId(), true, NOW);
        terminations.assertNoCalls();
        assertTrue(intents.calls().isEmpty());
    }

    /** 合法边界不被误拒绝，业务取消保持原始公开订单号。 */
    @Test
    void test_maximumOrderNoAndCredential() {
        fixture.getOrder().setOrderNo("x".repeat(64));
        data.mock(fixture);

        invoke(Long.toString(checkoutDataMocker.clientId()), Long.toString(Long.MAX_VALUE), orderNo())
            .assertCancelled(orderNo(), true, NOW);

        checker.assertCancelled(orderId(), true, NOW);
        terminations.assertNoCalls();
    }

    /** 路径参数必须满足非空与长度限制，大小写变化和 Client 编号不会匹配订单号。 */
    @ParameterizedTest
    @ValueSource(strings = {"blank", "too-long", "case", "client-reference"})
    void test_invalidOrderNo(String input) {
        data.mock(fixture);
        String requested = switch (input) {
            case "blank" -> " ";
            case "too-long" -> "x".repeat(65);
            case "case" -> "ORD_" + orderNo().substring(4);
            default -> fixture.getOrder().getClientOrderId();
        };
        var before = snapshots.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        boolean invalid = input.equals("blank") || input.equals("too-long");
        invoke(requested).assertProblem(invalid ? 400 : 404,
            invalid ? CommonErrorCode.INVALID_REQUEST.getCode() : CommerceCheckoutErrorCode.ORDER_NOT_FOUND.getCode());
        snapshots.assertUnchanged(before, checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        terminations.assertNoCalls();
    }

    /** 无投影订单在 Payment 中的合成 Intent ID，与订单 ID 区分，证明终止使用按来源查询的结果。 */
    private static long intentIdOf(long orderId) {
        return orderId + 1;
    }

    /** Payment 合同的 Problem 故障。 */
    private static MockHttpFault problem(HttpStatus status, CommonErrorCode code) {
        return MockHttpFault.json(status.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
            status.getReasonPhrase(), "Payment 终止失败", code.getCode(), "payment-test",
            "2026-09-08T00:00:00.000000Z", null));
    }

    /** 本例订单归属 Client。 */
    private long clientId() {
        return checkoutDataMocker.clientId();
    }

    /** 统一验证拒绝后的全列事实保留以及没有远端操作。 */
    private void assertRejected(int status, String code) {
        var before = snapshots.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        invoke().assertProblem(status, code);
        snapshots.assertUnchanged(before, checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        terminations.assertNoCalls();
        assertTrue(intents.calls().isEmpty());
    }

    /** 本例根身份由公共 Mocker 生成并登记清理。 */
    private long orderId() {
        return fixture.getOrder().getOrderId();
    }

    /** 使用 Fixture 的公开订单号，不暴露内部主键到 URL。 */
    private String orderNo() {
        return fixture.getOrder().getOrderNo();
    }

    /** 默认取消本例订单。 */
    private CancelOrderResponseChecker invoke() {
        return invoke(orderNo());
    }

    /** 只提供 API 实际要求的 Header，不创建额外请求对象。 */
    private CancelOrderResponseChecker invoke(String orderNo) {
        return invoke(Long.toString(checkoutDataMocker.clientId()), "1", orderNo);
    }

    /** 不携带 Idempotency-Key 的真实 HTTP 入口。 */
    private CancelOrderResponseChecker invoke(String client, String credential, String orderNo) {
        return invoke(client, credential, orderNo, null);
    }

    /** 真实 HTTP 入口；key 仅模拟 Gateway 过渡期转发。每次显式检查外部调用事务边界和无关依赖零调用。 */
    private CancelOrderResponseChecker invoke(String client, String credential, String orderNo, String key) {
        var before = snapshots.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        var facts = before.keySet().stream().collect(Collectors.toMap(Function.identity(), checker::unchangedFacts));
        var headers = TestRequestHeaders.client(REQUEST_ID, client, credential);
        if (key != null) {
            headers.set(ApiHeaderNames.IDEMPOTENCY_KEY, key);
        }
        var path = UriComponentsBuilder.fromPath("/client-gateway-api/v1/orders/{orderNo}/cancel")
            .buildAndExpand(orderNo).toUriString();
        var result = httpTestInvoker.post(path, headers, null);
        facts.forEach((id, rows) -> assertEquals(rows, checker.unchangedFacts(id), "取消改动了金额、法律或退款事实"));
        assertEquals(before.keySet(),
            snapshots.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId()).keySet(),
            "取消不得创建或删除订单");
        assertFalse(paymentTransactions.transactionStates().contains(true), "事务内调用了 Payment 终止链路");
        preparationMocker.assertNoCalls();
        return new CancelOrderResponseChecker(result, objectMapper, REQUEST_ID);
    }

    /** 一次只改变一个身份 Header，避免校验相互遮蔽。 */
    private static Stream<Arguments> invalidIdentities() {
        return Stream.of(true, false).flatMap(client -> Stream.of(null, "0", "-1", "invalid", "9223372036854775808")
            .map(value -> Arguments.of(client, value)));
    }
}
