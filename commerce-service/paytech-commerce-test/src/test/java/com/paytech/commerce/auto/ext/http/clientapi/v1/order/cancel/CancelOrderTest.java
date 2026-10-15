package com.paytech.commerce.auto.ext.http.clientapi.v1.order.cancel;

import static com.paytech.commerce.test.builder.order.GetOrderDataBuilder.NOW;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriComponentsBuilder;

import com.paytech.commerce.client.api.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.commerce.client.api.v1.order.ClientOrderApi;
import com.paytech.commerce.domain.enums.CheckoutSessionStatus;
import com.paytech.commerce.domain.enums.OrderStatus;
import com.paytech.commerce.domain.enums.PaymentIntentStatus;
import com.paytech.commerce.integration.payment.PaymentTerminationClient;
import com.paytech.commerce.test.base.MockTestBase;
import com.paytech.commerce.test.builder.order.GetOrderDataBuilder.Fixture;
import com.paytech.commerce.test.builder.order.GetOrderDataBuilder.Payment;
import com.paytech.commerce.test.checker.order.CancelOrderResponseChecker;
import com.paytech.commerce.test.checker.order.OrderCancellationChecker;
import com.paytech.commerce.test.checker.refund.RefundQueryChecker;
import com.paytech.commerce.test.mocker.external.CheckoutIntentMocker;
import com.paytech.commerce.test.mocker.external.PaymentTerminationMocker;
import com.paytech.commerce.test.mocker.local.CheckoutPaymentDataMocker;
import com.paytech.commerce.test.mocker.local.OrderCancellationDataMocker;
import com.paytech.commerce.test.mocker.local.OrderQueryDataMocker;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

