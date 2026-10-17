package com.paytech.risk.ext.mq;

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
import com.paytech.risk.compliance.domain.ClientReviewResult;
import com.paytech.risk.compliance.integration.ClientReviewResultPublisher;

import lombok.AllArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 审核结果的 RabbitMQ 发布：topic_risk_client_review / client_review.decided.v1
 * <li>统一 JSON 信封、Persistent、mandatory；收到 Broker ack 且未被退回才算成功。</li>
 * <li>payloadHash 是 payload 按本类固定字段顺序序列化后的 UTF-8 字节 SHA-256；ID 一律十进制字符串。</li>
 */
@Component
@AllArgsConstructor
public class ClientReviewResultRabbitPublisher implements ClientReviewResultPublisher {
    /** Durable Topic Exchange。 */
    public static final String EXCHANGE = "topic_risk_client_review";
    /** 路由键。 */
    public static final String ROUTING_KEY = "client_review.decided.v1";
    /** 事件类型。 */
    public static final String EVENT_TYPE = "ClientReviewDecided";

    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(10);

    private final JsonMapper jsonMapper;
    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(ClientReviewResult result) {
        String eventKey = "RISK:DD_CASE:" + result.getCaseId() + ":" + result.getCaseVersion() + ":" + EVENT_TYPE;
        CorrelationData correlation = new CorrelationData(eventKey);
        rabbitTemplate.send(EXCHANGE, ROUTING_KEY, message(eventKey, result), correlation);
        CorrelationData.Confirm confirm = awaitConfirm(correlation);
        if (!confirm.ack() || correlation.getReturned() != null) {
            throw new ErrorException("审核结果未被 Broker 接收");
        }
    }

    /** 统一信封消息。 */
    Message message(String eventKey, ClientReviewResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", Long.toString(result.getCaseId()));
        payload.put("caseVersion", result.getCaseVersion());
        payload.put("applicationId", Long.toString(result.getApplicationId()));
        payload.put("submissionId", result.getSourceSubmissionId());
        payload.put("outcome", result.getOutcome());
        payload.put("returnSection", result.getReturnSection());
        payload.put("publicNote", result.getPublicNote());
        payload.put("decidedAt", result.getDecidedAt().toString());
        payload.put("decisionId", result.getDecisionId() == null ? null : Long.toString(result.getDecisionId()));
        payload.put("decidedBy", result.getDecidedBy() == null ? null : Long.toString(result.getDecidedBy()));
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventKey", eventKey);
        envelope.put("eventType", EVENT_TYPE);
        envelope.put("sourceSystem", "risk");
        envelope.put("sourceType", "DD_CASE");
        envelope.put("sourceId", Long.toString(result.getCaseId()));
        envelope.put("sourceVersion", result.getCaseVersion());
        envelope.put("occurredAt", result.getDecidedAt().toString());
        envelope.put("schemaVersion", 1);
        envelope.put("payloadHash", "sha256:" + sha256(jsonMapper.writeValueAsBytes(payload)));
        envelope.put("payload", payload);
        return MessageBuilder.withBody(jsonMapper.writeValueAsBytes(envelope)).setContentType("application/json")
            .setContentEncoding(StandardCharsets.UTF_8.name()).setDeliveryMode(MessageDeliveryMode.PERSISTENT)
            .setMessageId(eventKey).setType(EVENT_TYPE).build();
    }

    /** 等待 Publisher Confirm；超时或中断按未发布处理，由补偿任务重试。 */
    static CorrelationData.Confirm awaitConfirm(CorrelationData correlation) {
        try {
            return correlation.getFuture().get(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ErrorException("等待审核结果确认被中断");
        } catch (ExecutionException | TimeoutException unconfirmed) {
            throw new ErrorException("审核结果未收到 Broker 确认");
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
