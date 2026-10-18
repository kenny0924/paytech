package com.paytech.client.master.test.mocker.onboarding;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.core.observability.MessagingMetrics;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;
import com.paytech.foundation.messaging.publish.RabbitDomainEventPublisher;
import com.paytech.foundation.testsupport.api.Clear;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * 测试扮演 compliance：以 paytech-messaging 的真实发布实现（{@link RabbitDomainEventPublisher}，服务名 compliance）向
 * compliance.events 发布 ClientReviewDecided 信封，经真实 Broker 路由到 client-master 的分区队列。
 * <li>每例开始与结束清空本服务分区队列与 DLQ；消费登记两表由全库清理器删除。</li>
 * <li>{@link #outcome} 观察 client-master 自己的 {@code messaging.consumed{outcome}} 计数，用于确认重放已被跳过。</li>
 */
@TestComponent
public class ComplianceEventMocker implements Clear {
    /** 发布方服务名。 */
    public static final String PRODUCER = "compliance";
    /** 本服务名。 */
    public static final String CONSUMER = "client-master";
    /** 分区数，与 messaging 默认值一致。 */
    public static final int PARTITIONS = 4;
    /** 死信队列。 */
    public static final String DEAD_LETTER_QUEUE = MessagingTopology.deadLetterQueue(CONSUMER, PRODUCER);

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate template;
    private final RabbitDomainEventPublisher publisher;
    private final MeterRegistry meterRegistry;

    public ComplianceEventMocker(ConnectionFactory connectionFactory, AmqpAdmin amqpAdmin, DomainEventCodec codec,
        MeterRegistry meterRegistry) {
        this.amqpAdmin = amqpAdmin;
        this.template = new RabbitTemplate(connectionFactory);
        this.template.setMandatory(true);
        this.publisher = new RabbitDomainEventPublisher(PRODUCER, Duration.ofSeconds(10), codec, template,
            new MessagingMetrics(new SimpleMeterRegistry()));
        this.meterRegistry = meterRegistry;
    }

    /** 每例开始清空分区队列与 DLQ。 */
    public void beforeEach() {
        purge();
    }

    /** 以 compliance 身份发布信封并等待 Broker 确认与路由。 */
    public void publish(DomainEvent event) {
        publisher.publish(event);
    }

    /** 绕过发布端向 compliance.events 发布原始字节（构造被篡改的信封）。 */
    public void publishRaw(String caseId, byte[] body) throws Exception {
        Message message = MessageBuilder.withBody(body).setContentType("application/json")
            .setContentEncoding(StandardCharsets.UTF_8.name()).setDeliveryMode(MessageDeliveryMode.PERSISTENT)
            .setMessageId(UUID.randomUUID().toString()).build();
        CorrelationData correlation = new CorrelationData(message.getMessageProperties().getMessageId());
        template.send(MessagingTopology.producerExchange(PRODUCER), caseId, message, correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
        if (!confirm.ack() || correlation.getReturned() != null) {
            throw new AssertionError("测试审核结果未被 Broker 接收");
        }
    }

    /** 从 DLQ 取一条消息。 */
    public Message receiveDeadLetter(long timeoutMillis) {
        return template.receive(DEAD_LETTER_QUEUE, timeoutMillis);
    }

    /** client-master 对 ClientReviewDecided 的某种消费结果计数（handled / replayed / stale / dead）。 */
    public double outcome(String outcome) {
        Counter counter = meterRegistry.find("messaging.consumed").tag("eventType", "ClientReviewDecided")
            .tag("outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    /** 等待某种消费结果的计数达到基线 + delta。 */
    public void awaitOutcome(String outcome, double baseline, double delta) {
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> outcome(outcome) >= baseline + delta);
    }

    @Override
    public void clear() {
        purge();
    }

    private void purge() {
        for (int partition = 0; partition < PARTITIONS; partition++) {
            amqpAdmin.purgeQueue(MessagingTopology.partitionQueue(CONSUMER, PRODUCER, partition), false);
        }
        amqpAdmin.purgeQueue(DEAD_LETTER_QUEUE, false);
    }
}
