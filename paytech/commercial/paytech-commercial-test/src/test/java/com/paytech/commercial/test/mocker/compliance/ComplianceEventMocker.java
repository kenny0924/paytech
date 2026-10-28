package com.paytech.commercial.test.mocker.compliance;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.awaitility.Awaitility;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.observability.MessagingMetrics;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;
import com.paytech.foundation.messaging.publish.RabbitDomainEventPublisher;
import com.paytech.foundation.testsupport.api.Clear;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.json.JsonMapper;

/**
 * 测试扮演 compliance：以 paytech-messaging 的真实发布实现（服务名 compliance）向 compliance.events 发布 QualificationChanged 信封，
 * 经真实本地 Broker 路由到 commercial 的分区队列 commercial.compliance.p0..p3
 * <li>每例结束清空本服务分区队列与 DLQ；消费登记两表由全库清理器删除。</li>
 * <li>{@link #awaitOutcome} 观察 commercial 自己的 {@code messaging.consumed{outcome}} 计数，确认 Listener 已处理完毕再断言。</li>
 */
@TestComponent
public class ComplianceEventMocker implements Clear {
    /** 发布方服务名。 */
    public static final String PRODUCER = "compliance";

    /** 本服务名。 */
    public static final String CONSUMER = "commercial";

    /** 分区数，与 messaging 默认值一致。 */
    public static final int PARTITIONS = 4;

    /** 死信队列。 */
    public static final String DEAD_LETTER_QUEUE = MessagingTopology.deadLetterQueue(CONSUMER, PRODUCER);

    /** 事件类型。 */
    static final String EVENT_TYPE = "QualificationChanged";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate template;
    private final MeterRegistry meterRegistry;
    private final RabbitDomainEventPublisher publisher;

    /** 以宿主连接构造独立的 compliance 身份发布器。 */
    public ComplianceEventMocker(ConnectionFactory connectionFactory, AmqpAdmin amqpAdmin, DomainEventCodec codec,
        MeterRegistry meterRegistry) {
        this.amqpAdmin = amqpAdmin;
        this.template = new RabbitTemplate(connectionFactory);
        this.template.setMandatory(true);
        this.publisher = new RabbitDomainEventPublisher(PRODUCER, Duration.ofSeconds(10), codec, template,
            new MessagingMetrics(new SimpleMeterRegistry()));
        this.meterRegistry = meterRegistry;
    }

    /**
     * QualificationChanged v1 信封；Payload 字段同 compliance QualificationChangedPayload
     *
     * @param qualificationId 资格 ID（聚合 ID）
     * @param roleType 角色类型
     * @param roleId 角色标识
     * @param status 迁移后资格状态
     * @return 信封
     */
    public static DomainEvent qualificationChanged(long qualificationId, String roleType, String roleId, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("qualificationId", Long.toString(qualificationId));
        payload.put("qualificationVersion", 2);
        payload.put("roleType", roleType);
        payload.put("roleId", roleId);
        payload.put("qualificationType", "CORPORATE_KYB");
        payload.put("previousStatus", "APPROVED");
        payload.put("status", status);
        payload.put("decisionId", Long.toString(qualificationId + 1));
        payload.put("decisionSource", "OPERATIONS");
        payload.put("changedAt", "2026-09-11T11:00:00Z");
        return event(qualificationId, payload);
    }

    /**
     * 任意 Payload 的 QualificationChanged 信封（构造不合法内容）
     *
     * @param qualificationId 资格 ID
     * @param payload Payload
     * @return 信封
     */
    public static DomainEvent event(long qualificationId, Map<String, Object> payload) {
        return DomainEvent.builder().eventType(EVENT_TYPE).schemaVersion(1).aggregate("ROLE_QUALIFICATION",
            Long.toString(qualificationId)).aggregateVersion(2).occurredAt(Instant.parse("2026-09-11T11:00:00Z"))
            .sourceService(PRODUCER).instanceId("main").payload(JSON.valueToTree(payload)).build();
    }

    /** 以 compliance 身份发布并等待 Broker 确认与路由。 */
    public void publish(DomainEvent event) {
        publisher.publish(event);
    }

    /** 从 DLQ 取一条消息。 */
    public Message receiveDeadLetter(long timeoutMillis) {
        return template.receive(DEAD_LETTER_QUEUE, timeoutMillis);
    }

    /** commercial 对 QualificationChanged 的某种消费结果计数（handled / replayed / stale / dead）。 */
    public double outcome(String outcome) {
        Counter counter = meterRegistry.find("messaging.consumed").tag("eventType", EVENT_TYPE).tag("outcome", outcome)
            .counter();
        return counter == null ? 0 : counter.count();
    }

    /** 等待某种消费结果的计数达到基线 + delta。 */
    public void awaitOutcome(String outcome, double baseline, double delta) {
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> outcome(outcome) >= baseline + delta);
    }

    /** 每例结束清空分区队列与 DLQ。 */
    @Override
    public void clear() {
        for (int partition = 0; partition < PARTITIONS; partition++) {
            amqpAdmin.purgeQueue(MessagingTopology.partitionQueue(CONSUMER, PRODUCER, partition), false);
        }
        amqpAdmin.purgeQueue(DEAD_LETTER_QUEUE, false);
    }
}
