package com.paytech.commercial.service.handler;

import java.time.Duration;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.commercial.api.event.AgreementStatusChangedPayload;
import com.paytech.commercial.core.configuration.CommercialEventProperties;
import com.paytech.commercial.domain.CommercialEventPayloads;
import com.paytech.commercial.domainservice.service.CommercialEventPublicationService;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.messaging.api.AggregateKey;
import com.paytech.foundation.messaging.api.DomainEventPublisher;

import lombok.AllArgsConstructor;

/**
 * 合同状态变更提交后发布 AgreementStatusChanged（technique.md §2.8）
 * <li>调用方在合同状态变更的同一本地事务内用 {@link #buildEvent(long, long)} 与 CompensationEventRegistrar 登记任务；Handler 在
 * 提交后从合同行与 AGREEMENT 根修订重建 Payload，补偿记录不保存业务内容。</li>
 * <li>经 {@link DomainEventPublisher} 发布到 commercial.events，Broker 确认后返回成功；重复执行只会重发同一 eventId，消费方按
 * eventId 与 changeVersion 幂等。</li>
 * <li>事件键非法、合同不存在或根修订已不等于事件版本为终态失败，保留记录供人工处理；Broker 未确认或连接失败可重试。</li>
 */
@AllArgsConstructor
public class AgreementStatusChangedHandler implements CompensationEventHandler {
    /** 补偿事件类型，登记于 compensation-event-catalog.yaml。 */
    public static final String EVENT_TYPE = "AGREEMENT_STATUS_CHANGED";

    private final DomainEventPublisher domainEventPublisher;
    private final CommercialEventProperties eventProperties;
    private final CommercialEventPublicationService publicationService;

    /**
     * 纯工厂：只能与合同状态变更在同一本地事务登记
     *
     * @param agreementId 合同 ID
     * @param changeVersion 本次变更提交后的 AGREEMENT 根修订版本
     * @return 登记请求
     */
    public static CompensationEventRequest buildEvent(long agreementId, long changeVersion) {
        return CommercialEventKeys.request(AgreementStatusChangedPayload.AGGREGATE_TYPE,
            AgreementStatusChangedPayload.EVENT_TYPE, EVENT_TYPE, agreementId, changeVersion);
    }

    /** 补偿事件类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 每次尝试 20 秒，最多 12 次，5 秒起指数退避至 30 分钟。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(20), 12, Duration.ofSeconds(5), Duration.ofMinutes(30),
            2, 0.2);
    }

    /**
     * 重建并发布合同状态变更事件
     * <li>必须在事务外执行；事件键非法或合同不可发布为终态失败，发布失败可重试。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("AGREEMENT_STATUS_TRANSACTION_ACTIVE",
                "Agreement status event must publish outside a transaction");
        }
        AggregateKey.ParsedEventKey key = CommercialEventKeys.parse(event, AgreementStatusChangedPayload.AGGREGATE_TYPE,
            AgreementStatusChangedPayload.EVENT_TYPE);
        if (key == null) {
            return CompensationHandlerResult.terminalFailure("AGREEMENT_STATUS_EVENT_INVALID",
                "Agreement status event is malformed");
        }
        AgreementStatusChangedPayload payload = publicationService.loadAgreementStatusChanged(
            Long.parseLong(key.aggregateKey().aggregateId()), key.aggregateVersion());
        if (payload == null) {
            return CompensationHandlerResult.terminalFailure("AGREEMENT_STATUS_NOT_PUBLISHABLE",
                "Agreement is not at the event change version");
        }
        try {
            domainEventPublisher.publish(CommercialEventKeys.envelope(AgreementStatusChangedPayload.EVENT_TYPE,
                AgreementStatusChangedPayload.SCHEMA_VERSION, key, payload.getOccurredAt(),
                CommercialEventPayloads.json(payload), eventProperties));
        } catch (RuntimeException unavailable) {
            // 不把 Broker 返回信息写入任务错误摘要。
            return CompensationHandlerResult.retryableFailure("AGREEMENT_STATUS_UNPUBLISHED",
                "Agreement status event publication requires retry");
        }
        return CompensationHandlerResult.success();
    }
}
