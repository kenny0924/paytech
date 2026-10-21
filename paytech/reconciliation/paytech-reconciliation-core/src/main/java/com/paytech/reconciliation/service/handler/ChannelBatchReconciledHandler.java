package com.paytech.reconciliation.service.handler;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.messaging.api.DomainEventPublisher;
import com.paytech.reconciliation.api.event.ChannelBatchReconciledPayload;
import com.paytech.reconciliation.core.configuration.ReconciliationEventProperties;
import com.paytech.reconciliation.domain.ReconciliationEventPayloads;
import com.paytech.reconciliation.domainservice.service.ReconciliationEventQueryService;

import tools.jackson.databind.node.ObjectNode;

/**
 * 渠道账单对平后发布 ChannelBatchReconciled（technique §2.4，决定 I-02）
 * <li>事件键 {@code CHANNEL_STATEMENT:<statementId>:ChannelBatchReconciled:1}；与账单对平在同一本地事务登记。</li>
 * <li>载荷从账单、行、已应用处置与预期银行流水重建：应收 / 应付 / 实际费用合计、已归类调整行、平台流水笔数。</li>
 */
@Component
public class ChannelBatchReconciledHandler extends AbstractEventPublishHandler<ChannelBatchReconciledPayload> {
    /** 补偿事件类型。 */
    public static final String EVENT_TYPE = "CHANNEL_BATCH_RECONCILED";

    private final ReconciliationEventQueryService reconciliationEventQueryService;

    /** 以发布器、信封配置与载荷查询构造。 */
    public ChannelBatchReconciledHandler(DomainEventPublisher domainEventPublisher,
        ReconciliationEventProperties eventProperties, ReconciliationEventQueryService reconciliationEventQueryService) {
        super(domainEventPublisher, eventProperties);
        this.reconciliationEventQueryService = reconciliationEventQueryService;
    }

    /**
     * 纯工厂：只能与账单对平在同一本地事务登记
     *
     * @param statementId 渠道账单主键
     * @return 登记请求
     */
    public static CompensationEventRequest buildEvent(long statementId) {
        return request(EVENT_TYPE, ChannelBatchReconciledPayload.AGGREGATE_TYPE, statementId,
            ChannelBatchReconciledPayload.EVENT_TYPE);
    }

    /** 补偿事件类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 领域事件类型。 */
    @Override
    protected String domainEventType() {
        return ChannelBatchReconciledPayload.EVENT_TYPE;
    }

    /** Schema 版本。 */
    @Override
    protected int schemaVersion() {
        return ChannelBatchReconciledPayload.SCHEMA_VERSION;
    }

    /** 聚合类型：渠道账单。 */
    @Override
    protected String aggregateType() {
        return ChannelBatchReconciledPayload.AGGREGATE_TYPE;
    }

    /** 重建载荷；账单未对平时为空。 */
    @Override
    protected ChannelBatchReconciledPayload loadPayload(long aggregateId) {
        return reconciliationEventQueryService.channelBatchReconciled(aggregateId);
    }

    /** 载荷 JSON。 */
    @Override
    protected ObjectNode json(ChannelBatchReconciledPayload payload) {
        return ReconciliationEventPayloads.json(payload);
    }

    /** occurredAt 为对平时间。 */
    @Override
    protected Instant occurredAt(ChannelBatchReconciledPayload payload) {
        return payload.getReconciledTime();
    }
}
