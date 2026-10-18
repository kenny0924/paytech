package com.paytech.foundation.messaging.persistence.mapper;

/**
 * {@code t_consumed_event} 的一行。
 *
 * @author Claude
 */
public final class ConsumedEvent {

    /** 事件 ID。 */
    private final String eventId;

    /** 事件类型。 */
    private final String eventType;

    /** 聚合类型。 */
    private final String aggregateType;

    /** 聚合标识。 */
    private final String aggregateId;

    /** 聚合版本。 */
    private final long aggregateVersion;

    /**
     * 创建待登记的已消费事件。
     *
     * @param eventId 事件 ID
     * @param eventType 事件类型
     * @param aggregateType 聚合类型
     * @param aggregateId 聚合标识
     * @param aggregateVersion 聚合版本
     */
    public ConsumedEvent(String eventId, String eventType, String aggregateType, String aggregateId,
        long aggregateVersion) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.aggregateVersion = aggregateVersion;
    }

    /**
     * 事件 ID。
     *
     * @return 字段值
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * 事件类型。
     *
     * @return 字段值
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * 聚合类型。
     *
     * @return 字段值
     */
    public String getAggregateType() {
        return aggregateType;
    }

    /**
     * 聚合标识。
     *
     * @return 字段值
     */
    public String getAggregateId() {
        return aggregateId;
    }

    /**
     * 聚合版本。
     *
     * @return 字段值
     */
    public long getAggregateVersion() {
        return aggregateVersion;
    }
}
