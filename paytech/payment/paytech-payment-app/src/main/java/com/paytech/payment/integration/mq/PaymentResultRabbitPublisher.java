package com.paytech.payment.integration.mq;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.payment.execution.domain.PaymentSucceededPayload;
import com.paytech.payment.execution.domain.PaymentSucceededResult;
import com.paytech.payment.execution.integration.PaymentResultPublisher;

import lombok.AllArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 支付结果的旧版 RabbitMQ 发布：topic_payment_result / payment.succeeded.v1（与 payment.events 新信封双写，消费方切换后删除）
 * <li>统一 JSON 信封、Persistent、mandatory；收到 Broker ack 且未被退回才算成功。</li>
 * <li>payloadHash 是 payload 按本类固定字段顺序序列化后的 UTF-8 字节 SHA-256；ID 与金额一律十进制字符串。</li>
 * <li>Payload 只含最小集合，不含卡数据、付款人引用、IP 与 User-Agent。</li>
 */
@Component
@AllArgsConstructor
public class PaymentResultRabbitPublisher implements PaymentResultPublisher {
    /** Durable Topic Exchange。 */
    public static final String EXCHANGE = "topic_payment_result";
    /** 支付成功路由键。 */
    public static final String SUCCEEDED_ROUTING_KEY = "payment.succeeded.v1";
    /** 支付成功事件类型。 */
    public static final String SUCCEEDED_EVENT_TYPE = PaymentSucceededPayload.EVENT_TYPE;

    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(10);

    private final JsonMapper jsonMapper;
    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishSucceeded(PaymentSucceededResult result) {
        String eventKey = "PAYMENT:PAYMENT_ATTEMPT:" + result.getPaymentAttemptId() + ":" + result.getAttemptVersion() + ":"
            + SUCCEEDED_EVENT_TYPE;
        CorrelationData correlation = new CorrelationData(eventKey);
        rabbitTemplate.send(EXCHANGE, SUCCEEDED_ROUTING_KEY, message(eventKey, result), correlation);
        CorrelationData.Confirm confirm = awaitConfirm(correlation);
        if (!confirm.ack() || correlation.getReturned() != null) {
            throw new ErrorException("支付结果未被 Broker 接收");
        }
    }

    /** 统一信封消息。 */
    Message message(String eventKey, PaymentSucceededResult result) {
        Map<String, String> payload = PaymentSucceededPayload.fields(result);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventKey", eventKey);
        envelope.put("eventType", SUCCEEDED_EVENT_TYPE);
        envelope.put("sourceSystem", "payment");
        envelope.put("sourceType", "PAYMENT_ATTEMPT");
        envelope.put("sourceId", Long.toString(result.getPaymentAttemptId()));
        envelope.put("sourceVersion", result.getAttemptVersion());
        envelope.put("occurredAt", result.getSucceededAt().toString());
        envelope.put("schemaVersion", 1);
        envelope.put("payloadHash", "sha256:" + sha256(jsonMapper.writeValueAsBytes(payload)));
        envelope.put("payload", payload);
        return MessageBuilder.withBody(jsonMapper.writeValueAsBytes(envelope)).setContentType("application/json")
            .setContentEncoding(StandardCharsets.UTF_8.name()).setDeliveryMode(MessageDeliveryMode.PERSISTENT)
            .setMessageId(eventKey).setType(SUCCEEDED_EVENT_TYPE).build();
    }

    /** 等待 Publisher Confirm；超时或中断按未发布处理，由补偿任务重试。 */
    static CorrelationData.Confirm awaitConfirm(CorrelationData correlation) {
        try {
            return correlation.getFuture().get(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ErrorException("等待支付结果确认被中断");
        } catch (ExecutionException | TimeoutException unconfirmed) {
            throw new ErrorException("支付结果未收到 Broker 确认");
        }
    }

    /** 小写十六进制 SHA-256。 */
    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
