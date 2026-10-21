package com.paytech.reconciliation.service.handler;

import java.time.Instant;
import org.springframework.stereotype.Component;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.messaging.api.DomainEventPublisher;
import com.paytech.reconciliation.api.event.ReconAdjustmentApprovedPayload;
import com.paytech.reconciliation.core.configuration.ReconciliationEventProperties;
import com.paytech.reconciliation.domain.ReconciliationEventPayloads;
import com.paytech.reconciliation.domainservice.service.ReconciliationEventQueryService;
import tools.jackson.databind.node.ObjectNode;

/**
 * 平台流水、预期银行流水、银行流水的 CLASSIFY 处置批准后发布 ReconAdjustmentApproved（technique §2.4，决定 I-03，AC-11）
 * <li>事件键 {@code RECON_RESOLUTION:<resolutionId>:ReconAdjustmentApproved:1}；与处置批准在同一本地事务登记。</li>
 * <li>载荷从已应用的处置与所属异常重建；渠道账单行的归类不发本事件。</li>
 */
@Component
public class ReconAdjustmentApprovedHandler extends AbstractEventPublishHandler<ReconAdjustmentApprovedPayload> {
    /** 补偿事件类型。 */
    public static final String EVENT_TYPE = "RECON_ADJUSTMENT_APPROVED";

    private final ReconciliationEventQueryService reconciliationEventQueryService;

    /** 以发布器、信封配置与载荷查询构造。 */
    public ReconAdjustmentApprovedHandler(DomainEventPublisher domainEventPublisher,
        ReconciliationEventProperties eventProperties, ReconciliationEventQueryService reconciliationEventQueryService) {
        super(domainEventPublisher, eventProperties);
        this.reconciliationEventQueryService = reconciliationEventQueryService;
    }

    /**
     * 纯工厂：只能与处置批准在同一本地事务登记
     *
     * @param resolutionId 处置主键
     * @return 登记请求
     */
    public static CompensationEventRequest buildEvent(long resolutionId) {
        return request(EVENT_TYPE, ReconAdjustmentApprovedPayload.AGGREGATE_TYPE, resolutionId,
            ReconAdjustmentApprovedPayload.EVENT_TYPE);
    }

    /** 补偿事件类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 领域事件类型。 */
    @Override
    protected String domainEventType() {
        return ReconAdjustmentApprovedPayload.EVENT_TYPE;
    }

    /** Schema 版本。 */
    @Override
    protected int schemaVersion() {
        return ReconAdjustmentApprovedPayload.SCHEMA_VERSION;
    }

    /** 聚合类型：异常处置。 */
    @Override
    protected String aggregateType() {
        return ReconAdjustmentApprovedPayload.AGGREGATE_TYPE;
    }

    /** 重建载荷；处置不存在、未应用或不是需发事件的归类时为空。 */
    @Override
    protected ReconAdjustmentApprovedPayload loadPayload(long aggregateId) {
        return reconciliationEventQueryService.reconAdjustmentApproved(aggregateId);
    }

    /** 载荷 JSON。 */
    @Override
    protected ObjectNode json(ReconAdjustmentApprovedPayload payload) {
        return ReconciliationEventPayloads.json(payload);
    }

    /** occurredAt 为批准时间。 */
    @Override
    protected Instant occurredAt(ReconAdjustmentApprovedPayload payload) {
        return payload.getApprovedTime();
    }
}
