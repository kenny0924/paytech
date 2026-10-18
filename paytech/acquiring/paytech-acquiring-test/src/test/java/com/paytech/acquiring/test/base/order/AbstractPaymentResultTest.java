package com.paytech.acquiring.test.base.order;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.acquiring.domain.checkout.CreateCheckoutContext;
import com.paytech.acquiring.test.base.MockTestBase;
import com.paytech.acquiring.test.mocker.external.PaymentEventMocker;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 测试扮演 Payment 发布 PaymentSucceeded：经 paytech-messaging 真实发布实现向 payment.events 发布 Schema v1 信封；不含 @Test。 */
public abstract class AbstractPaymentResultTest extends MockTestBase {
    /** 本例支付成功时间。 */
    protected static final String SUCCEEDED_TIME = "2026-09-08T00:00:01.654321Z";
    /** 本例事件发生时间。 */
    protected static final String OCCURRED_AT = "2026-09-08T00:00:02.123456Z";
    /** 本例支付尝试版本。 */
    protected static final long SOURCE_VERSION = 3;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    protected PaymentEventMocker paymentEvents;

    @Autowired
    private DomainEventCodec codec;

    /** 消费登记两表存在，本服务分区队列与 DLQ 只保留本例消息。 */
    @BeforeEach
    void setUpPaymentEvents() {
        paymentEvents.beforeEach();
    }

    /** 订单 Fixture 的成功载荷，字段顺序与发布方一致；Intent ID 与订单 ID 相同。 */
    protected static Map<String, Object> payload(CreateCheckoutContext fixture, long attemptId) {
        var order = fixture.getOrder();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientId", Long.toString(order.getClientId()));
        payload.put("orderId", Long.toString(order.getOrderId()));
        payload.put("sourceType", "COMMERCE_ORDER");
        payload.put("paymentIntentId", Long.toString(order.getOrderId()));
        payload.put("paymentAttemptId", Long.toString(attemptId));
        payload.put("sourceSubmissionId", "7000001");
        payload.put("entityNo", order.getEntityNo());
        payload.put("amount", "1.00");
        payload.put("currency", order.getCurrency());
        payload.put("capturedAmount", "1.00");
        payload.put("paymentSucceededTime", SUCCEEDED_TIME);
        payload.put("paymentMethodCode", "CARD_TEST");
        payload.put("channelId", "301");
        payload.put("channelAccountId", "302");
        payload.put("midId", "303");
        payload.put("channelTransactionRef", "ch-ref-" + attemptId);
        // v2 追加的 card 字段（acquiring 不读取）
        payload.put("instrumentRef", "7100001");
        payload.put("consentToSave", false);
        payload.put("customerId", null);
        return payload;
    }

    /** Commerce 业务防重使用的载荷 Hash：载荷 JSON 字节的 SHA-256 小写十六进制（沿用旧报文口径）。 */
    protected static String payloadHash(Map<String, Object> payload) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(payload)));
    }

    /** Commerce 业务防重使用的事件键（沿用旧报文口径）：来源版本固定为 3。 */
    protected static String eventKey(Map<String, Object> payload) {
        return "PAYMENT:PAYMENT_ATTEMPT:" + payload.get("paymentAttemptId") + ":" + SOURCE_VERSION + ":PaymentSucceeded";
    }

    /** 发布合法事件（版本 3）并等待确认。 */
    protected DomainEvent publish(Map<String, Object> payload) {
        return publish(payload, SOURCE_VERSION);
    }

    /** 发布指定版本的合法事件并等待确认。 */
    protected DomainEvent publish(Map<String, Object> payload, long version) {
        return paymentEvents.publish(payload, version, Instant.parse(OCCURRED_AT));
    }

    /** 发布指定信封版本的合法事件（来源版本 3）并等待确认。 */
    protected DomainEvent publishSchema(Map<String, Object> payload, int schemaVersion) {
        return paymentEvents.publish(payload, SOURCE_VERSION, Instant.parse(OCCURRED_AT), schemaVersion);
    }

    /** 发布 payloadHash 被篡改的信封。 */
    protected void publishTampered(Map<String, Object> payload) throws Exception {
        byte[] encoded = codec.encode(PaymentEventMocker.event(payload, SOURCE_VERSION, Instant.parse(OCCURRED_AT)));
        ObjectNode envelope = (ObjectNode)JSON.readTree(new String(encoded, StandardCharsets.UTF_8));
        envelope.put("payloadHash", "sha256:" + "0".repeat(64));
        paymentEvents.publishRaw((String)payload.get("paymentAttemptId"), JSON.writeValueAsBytes(envelope));
    }
}
