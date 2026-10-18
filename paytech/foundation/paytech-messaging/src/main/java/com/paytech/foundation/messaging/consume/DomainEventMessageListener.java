package com.paytech.foundation.messaging.consume;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter;
import org.springframework.amqp.rabbit.support.MessagePropertiesConverter;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventRejectedException;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.codec.InvalidDomainEventException;
import com.paytech.foundation.messaging.core.observability.ConsumeOutcome;
import com.paytech.foundation.messaging.core.observability.MessagingMetrics;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;

/**
 * 一个分区队列的手动 Ack 监听器：解码信封、路由到 Handler、按判定结果确认、重投或进入 DLQ。
 * <li>Ack 在本地事务提交之后发送；Ack 丢失导致的重投由事件 ID 去重吸收。</li>
 * <li>版本缺口：退避后把消息重新发布到本分区队列<b>尾部</b>（等待 Broker 确认后再 Ack 原消息），让排在后面的缺失版本先被处理；
 * Quorum Queue 对 Nack 重新入队的消息会优先重投，原样重新入队无法等到缺口补齐。尝试次数记在消息头
 * {@value #GAP_ATTEMPT_HEADER}。</li>
 * <li>Handler 异常：退避后 Nack 重新入队（同一消息优先重投）。</li>
 * <li>两者第 {@value MessagingTopology#DELIVERY_LIMIT} 次仍未成功时拒绝进入 DLQ，记录 ERROR 与 {@code outcome=dead}。</li>
 * <li>非法信封、发布方不符、Handler 抛出 {@link DomainEventRejectedException} 直接进入 DLQ；本服务未订阅的事件类型直接确认。</li>
 *
 * @author Claude
 */
