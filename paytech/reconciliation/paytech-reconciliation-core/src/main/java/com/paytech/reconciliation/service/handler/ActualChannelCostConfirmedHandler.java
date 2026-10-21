package com.paytech.reconciliation.service.handler;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.messaging.api.DomainEventPublisher;
import com.paytech.reconciliation.api.event.ActualChannelCostConfirmedPayload;
import com.paytech.reconciliation.core.configuration.ReconciliationEventProperties;
import com.paytech.reconciliation.domain.ReconciliationEventPayloads;
import com.paytech.reconciliation.domainservice.service.ReconciliationEventQueryService;

import tools.jackson.databind.node.ObjectNode;

/**
 * 平台流水随账单对平转为 SETTLED 且实际渠道费 ≠ 预估时发布 ActualChannelCostConfirmed（technique D-05）
 * <li>事件键 {@code PLATFORM_TXN:<platformTxnId>:ActualChannelCostConfirmed:1}；每笔平台流水至多一次，与账单对平同一本地事务登记。</li>
 * <li>载荷从平台流水与覆盖它、最后对平的账单重建。</li>
 */
@Component
public class ActualChannelCostConfirmedHandler extends AbstractEventPublishHandler<ActualChannelCostConfirmedPayload> {
    /** 补偿事件类型。 */
    public static final String EVENT_TYPE = "ACTUAL_CHANNEL_COST_CONFIRMED";

    private final ReconciliationEventQueryService reconciliationEventQueryService;

    /** 以发布器、信封配置与载荷查询构造。 */
    public ActualChannelCostConfirmedHandler(DomainEventPublisher domainEventPublisher,
        ReconciliationEventProperties eventProperties, ReconciliationEventQueryService reconciliationEventQueryService) {
        super(domainEventPublisher, eventProperties);
        this.reconciliationEventQueryService = reconciliationEventQueryService;
    }

    /**
     * 纯工厂：只能与平台流水转为 SETTLED 在同一本地事务（账单对平）登记
     *
     * @param platformTxnId 平台流水主键
     * @return 登记请求
     */
    public static CompensationEventRequest buildEvent(long platformTxnId) {
        return request(EVENT_TYPE, ActualChannelCostConfirmedPayload.AGGREGATE_TYPE, platformTxnId,
            ActualChannelCostConfirmedPayload.EVENT_TYPE);
    }

    /** 补偿事件类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 领域事件类型。 */
    @Override
    protected String domainEventType() {
        return ActualChannelCostConfirmedPayload.EVENT_TYPE;
    }

    /** Schema 版本。 */
    @Override
    protected int schemaVersion() {
        return ActualChannelCostConfirmedPayload.SCHEMA_VERSION;
    }

    /** 聚合类型：平台流水。 */
    @Override
    protected String aggregateType() {
        return ActualChannelCostConfirmedPayload.AGGREGATE_TYPE;
    }

    /** 重建载荷；平台流水未 SETTLED 或费用无差额时为空。 */
    @Override
    protected ActualChannelCostConfirmedPayload loadPayload(long aggregateId) {
        return reconciliationEventQueryService.actualChannelCostConfirmed(aggregateId);
    }

    /** 载荷 JSON。 */
    @Override
    protected ObjectNode json(ActualChannelCostConfirmedPayload payload) {
        return ReconciliationEventPayloads.json(payload);
    }

    /** occurredAt 为确认时间（所在账单对平时间）。 */
    @Override
    protected Instant occurredAt(ActualChannelCostConfirmedPayload payload) {
        return payload.getConfirmedTime();
    }
}
