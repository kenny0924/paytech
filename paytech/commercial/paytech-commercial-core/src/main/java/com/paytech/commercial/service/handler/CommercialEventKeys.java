package com.paytech.commercial.service.handler;

import java.time.Instant;

import com.paytech.commercial.core.configuration.CommercialEventProperties;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.messaging.api.AggregateKey;
import com.paytech.foundation.messaging.api.DomainEvent;

import tools.jackson.databind.node.ObjectNode;

/**
 * 两个发布 Handler 共用的事件键与信封规则
 * <li>事件键为 {@code AggregateKey.eventKey}：{@code <aggregateType>:<aggregateId>:<eventType>:<changeVersion>}，同时作为信封
 * eventId，补偿重试不变；无业务子类型，sub_event_type 固定为 {@value #NO_SUB_EVENT_TYPE}，补偿记录不保存 Payload。</li>
 */
final class CommercialEventKeys {
    /** 无业务子类型的占位值。 */
    static final String NO_SUB_EVENT_TYPE = "-";

    /** 纯静态工具。 */
    private CommercialEventKeys() {
    }

    /**
     * 登记请求：聚合 ID 与修订版本必须为正
     *
     * @param aggregateType 事件聚合类型
     * @param eventType 业务事件类型
     * @param handlerEventType 补偿 Handler 类型
     * @param aggregateId 聚合 ID
     * @param changeVersion 根修订版本
     * @return 登记请求
     */
    static CompensationEventRequest request(String aggregateType, String eventType, String handlerEventType,
        long aggregateId, long changeVersion) {
        if (aggregateId <= 0 || changeVersion <= 0) {
            throw new IllegalArgumentException("Invalid commercial event identity");
        }
        String eventKey = AggregateKey.of(aggregateType, Long.toString(aggregateId)).eventKey(eventType, changeVersion);
        return new CompensationEventRequest(eventKey, handlerEventType, NO_SUB_EVENT_TYPE);
    }

    /**
     * 解析并核对事件键：聚合类型、事件类型、十进制正整数聚合 ID、占位子类型且不带 Payload；不符合返回空
     *
     * @param event 补偿任务
     * @param aggregateType 期望聚合类型
     * @param eventType 期望业务事件类型
     * @return 解析结果或 null
     */
    static AggregateKey.ParsedEventKey parse(CompensationEvent event, String aggregateType, String eventType) {
        if (event.payloadJson() != null || !NO_SUB_EVENT_TYPE.equals(event.subEventType())) {
            return null;
        }
        try {
            AggregateKey.ParsedEventKey key = AggregateKey.parseEventKey(event.eventKey());
            boolean expected = aggregateType.equals(key.aggregateKey().aggregateType())
                && eventType.equals(key.eventType()) && key.aggregateKey().aggregateId().matches("[1-9][0-9]{0,18}");
            return expected ? key : null;
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /**
     * Schema v1 信封；eventId 默认取四段式事件键
     *
     * @param eventType 业务事件类型
     * @param schemaVersion Schema 版本
     * @param key 已核对的事件键
     * @param occurredAt 业务发生时间
     * @param payload Payload JSON
     * @param properties 发布身份
     * @return 信封
     */
    static DomainEvent envelope(String eventType, int schemaVersion, AggregateKey.ParsedEventKey key, Instant occurredAt,
        ObjectNode payload, CommercialEventProperties properties) {
        return DomainEvent.builder().eventType(eventType).schemaVersion(schemaVersion).aggregate(key.aggregateKey())
            .aggregateVersion(key.aggregateVersion()).occurredAt(occurredAt).sourceService(properties.getSourceService())
            .instanceId(properties.getInstanceId()).payload(payload).build();
    }
}
