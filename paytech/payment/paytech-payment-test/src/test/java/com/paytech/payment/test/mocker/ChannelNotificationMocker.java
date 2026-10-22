package com.paytech.payment.test.mocker;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.channel.connector.api.event.ChannelPaymentNotifiedPayload;
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
 * 以 channel-connector 身份向本地 Broker 的 channel-connector.events 发布 ChannelPaymentNotified（外部生产者的 DTO 替身，真实信封编码与
 * Publisher Confirm），并观察 payment 的分区队列、DLQ 与消费结果计数。
 * <li>每例开始与结束清空 payment.channel-connector.p0..p3 与 DLQ；消费登记行由 PaymentDataMocker 统一清理。</li>
 */
@TestComponent
public class ChannelNotificationMocker implements Clear {
    /** 发布方服务名。 */
    public static final String PRODUCER = "channel-connector";
    /** 本服务名。 */
    public static final String CONSUMER = "payment";
    /** 分区数，与 application.yml 一致。 */
    public static final int PARTITIONS = 4;
    /** 死信队列。 */
    public static final String DEAD_LETTER_QUEUE = MessagingTopology.deadLetterQueue(CONSUMER, PRODUCER);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate template;
    private final RabbitDomainEventPublisher publisher;
    private final MeterRegistry meterRegistry;

    public ChannelNotificationMocker(ConnectionFactory connectionFactory, AmqpAdmin amqpAdmin, DomainEventCodec codec,
        MeterRegistry meterRegistry) {
        this.amqpAdmin = amqpAdmin;
        this.template = new RabbitTemplate(connectionFactory);
        this.template.setMandatory(true);
        this.publisher = new RabbitDomainEventPublisher(PRODUCER, Duration.ofSeconds(10), codec, template,
            new MessagingMetrics(new SimpleMeterRegistry()));
        this.meterRegistry = meterRegistry;
    }

    /** 每例开始：分区队列与 DLQ 为空。 */
    public void beforeEach() {
        purge();
    }

    /** 构造一条通知信封：回调 ID 为聚合身份，版本固定为 1。 */
    public static DomainEvent event(long callbackId, long channelId, long channelAccountId, String merchantReference) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channelId", Long.toString(channelId));
        payload.put("channelAccountId", Long.toString(channelAccountId));
        payload.put("merchantReference", merchantReference);
        payload.put("channelTransactionRef", "T-NOTIFY-" + callbackId);
        payload.put("reportedStatus", "SUCCESS");
        payload.put("receivedAt", "2026-10-22T08:15:31.000000Z");
        return DomainEvent.builder().eventType(ChannelPaymentNotifiedPayload.EVENT_TYPE)
            .schemaVersion(ChannelPaymentNotifiedPayload.SCHEMA_VERSION)
            .aggregate(ChannelPaymentNotifiedPayload.AGGREGATE_TYPE, Long.toString(callbackId))
            .aggregateVersion(ChannelPaymentNotifiedPayload.AGGREGATE_VERSION)
            .occurredAt(Instant.parse("2026-10-22T08:15:31Z")).sourceService(PRODUCER).instanceId("main")
            .payload(JSON.valueToTree(payload)).build();
    }

    /** 发布并等待 Broker 确认与路由。 */
    public void publish(DomainEvent event) {
        publisher.publish(event);
    }

    /** 从 DLQ 取一条消息。 */
    public Message receiveDeadLetter(long timeoutMillis) {
        return template.receive(DEAD_LETTER_QUEUE, timeoutMillis);
    }

    /** payment 对 ChannelPaymentNotified 的某种消费结果计数（handled / replayed / stale / dead）。 */
    public double outcome(String outcome) {
        Counter counter = meterRegistry.find("messaging.consumed").tag("eventType", ChannelPaymentNotifiedPayload.EVENT_TYPE)
            .tag("outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    /** 等待某种消费结果的计数达到基线 + delta。 */
    public void awaitOutcome(String outcome, double baseline, double delta) {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> outcome(outcome) >= baseline + delta);
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
