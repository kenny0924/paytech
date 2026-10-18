package com.paytech.foundation.messaging.consume;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;

import com.paytech.foundation.messaging.api.ConsumeTransaction;
import com.paytech.foundation.messaging.api.DomainEventConsumer;
import com.paytech.foundation.messaging.api.DomainEventHandler;
import com.paytech.foundation.messaging.api.EventCatalog;
import com.paytech.foundation.messaging.api.EventCatalogEntry;

/**
 * 启动时按事件目录核对全部 {@link DomainEventConsumer}，并按事件类型索引。
 * <li>每个实现必须标注 {@link DomainEventHandler}；每个事件类型只能有一个 Handler。</li>
 * <li>事件类型必须登记在目录中，目录 consumers 必须包含本服务，Handler 的 strictOrder 必须与目录一致。</li>
 *
 * @author Claude
 */
public final class DomainEventHandlerRegistry {

    private final Map<String, Registration> registrations;

    /**
     * 构造并校验注册表。
     *
     * @param consumerService 本服务名
     * @param consumers 宿主中的全部消费者 Bean
     * @param catalog 事件目录
     * @throws IllegalStateException 任一 Handler 与目录不一致
     */
    public DomainEventHandlerRegistry(String consumerService, List<DomainEventConsumer> consumers,
        EventCatalog catalog) {
        Objects.requireNonNull(consumerService, "consumerService must not be null");
        Map<String, Registration> indexed = new LinkedHashMap<>();
        for (DomainEventConsumer consumer : consumers) {
            Registration registration = register(consumerService, consumer, catalog);
            if (indexed.putIfAbsent(registration.eventType(), registration) != null) {
                throw new IllegalStateException("同一事件类型只能有一个 DomainEventHandler: " + registration.eventType());
            }
        }
        this.registrations = Map.copyOf(indexed);
    }

    /**
     * 查找事件类型的 Handler。
     *
     * @param eventType 事件类型
     * @return 注册项；本服务不消费该类型时为空
     */
    public Optional<Registration> find(String eventType) {
        return Optional.ofNullable(registrations.get(eventType));
    }

    /**
     * 本服务订阅的全部发布方。
     *
     * @return 有序去重的发布服务名
     */
    public Set<String> producers() {
        Set<String> producers = new TreeSet<>();
        registrations.values().forEach(registration -> producers.add(registration.producer()));
        return producers;
    }

    private static Registration register(String consumerService, DomainEventConsumer consumer, EventCatalog catalog) {
        Class<?> type = AopUtils.getTargetClass(consumer);
        DomainEventHandler annotation = AnnotationUtils.findAnnotation(type, DomainEventHandler.class);
        if (annotation == null) {
            throw new IllegalStateException("DomainEventConsumer 缺少 @DomainEventHandler: " + type.getName());
        }
        EventCatalogEntry entry = catalog.find(annotation.eventType()).orElseThrow(() -> new IllegalStateException(
            "事件类型未登记在事件目录: " + annotation.eventType() + ", Handler=" + type.getName()));
        if (!entry.consumers().contains(consumerService)) {
            throw new IllegalStateException("事件目录未登记本服务为消费者: " + entry.eventType() + ", 服务="
                + consumerService);
        }
        if (entry.strictOrder() != annotation.strictOrder()) {
            throw new IllegalStateException("Handler strictOrder 与事件目录不一致: " + entry.eventType() + ", Handler="
                + type.getName());
        }
        return new Registration(entry.eventType(), entry.producer(), annotation.strictOrder(), annotation.transaction(),
            consumer);
    }

    /**
     * 一个事件类型的消费注册。
     *
     * @param eventType 事件类型
     * @param producer 发布服务名
     * @param strictOrder 是否严格有序
     * @param transaction Handler 与消费登记的事务关系
     * @param consumer 业务 Handler
     */
    public record Registration(String eventType, String producer, boolean strictOrder, ConsumeTransaction transaction,
        DomainEventConsumer consumer) {
    }
}
