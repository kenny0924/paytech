package com.paytech.foundation.messaging.publish;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventPublishException;
import com.paytech.foundation.messaging.api.DomainEventPublisher;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.observability.MessagingMetrics;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;

/**
 * RabbitMQ 发布实现：交换机 {@code <sourceService>.events}，路由键 {@code aggregateId}（design/08 §2–§3）。
 * <li>AMQP 属性：{@code messageId=eventId}、{@code type=eventType}、{@code correlationId}、Persistent、
 * {@code x-aggregate-id} 头。</li>
 * <li>{@code payloadHash} 在编码时计算并写入信封；Payload 超出 JSON Object Profile 时抛 {@link IllegalArgumentException}（编程错误，不重试）。</li>
 * <li>mandatory 发送并等待 Publisher Confirm；Nack、退回（未路由到任何队列）或确认超时都抛出
 * {@link DomainEventPublishException}，由补偿 Handler 按可重试失败处理。</li>
 *
 * @author Claude
 */
public final class RabbitDomainEventPublisher implements DomainEventPublisher {

    private final String producer;
    private final Duration confirmTimeout;
    private final DomainEventCodec codec;
    private final RabbitTemplate template;
    private final MessagingMetrics metrics;

    /**
     * 创建发布器。
     *
     * @param producer 本服务名，必须等于信封 {@code sourceService}
     * @param confirmTimeout Publisher Confirm 最长等待时间
     * @param codec 信封编码
     * @param template 已启用 mandatory 的专用模板，连接需开启 Correlated Confirm 与 Returns
     * @param metrics 计数器
     */
    public RabbitDomainEventPublisher(String producer, Duration confirmTimeout, DomainEventCodec codec,
        RabbitTemplate template, MessagingMetrics metrics) {
        this.producer = Objects.requireNonNull(producer, "producer must not be null");
        this.confirmTimeout = Objects.requireNonNull(confirmTimeout, "confirmTimeout must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.template = Objects.requireNonNull(template, "template must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /**
     * 同步发布并等待 Broker 确认；必须在数据库事务之外调用。
     */
    @Override
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (!producer.equals(event.sourceService())) {
            throw new IllegalArgumentException("event sourceService does not match messaging.producer.service");
        }
        Message message = message(event, codec.encode(event));
        CorrelationData correlation = new CorrelationData(event.eventId());
        try {
            template.send(MessagingTopology.producerExchange(producer), event.aggregateId(), message, correlation);
        } catch (AmqpException exception) {
            throw new DomainEventPublishException("领域事件发送失败: " + event.eventType() + ", eventId="
                + event.eventId(), exception);
        }
        CorrelationData.Confirm confirm = awaitConfirm(event, correlation);
        if (!confirm.ack()) {
            throw new DomainEventPublishException("Broker 拒绝领域事件: " + event.eventType() + ", eventId="
                + event.eventId() + ", 原因=" + confirm.reason());
        }
        if (correlation.getReturned() != null) {
            throw new DomainEventPublishException("领域事件未路由到任何队列: " + event.eventType() + ", eventId="
                + event.eventId() + ", replyCode=" + correlation.getReturned().getReplyCode());
        }
        metrics.published(event.eventType());
    }

    private static Message message(DomainEvent event, byte[] body) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(event.eventId());
        properties.setType(event.eventType());
        if (event.correlationId() != null) {
            properties.setCorrelationId(event.correlationId());
        }
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setHeader(MessagingTopology.AGGREGATE_ID_HEADER, event.aggregateId());
        return new Message(body, properties);
    }

    private CorrelationData.Confirm awaitConfirm(DomainEvent event, CorrelationData correlation) {
        try {
            return correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new DomainEventPublishException("等待领域事件确认被中断: eventId=" + event.eventId(), interrupted);
        } catch (ExecutionException | TimeoutException unconfirmed) {
            throw new DomainEventPublishException("领域事件未收到 Broker 确认: eventId=" + event.eventId(), unconfirmed);
        }
    }
}
