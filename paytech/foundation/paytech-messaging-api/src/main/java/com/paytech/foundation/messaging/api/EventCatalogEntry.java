package com.paytech.foundation.messaging.api;

import java.util.List;
import java.util.Objects;

/**
 * 事件目录中的一条登记（design/08 §5）。
 *
 * @author Claude
 */
public final class EventCatalogEntry {

    private final String eventType;
    private final int schemaVersion;
    private final String producer;
    private final String aggregateType;
    private final String aggregateVersionField;
    private final List<String> consumers;
    private final boolean strictOrder;
    private final String payloadSchemaRef;

    /**
     * 创建登记项；字段合法性由目录读取实现校验。
     *
     * @param eventType 事件类型
     * @param schemaVersion 当前 Payload Schema 版本
     * @param producer 唯一发布服务
     * @param aggregateType 聚合类型
     * @param aggregateVersionField 聚合版本取自的业务字段，例如 {@code journal_sequence}
     * @param consumers 订阅服务，可为空列表（仅登记 Schema）
     * @param strictOrder 消费是否要求按版本连续
     * @param payloadSchemaRef Payload Schema 的引用位置
     */
    public EventCatalogEntry(String eventType, int schemaVersion, String producer, String aggregateType,
        String aggregateVersionField, List<String> consumers, boolean strictOrder, String payloadSchemaRef) {
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.schemaVersion = schemaVersion;
        this.producer = Objects.requireNonNull(producer, "producer must not be null");
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        this.aggregateVersionField = Objects.requireNonNull(aggregateVersionField,
            "aggregateVersionField must not be null");
        this.consumers = List.copyOf(consumers);
        this.strictOrder = strictOrder;
        this.payloadSchemaRef = Objects.requireNonNull(payloadSchemaRef, "payloadSchemaRef must not be null");
    }

    /**
     * 事件类型。
     *
     * @return PascalCase 事件类型
     */
    public String eventType() {
        return eventType;
    }

    /**
     * 当前 Payload Schema 版本。
     *
     * @return 正整数
     */
    public int schemaVersion() {
        return schemaVersion;
    }

    /**
     * 唯一发布服务。
     *
     * @return 01 §3 服务名
     */
    public String producer() {
        return producer;
    }

    /**
     * 聚合类型。
     *
     * @return 大写 Code
     */
    public String aggregateType() {
        return aggregateType;
    }

    /**
     * 聚合版本来源字段。
     *
     * @return 业务字段名
     */
    public String aggregateVersionField() {
        return aggregateVersionField;
    }

    /**
     * 订阅服务。
     *
     * @return 不可变列表
     */
    public List<String> consumers() {
        return consumers;
    }

    /**
     * 消费是否严格有序。
     *
     * @return 严格有序返回 true
     */
    public boolean strictOrder() {
        return strictOrder;
    }

    /**
     * Payload Schema 引用。
     *
     * @return 引用位置
     */
    public String payloadSchemaRef() {
        return payloadSchemaRef;
    }
}
