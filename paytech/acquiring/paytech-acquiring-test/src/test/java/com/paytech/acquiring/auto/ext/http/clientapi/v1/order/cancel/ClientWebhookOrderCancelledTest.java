package com.paytech.acquiring.auto.ext.http.clientapi.v1.order.cancel;

import static com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriComponentsBuilder;

import com.paytech.acquiring.api.client.v1.order.ClientOrderApi;
import com.paytech.acquiring.domain.enums.CheckoutSessionStatus;
import com.paytech.acquiring.domain.enums.ClientWebhookEventType;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.acquiring.domain.webhook.ClientWebhookEvent;
import com.paytech.acquiring.integration.payment.PaymentTerminationClient.Status;
import com.paytech.acquiring.test.base.MockTestBase;
import com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.Fixture;
import com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.Payment;
import com.paytech.acquiring.test.checker.order.CancelOrderResponseChecker;
import com.paytech.acquiring.test.checker.order.OrderCancellationChecker;
import com.paytech.acquiring.test.checker.webhook.ClientWebhookChecker;
import com.paytech.acquiring.test.mocker.external.PaymentTerminationMocker;
import com.paytech.acquiring.test.mocker.local.OrderCancellationDataMocker;
import com.paytech.acquiring.test.mocker.local.OrderQueryDataMocker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.JsonNode;

/**
 * 首次取消完成时冻结 order.cancelled：仅本地取消与 Payment 确认终止后取消各提交一次；重复取消与终止未决不提交。
 *
 * @see ClientOrderApi#cancelOrder(long, long, String, String)
 */
class ClientWebhookOrderCancelledTest extends MockTestBase {
    private static final String REQUEST_ID = "Commerce.CancelOrderWebhook.Test";

    @Autowired
    private OrderQueryDataMocker orders;
    @Autowired
    private OrderCancellationDataMocker data;
    @Autowired
    private PaymentTerminationMocker terminations;
    @Autowired
    private OrderCancellationChecker checker;
    @Autowired
    private ClientWebhookChecker webhooks;

    private Fixture fixture;

    /** 已初始化但尚未受理支付的新订单。 */
    @BeforeEach
    void setUpCancelledWebhook() {
        fixture = orders.template().ready().build();
    }

    /** 仅本地取消提交一次；Direct Pay 无会话时省略 checkoutStatus；重复取消不再登记或提交。 */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void test_localCancel(boolean hasSession) {
        fixture = orders.template().directPay().session(hasSession).ready().build();
        data.mock(fixture);

        invoke().assertCancelled(orderNo(), hasSession, NOW);

        var submission = webhooks.awaitSubmissions(eventId(), 1);
        webhooks.awaitTaskCompleted(eventId());
        webhooks.assertFrozen(submission, ClientWebhookEventType.ORDER_CANCELLED, eventId(), clientId(),
            "2026-09-08T00:00:00Z", expectedObject(hasSession, checker.order(orderId()).getVersion()));

        adjustableUtcClock.advance(Duration.ofHours(1));
        invoke().assertCancelled(orderNo(), hasSession, NOW);
        webhooks.assertNoTask(eventId());
        assertEquals(1, webhooks.submissions(eventId()).size(), "重复取消不能再次提交");
    }

    /** 已有 Intent：终止意图单独提交不登记事件，Payment 确认终止后的本地取消提交一次。 */
    @Test
    void test_cancelAfterPaymentTermination() {
        fixture.setPayment(Payment.builder().build());
        data.mock(fixture);
        terminations.mockDecision(orderId(), Status.TERMINATED);

        invoke().assertCancelled(orderNo(), true, NOW);

        var submission = webhooks.awaitSubmissions(eventId(), 1);
        webhooks.awaitTaskCompleted(eventId());
        webhooks.assertFrozen(submission, ClientWebhookEventType.ORDER_CANCELLED, eventId(), clientId(),
            "2026-09-08T00:00:00Z", expectedObject(true, checker.order(orderId()).getVersion()));
        assertEquals(1, terminations.calls().size());
        assertEquals(Long.toString(orderId()), terminations.calls().getFirst().intentId());
    }

    /** Payment 终止未决时订单保持开放，不登记也不提交。 */
    @Test
    void test_pendingTerminationNotSubmitted() {
        fixture.setPayment(Payment.builder().build());
        data.mock(fixture);
        terminations.mockDecision(orderId(), Status.PENDING);

        invoke().assertPending(orderNo());

        assertEquals(OrderStatus.OPEN, checker.order(orderId()).getOrderStatus());
        webhooks.assertNoTask(eventId());
        assertTrue(webhooks.submissions(eventId()).isEmpty());
    }

    /** 合同约定的 order.cancelled 对象；版本为取消写入后的订单版本。 */
    JsonNode expectedObject(boolean hasSession, long version) {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("orderNo", orderNo());
        object.put("clientOrderId", fixture.getOrder().getClientOrderId());
        object.put("orderStatus", OrderStatus.CANCELLED.getCode());
        if (hasSession) {
            object.put("checkoutStatus", CheckoutSessionStatus.CANCELLED.getCode());
        }
        object.put("reasonCode", ClientWebhookEvent.CLIENT_REQUESTED_REASON);
        object.put("cancelledAt", NOW.toString());
        object.put("version", version);
        return objectMapper.valueToTree(object);
    }

    /** 真实 HTTP 取消入口。 */
    CancelOrderResponseChecker invoke() {
        var headers = TestRequestHeaders.client(REQUEST_ID, Long.toString(clientId()), "1");
        var path = UriComponentsBuilder.fromPath("/client-gateway-api/v1/orders/{orderNo}/cancel")
            .buildAndExpand(orderNo()).toUriString();
        return new CancelOrderResponseChecker(httpTestInvoker.post(path, headers, null), objectMapper, REQUEST_ID);
    }

    /** 本例订单归属 Client。 */
    long clientId() {
        return checkoutDataMocker.clientId();
    }

    /** 本例订单 ID。 */
    long orderId() {
        return fixture.getOrder().getOrderId();
    }

    /** 本例公开订单号。 */
    String orderNo() {
        return fixture.getOrder().getOrderNo();
    }

    /** 本例订单的 order.cancelled 事件 ID。 */
    String eventId() {
        return ClientWebhookEventType.ORDER_CANCELLED.eventId(orderNo());
    }
}