/**
 * 未支付订单取消、原取消事实重用、Client 隔离和支付创建中断后的取消。 终止端口配置 DTO / 异常；不代表真实 Payment HTTP 合同已接入。
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

    private Fixture fixture;

    /** 已初始化但尚未受理支付的新订单没有历史 preparationCommandId。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = orders.template().ready().build();
        fixture.getOrder().setPreparationCommandId(null);
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
        fixture.getOrder().setPreparationCommandId(null);
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

    /** 重提取消保留第一次时刻与版本，不再执行下游；原请求 Key 不参与业务。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_repeatCancellation(boolean hasSession) {
        fixture = orders.template().directPay().session(hasSession).ready().build();
        fixture.getOrder().setPreparationCommandId(null);
        data.mock(fixture);
        invoke().assertCancelled(orderNo(), hasSession, NOW);
        var before = snapshots.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        adjustableUtcClock.advance(Duration.ofHours(1));

        invoke(Long.toString(checkoutDataMocker.clientId()), "1", orderNo(), "another-key").assertCancelled(orderNo(),
            hasSession, NOW);

        snapshots.assertUnchanged(before, checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        terminations.assertNoCalls();
        assertTrue(intents.calls().isEmpty());
    }

    /** 已有 Intent 时按订单身份请求终止，调用在事务外，成功后本地取消。 */
    @ParameterizedTest
    @EnumSource(value = PaymentIntentStatus.class, names = {"REQUIRES_PAYMENT", "PROCESSING", "CANCELLED"})
    void test_cancelWithExistingIntent(PaymentIntentStatus status) {
        fixture.setPayment(Payment.builder().intentStatus(status).build());
        data.mock(fixture);
        mockTerminated(orderId());
        var paymentBefore = objectMapper.valueToTree(checker.payment(orderId()));

        invoke().assertCancelled(orderNo(), true, NOW);

        checker.assertCancelled(orderId(), true, NOW);
        assertEquals(paymentBefore, objectMapper.valueToTree(checker.payment(orderId())), "取消不能伪造 Payment 投影事件");
        assertEquals(1, terminations.calls().size());
        var request = terminations.calls().getFirst();
        assertAll(() -> assertEquals(checkoutDataMocker.clientId(), request.clientId()),
            () -> assertEquals(orderId(), request.orderId()));
        assertTrue(intents.calls().isEmpty());
        invoke().assertCancelled(orderNo(), true, NOW);
        assertEquals(1, terminations.calls().size(), "终态重提不能再次终止 Intent");
    }

    /** 历史准备标识但尚无支付关联时保持待定，重提不重复更新终止时间。 */
    @Test
    void test_pendingLegacyPreparation() {
        fixture.getOrder().setPreparationCommandId(orderId());
        data.mock(fixture);
        terminations.mockResult(orderId(),
            new PaymentTerminationClient.Result(orderId(), PaymentTerminationClient.Status.PENDING));

        invoke().assertPending(orderNo());
        var before = snapshots.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        adjustableUtcClock.advance(Duration.ofSeconds(10));
        invoke().assertPending(orderNo());

        snapshots.assertUnchanged(before, checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        assertAll(() -> assertEquals(OrderStatus.OPEN, checker.order(orderId()).getOrderStatus()),
            () -> assertEquals(NOW, checker.order(orderId()).getTerminationRequestedAt()),
            () -> assertEquals(8, checker.order(orderId()).getVersion()),
            () -> assertNull(checker.order(orderId()).getCancelledAt()),
            () -> assertEquals(CheckoutSessionStatus.OPEN, checker.session(orderId()).getSessionStatus()));
        assertTrue(intents.calls().isEmpty());
        assertEquals(2, terminations.calls().size());
    }

    /** 取消仅调用终止端口；超时保留原终止意图，重提成功也不创建或绑定 Intent。 */
    @Test
    void test_retryAfterTerminationUnavailable() {
        var accepted = paymentData.mockPrepared(false);
        long id = accepted.getOrder().getOrderId();
        String no = accepted.getOrder().getOrderNo();
        data.mockAcceptances(id, NOW);
        terminations.mockFailure(id,
            new ServiceUnavailableException(new java.net.SocketTimeoutException("response lost")));

        invoke(no).assertPending(no);
        assertAll(() -> assertEquals(OrderStatus.OPEN, checker.order(id).getOrderStatus()),
            () -> assertEquals(NOW, checker.order(id).getTerminationRequestedAt()),
            () -> assertNull(checker.payment(id)), () -> assertNull(checker.order(id).getCancelledAt()));
        mockTerminated(id);
        adjustableUtcClock.advance(Duration.ofSeconds(5));

        invoke(no).assertCancelled(no, true, NOW.plusSeconds(5));

        checker.assertCancelled(id, true, NOW.plusSeconds(5));
        assertEquals(NOW, checker.order(id).getTerminationRequestedAt());
        assertEquals(3, checker.order(id).getVersion());
        assertNull(checker.payment(id));
        assertTrue(intents.calls().isEmpty(), "取消不能创建 Intent");
        assertEquals(2, terminations.calls().size());
        assertEquals(terminations.calls().getFirst(), terminations.calls().getLast());
    }

    /** Payment 明确不存在时完成本地取消，重复取消不重新外呼。 */
    @Test
    void test_intentNotFoundCancelsWithoutCreatingIntent() {
        var accepted = paymentData.mockPrepared(false);
        long id = accepted.getOrder().getOrderId();
        String no = accepted.getOrder().getOrderNo();
        data.mockAcceptances(id, NOW);
        terminations.mockFailure(id, new FailException(CommonErrorCode.NOT_FOUND, "支付意图不存在"));

        invoke(no).assertCancelled(no, true, NOW);
        invoke(no).assertCancelled(no, true, NOW);

        checker.assertCancelled(id, true, NOW);
        assertNull(checker.payment(id));
        assertTrue(intents.calls().isEmpty());
        assertEquals(1, terminations.calls().size());
        assertEquals(new PaymentTerminationClient.Request(checkoutDataMocker.clientId(), id),
            terminations.calls().getFirst());
    }

    /** 非不存在错误不能被吞掉；已保存终止意图不回滚，订单尚未取消。 */
    @Test
    void test_otherBusinessErrorDoesNotCancel() {
        fixture.setPayment(Payment.builder().build());
        data.mock(fixture);
        terminations.mockFailure(orderId(), new FailException(CommonErrorCode.BUSINESS_CHECK_FAILED));

        invoke().assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode());

        assertEquals(OrderStatus.OPEN, checker.order(orderId()).getOrderStatus());
        assertNotNull(checker.order(orderId()).getTerminationRequestedAt());
        assertNull(checker.order(orderId()).getCancelledAt());
        assertTrue(intents.calls().isEmpty());
    }

    /** 未决或需要撤销时不能把本地订单关闭。 */
    @ParameterizedTest
    @EnumSource(value = PaymentTerminationClient.Status.class, names = {"PENDING", "REVERSAL_REQUIRED"})
    void test_terminationNotCompleted(PaymentTerminationClient.Status status) {
        fixture.setPayment(Payment.builder().build());
        data.mock(fixture);
        terminations.mockResult(orderId(), new PaymentTerminationClient.Result(orderId(), status));

        var response = invoke();
        if (status == PaymentTerminationClient.Status.PENDING) {
            response.assertPending(orderNo());
        } else {
            response.assertProblem(409, CommerceCheckoutErrorCode.REVERSAL_REQUIRED.getCode());
        }
        assertEquals(OrderStatus.OPEN, checker.order(orderId()).getOrderStatus());
        assertNull(checker.order(orderId()).getCancelledAt());
        assertTrue(intents.calls().isEmpty());
    }

    /** 错归属的取消结果返回协议错误，不能据此关闭原订单。 */
    @Test
    void test_terminationRejectsForeignOrder() {
        fixture.setPayment(Payment.builder().build());
        data.mock(fixture);
        terminations.mockResult(orderId(),
            new PaymentTerminationClient.Result(orderId() + 1, PaymentTerminationClient.Status.TERMINATED));

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
        invoke(Long.toString(checkoutDataMocker.otherClientId()), "1", orderNo(), "key").assertProblem(404,
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
        invoke(client ? value : Long.toString(checkoutDataMocker.clientId()), client ? "1" : value, orderNo(), "key")
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST.getCode());
        snapshots.assertUnchanged(before, checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        terminations.assertNoCalls();
    }

    /** 当前 API 仍声明该 Header 必填，测试遵守现有绑定，不新增请求幂等实现。 */
    @Test
    void test_missingReservedHeader() {
        data.mock(fixture);
        var before = snapshots.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        invoke(Long.toString(checkoutDataMocker.clientId()), "1", orderNo(), null).assertProblem(400,
            CommonErrorCode.INVALID_REQUEST.getCode());
        snapshots.assertUnchanged(before, checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        terminations.assertNoCalls();
    }

    /** 同一个接入 Key 可以取消两个不同订单，Commerce 不缓存首次 HTTP 结果。 */
    @Test
    void test_sameHeaderDoesNotReplaceBusinessIdentity() {
        data.mock(fixture);
        var second = orders.template().clientOrderId("second-order").ready().build();
        second.getOrder().setPreparationCommandId(null);
        data.mock(second);

        invoke().assertCancelled(orderNo(), true, NOW);
        invoke(second.getOrder().getOrderNo()).assertCancelled(second.getOrder().getOrderNo(), true, NOW);

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

        invoke(Long.toString(checkoutDataMocker.clientId()), Long.toString(Long.MAX_VALUE), orderNo(), "key")
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

    /** 只配置当前订单的终止 DTO，不自动响应其他订单。 */
    private void mockTerminated(long orderId) {
        terminations.mockResult(orderId,
            new PaymentTerminationClient.Result(orderId, PaymentTerminationClient.Status.TERMINATED));
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
        return invoke(Long.toString(checkoutDataMocker.clientId()), "1", orderNo, "cancel-test-key");
    }

    /** 真实 HTTP 入口；每次显式检查外部调用事务边界和无关依赖零调用。 */
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
        terminations.assertOutsideTransaction();
        preparationMocker.assertNoCalls();
        return new CancelOrderResponseChecker(result, objectMapper, REQUEST_ID);
    }

    /** 一次只改变一个身份 Header，避免校验相互遮蔽。 */
    private static Stream<Arguments> invalidIdentities() {
        return Stream.of(true, false).flatMap(client -> Stream.of(null, "0", "-1", "invalid", "9223372036854775808")
            .map(value -> Arguments.of(client, value)));
    }
}
