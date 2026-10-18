package com.paytech.account.ext.mq;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.paytech.account.domain.FactRecord;
import com.paytech.billing.api.fact.EconomicFactLinePayload;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.foundation.messaging.api.DomainEvent;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * EconomicFactRecorded Schema v1 信封到账户服务内部经济事实的解析（第 11a 步 Payload 合同）
 * <li>信封的聚合类型、聚合 ID 与版本必须与 Payload 的 factId、factVersion 一致；不一致或无法解析视为内容非法。</li>
 * <li>只做协议解析，不访问数据库；转换为本服务的 {@link FactRecord} 后由余额映射规则校验业务完整性。</li>
 */
public final class EconomicFactEventReader {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private EconomicFactEventReader() {
    }

    /**
     * 解析并核对信封
     *
     * @param event 已通过信封与哈希校验的事件
     * @return 账户服务内部的经济事实
     * @throws IllegalArgumentException Schema 版本、聚合身份不符、Payload 无法绑定或必填头字段缺失
     */
    public static FactRecord read(DomainEvent event) {
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
        return toRecord(fact, event.eventId());
    }

    /** 头字段 entityNo、currency、lines 必须存在；明细行按行号升序。 */
    static FactRecord toRecord(EconomicFactPayload fact, String eventId) {
        if (fact.getEntityNo() == null || fact.getCurrency() == null || fact.getLines() == null) {
            throw new IllegalArgumentException("EconomicFactRecorded Payload 缺少必填头字段: " + eventId);
        }
        List<FactRecord.Line> lines = new ArrayList<>();
        for (EconomicFactLinePayload line : fact.getLines()) {
            if (line == null || line.getLineNo() == null) {
                throw new IllegalArgumentException("EconomicFactRecorded 明细行缺少行号: " + eventId);
            }
            lines.add(new FactRecord.Line(line.getLineNo(), line.getComponentType(), line.getPayerRole(),
                line.getPayerId(), line.getPayeeRole(), line.getPayeeId(), line.getDirection(), line.getPostedAmount()));
        }
        lines.sort(Comparator.comparingInt(FactRecord.Line::lineNo));
        return new FactRecord(fact.getFactId(), fact.getFactVersion(), fact.getSupersedesFactId(), fact.getFactType(),
            fact.getClientId(), fact.getEntityNo(), fact.getLocalPartnerId(), fact.getFinanceEntityId(),
            fact.getCurrency(), fact.getGrossAmount(), fact.getChannelAccountId(), List.copyOf(lines));
    }
}
