package com.paytech.reconciliation.service.handler;

import java.time.Instant;
import org.springframework.stereotype.Component;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.messaging.api.DomainEventPublisher;
import com.paytech.reconciliation.api.event.ChannelSettlementConfirmedPayload;
import com.paytech.reconciliation.core.configuration.ReconciliationEventProperties;
import com.paytech.reconciliation.domain.ReconciliationEventPayloads;
import com.paytech.reconciliation.domainservice.service.ReconciliationEventQueryService;
import tools.jackson.databind.node.ObjectNode;

/**
 * 渠道预期银行流水的一条银行匹配确认后发布 ChannelSettlementConfirmed（technique §2.4，AC-10）
 * <li>事件键 {@code BANK_MATCH:<bankMatchId>:ChannelSettlementConfirmed:1}；与匹配确认在同一本地事务登记，按匹配 ID 幂等。</li>
 * <li>载荷从匹配、银行流水、预期银行流水、渠道账单与清算账户重建。</li>
 */
@Component
public class ChannelSettlementConfirmedHandler extends AbstractEventPublishHandler<ChannelSettlementConfirmedPayload> {
    /** 补偿事件类型。 */
    public static final String EVENT_TYPE = "CHANNEL_SETTLEMENT_CONFIRMED";

    private final ReconciliationEventQueryService reconciliationEventQueryService;

    /** 以发布器、信封配置与载荷查询构造。 */
    public ChannelSettlementConfirmedHandler(DomainEventPublisher domainEventPublisher,
        ReconciliationEventProperties eventProperties, ReconciliationEventQueryService reconciliationEventQueryService) {
        super(domainEventPublisher, eventProperties);
        this.reconciliationEventQueryService = reconciliationEventQueryService;
    }

    /**
     * 纯工厂：只能与匹配确认在同一本地事务登记
     *
     * @param bankMatchId 银行匹配主键
     * @return 登记请求
     */
    public static CompensationEventRequest buildEvent(long bankMatchId) {
        return request(EVENT_TYPE, ChannelSettlementConfirmedPayload.AGGREGATE_TYPE, bankMatchId,
            ChannelSettlementConfirmedPayload.EVENT_TYPE);
    }

    /** 补偿事件类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 领域事件类型。 */
    @Override
    protected String domainEventType() {
        return ChannelSettlementConfirmedPayload.EVENT_TYPE;
    }

    /** Schema 版本。 */
    @Override
    protected int schemaVersion() {
        return ChannelSettlementConfirmedPayload.SCHEMA_VERSION;
    }

    /** 聚合类型：银行匹配。 */
    @Override
    protected String aggregateType() {
        return ChannelSettlementConfirmedPayload.AGGREGATE_TYPE;
    }

    /** 重建载荷；匹配不存在、未确认或不是渠道预期流水时为空。 */
    @Override
    protected ChannelSettlementConfirmedPayload loadPayload(long aggregateId) {
        return reconciliationEventQueryService.channelSettlementConfirmed(aggregateId);
    }

    /** 载荷 JSON。 */
    @Override
    protected ObjectNode json(ChannelSettlementConfirmedPayload payload) {
        return ReconciliationEventPayloads.json(payload);
    }

    /** occurredAt 为确认时间。 */
    @Override
    protected Instant occurredAt(ChannelSettlementConfirmedPayload payload) {
        return payload.getConfirmedTime();
    }
}
