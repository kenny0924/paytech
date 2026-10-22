package com.paytech.acquiring.auto.ext.job;

import static com.paytech.acquiring.test.mocker.local.OrderExpiryDataMocker.DUE_AT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.acquiring.domain.enums.CheckoutSessionStatus;
import com.paytech.acquiring.domain.enums.ClientWebhookEventType;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.acquiring.domain.webhook.ClientWebhookEvent;
import com.paytech.acquiring.ext.job.OrderExpiryJobHandler;
import com.paytech.acquiring.integration.payment.PaymentTerminationClient.Status;
import com.paytech.acquiring.service.processor.order.response.ScanExpiredOrdersResponse;
import com.paytech.acquiring.test.base.MockTestBase;
import com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.Fixture;
import com.paytech.acquiring.test.checker.order.OrderExpiryChecker;
import com.paytech.acquiring.test.checker.webhook.ClientWebhookChecker;
import com.paytech.acquiring.test.mocker.external.PaymentTerminationMocker;
import com.paytech.acquiring.test.mocker.local.OrderExpiryDataMocker;

import tools.jackson.databind.JsonNode;

/**
 * 首次过期收尾时冻结 order.expired：本地收尾与 Payment 确认终止后收尾各提交一次；重复扫描与终止未决不提交。
 *
 * @see OrderExpiryJobHandler#runOnce(int, int)
 */
class ClientWebhookOrderExpiredTest extends MockTestBase {
    @Autowired
    private OrderExpiryJobHandler job;
    @Autowired
    private OrderExpiryDataMocker data;
    @Autowired
    private OrderExpiryChecker checker;
    @Autowired
    private PaymentTerminationMocker terminations;
    @Autowired
    private ClientWebhookChecker webhooks;

    private Fixture fixture;

    /** 本地收尾提交一次；Direct Pay 无会话时省略 checkoutStatus；重复扫描不再登记或提交。 */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void test_localExpire(boolean hasSession) {
        fixture = data.mock(data.template().directPay().session(hasSession).ready(), DUE_AT);

        assertEquals(1, scan().getScannedCount());

        var submission = webhooks.awaitSubmissions(eventId(), 1);
        webhooks.awaitTaskCompleted(eventId());
        webhooks.assertFrozen(submission, ClientWebhookEventType.ORDER_EXPIRED, eventId(), clientId(),
            "2026-09-08T00:00:00Z", expectedObject(hasSession, checker.order(orderId()).getVersion()));
        assertEquals("evt_" + fixture.getOrder().getOrderNo() + "_expired", submission.envelope().path("eventId").asString());

        adjustableUtcClock.advance(Duration.ofHours(1));
        assertEquals(0, scan().getScannedCount());
        webhooks.assertNoTask(eventId());
        assertEquals(1, webhooks.submissions(eventId()).size(), "重复扫描不能再次提交");
    }

    /** 已有 Intent：终止意图单独提交不登记事件，Payment 确认终止后的过期收尾提交一次。 */
    @Test
    void test_expireAfterPaymentTermination() {
        fixture = data.mockWithProjection();
        terminations.mockDecision(orderId(), Status.TERMINATED);

        assertEquals(1, scan().getScannedCount());

        var submission = webhooks.awaitSubmissions(eventId(), 1);
        webhooks.awaitTaskCompleted(eventId());
        webhooks.assertFrozen(submission, ClientWebhookEventType.ORDER_EXPIRED, eventId(), clientId(),
            "2026-09-08T00:00:00Z", expectedObject(true, checker.order(orderId()).getVersion()));
    }

    /** Payment 终止未决时订单保持开放，不登记也不提交。 */
    @Test
    void test_pendingTerminationNotSubmitted() {
        fixture = data.mockWithProjection();
        terminations.mockDecision(orderId(), Status.PENDING);

        assertEquals(1, scan().getScannedCount());

        assertEquals(OrderStatus.OPEN, checker.order(orderId()).getOrderStatus());
        webhooks.assertNoTask(eventId());
        assertTrue(webhooks.submissions(eventId()).isEmpty());
    }

    /** 合同约定的 order.expired 对象；expiredAt 为订单原到期时刻，版本为过期写入后的订单版本。 */
    JsonNode expectedObject(boolean hasSession, long version) {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("orderNo", fixture.getOrder().getOrderNo());
        object.put("clientOrderId", fixture.getOrder().getClientOrderId());
        object.put("orderStatus", OrderStatus.EXPIRED.getCode());
        if (hasSession) {
            object.put("checkoutStatus", CheckoutSessionStatus.EXPIRED.getCode());
        }
        object.put("reasonCode", ClientWebhookEvent.EXPIRY_REACHED_REASON);
        object.put("expiredAt", DUE_AT.toString());
        object.put("version", version);
        return objectMapper.valueToTree(object);
    }

    /** 经 Handler 单分片扫描本轮时刻到期的订单。 */
    ScanExpiredOrdersResponse scan() {
        return job.runOnce(0, 1);
    }

    /** 本例订单归属 Client。 */
    long clientId() {
        return checkoutDataMocker.clientId();
    }

    /** 本例订单 ID。 */
    long orderId() {
        return fixture.getOrder().getOrderId();
    }

    /** 本例订单的 order.expired 事件 ID。 */
    String eventId() {
        return ClientWebhookEventType.ORDER_EXPIRED.eventId(fixture.getOrder().getOrderNo());
    }
}
