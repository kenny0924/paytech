package com.paytech.compliance.ext.mq;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.paytech.compliance.api.event.ClientReviewDecidedPayload;
import com.paytech.compliance.domain.ClientReviewResult;
import com.paytech.compliance.integration.ClientReviewResultPublisher;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventPublisher;

import tools.jackson.databind.json.JsonMapper;

/**
 * 审核结果的 RabbitMQ 双写发布（design/07 §5、design/08 §3）
 * <li>旧 Topic：topic_risk_client_review / client_review.decided.v1，Exchange 与路由键不变；统一 JSON 信封、Persistent、mandatory，
 * 收到 Broker ack 且未被退回才算成功；payloadHash 是 payload 按固定字段顺序序列化后的 UTF-8 字节 SHA-256。client-master 消费者
 * 第 08 步切换前继续使用。</li>
 * <li>新信封：经 {@link DomainEventPublisher} 发布 ClientReviewDecided Schema v1 到 compliance.events（aggregateType=DD_CASE、
 * aggregateId=caseId、aggregateVersion=caseVersion），Payload 与旧报文同字段同顺序。</li>
 * <li>先旧后新，两者都确认才返回；任一失败抛出，由补偿任务重试并重发两者，消费方按 eventKey / eventId 与 Payload Hash 防重。</li>
 */
@Component
public class ClientReviewResultRabbitPublisher implements ClientReviewResultPublisher {
    /** Durable Topic Exchange。 */
    public static final String EXCHANGE = "topic_risk_client_review";
    /** 路由键。 */
    public static final String ROUTING_KEY = "client_review.decided.v1";
    /** 事件类型。 */
    public static final String EVENT_TYPE = ClientReviewDecidedPayload.EVENT_TYPE;

    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(10);

    private final JsonMapper jsonMapper;

    private final RabbitTemplate rabbitTemplate;

    private final DomainEventPublisher domainEvents;

    /** 信封 sourceService，与 messaging.producer.service 一致。 */
    private final String sourceService;

    /** 信封 instanceId（实例清单 instance.id）。 */
    private final String instanceId;

    public ClientReviewResultRabbitPublisher(JsonMapper jsonMapper, RabbitTemplate rabbitTemplate,
        DomainEventPublisher domainEvents, @Value("${messaging.producer.service}") String sourceService,
        @Value("${paytech.instance-id}") String instanceId) {
        this.jsonMapper = jsonMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.domainEvents = domainEvents;
        this.sourceService = sourceService;
        this.instanceId = instanceId;
    }

    @Override
    public void publish(ClientReviewResult result) {
        String eventKey = "RISK:DD_CASE:" + result.getCaseId() + ":" + result.getCaseVersion() + ":" + EVENT_TYPE;
        CorrelationData correlation = new CorrelationData(eventKey);
        rabbitTemplate.send(EXCHANGE, ROUTING_KEY, message(eventKey, result), correlation);
        CorrelationData.Confirm confirm = awaitConfirm(correlation);
        if (!confirm.ack() || correlation.getReturned() != null) {
            throw new ErrorException("审核结果未被 Broker 接收");
        }
        domainEvents.publish(envelope(result));
    }

    /** ClientReviewDecided Schema v1 信封；eventId 默认取 AggregateKey 四段式事件键，补偿重试不变。 */
    DomainEvent envelope(ClientReviewResult result) {
        return DomainEvent.builder().eventType(ClientReviewDecidedPayload.EVENT_TYPE)
            .schemaVersion(ClientReviewDecidedPayload.SCHEMA_VERSION)
            .aggregate(ClientReviewDecidedPayload.AGGREGATE_TYPE, Long.toString(result.getCaseId()))
            .aggregateVersion(result.getCaseVersion()).occurredAt(result.getDecidedAt()).sourceService(sourceService)
            .instanceId(instanceId).payload(jsonMapper.valueToTree(payload(result))).build();
    }

    /** 统一信封消息。 */
    Message message(String eventKey, ClientReviewResult result) {
        Map<String, Object> payload = payload(result);
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

    /** 两份报文共用的 Payload，字段顺序即 {@link ClientReviewDecidedPayload#FIELDS}。 */
    static Map<String, Object> payload(ClientReviewResult result) {
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
        return payload;
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
