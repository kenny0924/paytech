package com.paytech.foundation.messaging.core.observability;

import java.util.Objects;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 消息组件的 Micrometer 计数：{@code messaging.published{outcome}}、{@code messaging.consumed{outcome}}；标签只用低基数的事件类型与结果。
 * <li>发布结果：{@code confirmed} 为 Broker 已确认且已路由；{@code unroutable} 为 ACK_AND_WARN 策略下被退回但视为成功。</li>
 *
 * @author Claude
 */
public final class MessagingMetrics {

    /** 发布成功计数名。 */
    public static final String PUBLISHED = "messaging.published";

    /** 发布结果：已确认且已路由。 */
    public static final String PUBLISHED_CONFIRMED = "confirmed";

    /** 发布结果：未路由，按 ACK_AND_WARN 视为成功。 */
    public static final String PUBLISHED_UNROUTABLE = "unroutable";

    /** 消费结果计数名。 */
    public static final String CONSUMED = "messaging.consumed";

    private final MeterRegistry registry;

    /**
     * 创建计数器集合。
     *
     * @param registry 宿主 MeterRegistry
     */
    public MessagingMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /**
     * 记录一次 Broker 已确认的发布。
     *
     * @param eventType 事件类型
     */
    public void published(String eventType) {
        Counter.builder(PUBLISHED).tag("eventType", eventType).tag("outcome", PUBLISHED_CONFIRMED).register(registry)
            .increment();
    }

    /**
     * 记录一次未路由但按 ACK_AND_WARN 视为成功的发布。
     *
     * @param eventType 事件类型
     */
    public void publishedUnroutable(String eventType) {
        Counter.builder(PUBLISHED).tag("eventType", eventType).tag("outcome", PUBLISHED_UNROUTABLE).register(registry)
            .increment();
    }

    /**
     * 记录一次消费结果。
     *
     * @param eventType 事件类型；非法信封无法解析时为 {@code UNKNOWN}
     * @param outcome 消费结果
     */
    public void consumed(String eventType, ConsumeOutcome outcome) {
        Counter.builder(CONSUMED).tag("eventType", eventType).tag("outcome", outcome.tag()).register(registry)
            .increment();
    }
}
