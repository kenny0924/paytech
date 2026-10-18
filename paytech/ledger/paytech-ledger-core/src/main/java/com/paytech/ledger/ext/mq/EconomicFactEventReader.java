package com.paytech.ledger.ext.mq;

import java.util.Objects;

import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.foundation.messaging.api.DomainEvent;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * EconomicFactRecorded Schema v1 信封到 {@link EconomicFactPayload} 的解析（第 11a 步 Payload 合同）
 * <li>信封的聚合类型、聚合 ID 与版本必须与 Payload 的 factId、factVersion 一致；不一致或无法解析视为内容非法。</li>
 * <li>只做协议解析，不访问数据库；业务完整性由过账引擎断言。</li>
 */
public final class EconomicFactEventReader {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private EconomicFactEventReader() {
    }

    /**
     * 解析并核对信封
     *
     * @param event 已通过信封与哈希校验的事件
     * @return 经济事实载荷
     * @throws IllegalArgumentException Schema 版本、聚合身份不符或 Payload 无法绑定
     */
    public static EconomicFactPayload read(DomainEvent event) {
        if (event.schemaVersion() != EconomicFactPayload.SCHEMA_VERSION
            || !EconomicFactPayload.AGGREGATE_TYPE.equals(event.aggregateType())) {
            throw new IllegalArgumentException("不支持的 EconomicFactRecorded 信封: schemaVersion=" + event.schemaVersion()
                + ", aggregateType=" + event.aggregateType());
        }
        EconomicFactPayload fact;
        try {
            fact = JSON.treeToValue(event.payload(), EconomicFactPayload.class);
        } catch (JacksonException invalid) {
            throw new IllegalArgumentException("EconomicFactRecorded Payload 无法解析: " + event.eventId(), invalid);
        }
        if (fact.getFactId() == null || !Objects.equals(String.valueOf(fact.getFactId()), event.aggregateId())
            || fact.getFactVersion() == null || fact.getFactVersion() != event.aggregateVersion()) {
            throw new IllegalArgumentException("EconomicFactRecorded 信封聚合身份与 Payload 不一致: " + event.eventId());
        }
        return fact;
    }
}