public final class DomainEventMessageListener implements ChannelAwareMessageListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DomainEventMessageListener.class);

    /** 缺口重新发布的累计尝试次数。 */
    public static final String GAP_ATTEMPT_HEADER = "x-paytech-gap-attempts";

    private static final String DELIVERY_COUNT_HEADER = "x-delivery-count";

    private static final long REPUBLISH_CONFIRM_TIMEOUT_MILLIS = 10_000;

    private static final MessagePropertiesConverter PROPERTIES_CONVERTER = new DefaultMessagePropertiesConverter();

    private static final String UNKNOWN_EVENT_TYPE = "UNKNOWN";

    private static final int MAX_TRACKED_ATTEMPTS = 10_000;

    private final String producer;
    private final Duration retryBackoff;
    private final DomainEventCodec codec;
    private final MessagingMetrics metrics;
    private final DomainEventHandlerRegistry registry;
    private final IdempotentConsumeInterceptor interceptor;
    private final Map<String, Integer> attempts = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > MAX_TRACKED_ATTEMPTS;
        }
    };

    /**
     * 创建监听器。
     *
     * @param producer 该队列所属的发布服务名
     * @param retryBackoff 缺口或失败重投前的退避基数，第 n 次等待 n 倍
     * @param codec 信封编码
     * @param metrics 计数器
     * @param registry Handler 注册表
     * @param interceptor 幂等拦截器
     */
    public DomainEventMessageListener(String producer, Duration retryBackoff, DomainEventCodec codec,
        MessagingMetrics metrics, DomainEventHandlerRegistry registry, IdempotentConsumeInterceptor interceptor) {
        this.producer = Objects.requireNonNull(producer, "producer must not be null");
        this.retryBackoff = Objects.requireNonNull(retryBackoff, "retryBackoff must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.interceptor = Objects.requireNonNull(interceptor, "interceptor must not be null");
    }

    /**
     * 处理一条投递；所有分支都显式 Ack、Nack 或 Reject。
     */
    @Override
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        DomainEvent event;
        try {
            event = codec.decode(message.getBody());
        } catch (InvalidDomainEventException exception) {
            deadLetter(channel, deliveryTag, UNKNOWN_EVENT_TYPE, "非法领域事件信封: " + exception.getMessage());
            return;
        }
        if (!producer.equals(event.sourceService())) {
            deadLetter(channel, deliveryTag, event.eventType(), "发布方与队列不符: " + event);
            return;
        }
        Optional<DomainEventHandlerRegistry.Registration> registration = registry.find(event.eventType());
        if (registration.isEmpty()) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        handle(message, channel, event, registration.get());
    }

    private void handle(Message message, Channel channel, DomainEvent event,
        DomainEventHandlerRegistry.Registration registration) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        ConsumeOutcome outcome;
        try {
            outcome = interceptor.consume(event, registration);
        } catch (DomainEventRejectedException rejected) {
            forgetAttempts(event.eventId());
            deadLetter(channel, deliveryTag, event.eventType(), "Handler 拒绝领域事件: " + event + ", 原因="
                + rejected.getMessage());
            return;
        } catch (RuntimeException exception) {
            LOGGER.warn("领域事件处理失败，将重新投递: {}", event, exception);
            retryOrDeadLetter(message, channel, event, "Handler 持续失败");
            return;
        }
        if (outcome == ConsumeOutcome.GAP) {
            metrics.consumed(event.eventType(), ConsumeOutcome.GAP);
            deferGap(message, channel, event);
            return;
        }
        forgetAttempts(event.eventId());
        channel.basicAck(deliveryTag, false);
        metrics.consumed(event.eventType(), outcome);
    }

    private void retryOrDeadLetter(Message message, Channel channel, DomainEvent event, String reason)
        throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        int attempt = recordAttempt(event.eventId(), message);
        if (attempt >= MessagingTopology.DELIVERY_LIMIT) {
            forgetAttempts(event.eventId());
            deadLetter(channel, deliveryTag, event.eventType(), reason + "，已达投递上限: " + event);
            return;
        }
        pause(retryBackoff.multipliedBy(attempt));
        channel.basicNack(deliveryTag, false, true);
    }

    /**
     * 缺口等待：退避后把消息重新发布到本分区队列尾部，确认后 Ack 原消息；重新发布失败时退回 Nack 重新入队。
     * 先发布后 Ack 之间若进程中断，会多出一份副本，由事件 ID 去重吸收。
     */
    private void deferGap(Message message, Channel channel, DomainEvent event) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        int attempt = gapAttempts(message) + 1;
        if (attempt >= MessagingTopology.DELIVERY_LIMIT) {
            deadLetter(channel, deliveryTag, event.eventType(), "聚合版本缺口未补齐，已达投递上限: " + event);
            return;
        }
        pause(retryBackoff.multipliedBy(attempt));
        String queue = message.getMessageProperties().getConsumerQueue();
        try {
            MessageProperties properties = message.getMessageProperties();
            properties.setHeader(GAP_ATTEMPT_HEADER, attempt);
            AMQP.BasicProperties amqpProperties = PROPERTIES_CONVERTER.fromMessageProperties(properties,
                java.nio.charset.StandardCharsets.UTF_8.name());
            channel.confirmSelect();
            channel.basicPublish("", queue, true, amqpProperties, message.getBody());
            channel.waitForConfirmsOrDie(REPUBLISH_CONFIRM_TIMEOUT_MILLIS);
        } catch (IOException | java.util.concurrent.TimeoutException | RuntimeException exception) {
            LOGGER.warn("缺口消息重新发布失败，改为重新入队: {}", event, exception);
            channel.basicNack(deliveryTag, false, true);
            return;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            channel.basicNack(deliveryTag, false, true);
            return;
        }
        channel.basicAck(deliveryTag, false);
    }

    private static int gapAttempts(Message message) {
        Object value = message.getMessageProperties().getHeader(GAP_ATTEMPT_HEADER);
        return value instanceof Number number ? (int) Math.min(number.longValue(), Integer.MAX_VALUE) : 0;
    }

    private void deadLetter(Channel channel, long deliveryTag, String eventType, String reason) throws IOException {
        LOGGER.error("领域事件进入 DLQ: {}", reason);
        channel.basicReject(deliveryTag, false);
        metrics.consumed(eventType, ConsumeOutcome.DEAD);
    }

    private int recordAttempt(String eventId, Message message) {
        int brokerAttempt = deliveryCount(message) + 1;
        synchronized (attempts) {
            int localAttempt = attempts.getOrDefault(eventId, 0) + 1;
            int attempt = Math.max(brokerAttempt, localAttempt);
            attempts.put(eventId, attempt);
            return attempt;
        }
    }

    private void forgetAttempts(String eventId) {
        synchronized (attempts) {
            attempts.remove(eventId);
        }
    }

    private static int deliveryCount(Message message) {
        Object value = message.getMessageProperties().getHeader(DELIVERY_COUNT_HEADER);
        return value instanceof Number number ? (int) Math.min(number.longValue(), Integer.MAX_VALUE) : 0;
    }

    /** 退避等待；被中断（容器停止）时恢复中断标记并立即重投。 */
    private static void pause(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
