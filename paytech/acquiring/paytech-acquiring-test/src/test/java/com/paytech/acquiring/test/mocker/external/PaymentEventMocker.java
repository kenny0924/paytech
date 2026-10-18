package com.paytech.acquiring.test.mocker.external;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.acquiring.test.dal.ConsumedEventTestMapper;
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
import tools.jackson.databind.node.ObjectNode;

/**
 * 测试扮演 Payment：以 paytech-messaging 的真实发布实现（{@link RabbitDomainEventPublisher}，服务名 payment）向 payment.events
 * 发布 PaymentSucceeded 信封，经真实 Broker 路由到 acquiring 的分区队列。
 * <li>只记录本例发布的支付尝试；每例结束清空本服务分区队列与 DLQ，并删除这些尝试的消费登记两表行。</li>
 * <li>{@link #awaitOutcome} 观察 acquiring 自己的 {@code messaging.consumed{outcome}} 计数，用于确认重放 / 旧版本已被跳过。</li>
 */
@TestComponent
public class PaymentEventMocker implements Clear {
    /** 发布方服务名。 */
    public static final String PRODUCER = "payment";
    /** 本服务名。 */
    public static final String CONSUMER = "acquiring";
    /** 分区数，与 application.yml 一致。 */
    public static final int PARTITIONS = 4;
    /** 死信队列。 */
    public static final String DEAD_LETTER_QUEUE = MessagingTopology.deadLetterQueue(CONSUMER, PRODUCER);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate template;
    private final RabbitDomainEventPublisher publisher;
    private final ConsumedEventTestMapper consumedEvents;
    private final MeterRegistry meterRegistry;
    private final Set<String> attempts = ConcurrentHashMap.newKeySet();

    public PaymentEventMocker(ConnectionFactory connectionFactory, AmqpAdmin amqpAdmin, DomainEventCodec codec,
        ConsumedEventTestMapper consumedEvents, MeterRegistry meterRegistry) {
        this.amqpAdmin = amqpAdmin;
        this.template = new RabbitTemplate(connectionFactory);
        this.template.setMandatory(true);
        this.publisher = new RabbitDomainEventPublisher(PRODUCER, Duration.ofSeconds(10), codec, template,
            new MessagingMetrics(new SimpleMeterRegistry()));
        this.consumedEvents = consumedEvents;
        this.meterRegistry = meterRegistry;
    }

    /** 每例开始：消费登记两表存在，本服务分区队列与 DLQ 为空。 */
    public void beforeEach() {
        try {
            consumedEvents.checkTables();
        } catch (RuntimeException missing) {
            throw new AssertionError("mor_acquiring 缺少 t_consumed_event / t_consumed_aggregate_version；建表 SQL 见 "
                + "paytech/acquiring/paytech-acquiring-test/src/test/resources/init/ddl.sql（逐字取自 paytech-messaging），测试不会自动建表",
                missing);
        }
        purge();
    }

    /** 以 Payment 身份发布一条 PaymentSucceeded 信封并等待 Broker 确认与路由。 */
    public DomainEvent publish(Map<String, Object> payload, long version, Instant occurredAt) {
        DomainEvent event = event(payload, version, occurredAt);
        attempts.add(event.aggregateId());
        publisher.publish(event);
        return event;
    }

    /** 构造信封；Payload 字段顺序即给定 Map 顺序。 */
    public static DomainEvent event(Map<String, Object> payload, long version, Instant occurredAt) {
        ObjectNode node = JSON.valueToTree(payload);
        return DomainEvent.builder().eventType("PaymentSucceeded").schemaVersion(1)
            .aggregate("PAYMENT_ATTEMPT", (String)payload.get("paymentAttemptId")).aggregateVersion(version)
            .occurredAt(occurredAt).sourceService(PRODUCER).instanceId("main").payload(node).build();
    }

    /** 绕过发布端向 payment.events 发布原始字节（构造被篡改的信封）。 */
    public void publishRaw(String attemptId, byte[] body) throws Exception {
        attempts.add(attemptId);
        Message message = MessageBuilder.withBody(body).setContentType("application/json")
            .setContentEncoding(StandardCharsets.UTF_8.name()).setDeliveryMode(MessageDeliveryMode.PERSISTENT)
            .setMessageId(UUID.randomUUID().toString()).build();
        CorrelationData correlation = new CorrelationData(message.getMessageProperties().getMessageId());
        template.send(MessagingTopology.producerExchange(PRODUCER), attemptId, message, correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
        if (!confirm.ack() || correlation.getReturned() != null) {
            throw new AssertionError("测试支付结果未被 Broker 接收");
        }
    }

    /** 从 DLQ 取一条消息。 */
    public Message receiveDeadLetter(long timeoutMillis) {
        return template.receive(DEAD_LETTER_QUEUE, timeoutMillis);
    }

    /** acquiring 对 PaymentSucceeded 的某种消费结果计数（handled / replayed / stale / dead）。 */
    public double outcome(String outcome) {
        Counter counter = meterRegistry.find("messaging.consumed").tag("eventType", "PaymentSucceeded")
            .tag("outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    /** 等待某种消费结果的计数达到基线 + delta。 */
    public void awaitOutcome(String outcome, double baseline, double delta) {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10))
            .until(() -> outcome(outcome) >= baseline + delta);
    }

    /** 该支付尝试的消费登记行数。 */
    public int consumedEvents(long attemptId) {
        return consumedEvents.consumedEvents(Long.toString(attemptId));
    }

    /** 该支付尝试已处理的最大版本。 */
    public Long lastVersion(long attemptId) {
        return consumedEvents.lastVersion(Long.toString(attemptId));
    }

    @Override
    public void clear() {
        purge();
        for (String attemptId : attempts) {
            consumedEvents.deleteConsumedEvents(attemptId);
            consumedEvents.deleteVersion(attemptId);
        }
        attempts.clear();
    }

    private void purge() {
        for (int partition = 0; partition < PARTITIONS; partition++) {
            amqpAdmin.purgeQueue(MessagingTopology.partitionQueue(CONSUMER, PRODUCER, partition), false);
        }
        amqpAdmin.purgeQueue(DEAD_LETTER_QUEUE, false);
    }
}
