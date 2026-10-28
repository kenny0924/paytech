package com.paytech.commercial.service.handler;

import java.time.Duration;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.commercial.api.event.AgreementCapabilityStatusChangedPayload;
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
 * 开通项执行状态变化提交后发布 AgreementCapabilityStatusChanged（开通项前置门禁 technique.md §11.1）
 * <li>门禁、运营暂停 / 恢复、新增、关闭 / 重新纳入在执行状态变化的同一本地事务用 {@link #buildEvent(long, long)} 登记；Handler 提交后按
 * 本版本与上一版本的开通项 BCR 快照重建 changes[]，补偿记录不保存业务内容。快照不可变，所以合同后来又被修改也能重建本版本事件。</li>
 * <li>经 {@link DomainEventPublisher} 发布到 commercial.events，Broker 确认后返回成功；重复执行只会重发同一 eventId。</li>
 * <li>事件键非法、合同不存在或该版本没有执行状态变化为终态失败；Broker 未确认或连接失败可重试。</li>
 */
@AllArgsConstructor
public class AgreementCapabilityStatusChangedHandler implements CompensationEventHandler {
    /** 补偿事件类型，登记于 compensation-event-catalog.yaml。 */
    public static final String EVENT_TYPE = "AGREEMENT_CAPABILITY_STATUS_CHANGED";

    private final DomainEventPublisher domainEventPublisher;
    private final CommercialEventProperties eventProperties;
    private final CommercialEventPublicationService publicationService;

    /**
     * 纯工厂：只能与开通项执行状态变化在同一本地事务登记
     *
     * @param agreementId 合同 ID
     * @param changeVersion 本次变化提交后的 AGREEMENT 根修订版本
     * @return 登记请求
     */
    public static CompensationEventRequest buildEvent(long agreementId, long changeVersion) {
        return CommercialEventKeys.request(AgreementCapabilityStatusChangedPayload.AGGREGATE_TYPE,
            AgreementCapabilityStatusChangedPayload.EVENT_TYPE, EVENT_TYPE, agreementId, changeVersion);
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
     * 重建并发布开通项执行状态变更事件
     * <li>必须在事务外执行；事件键非法或该版本不可发布为终态失败，发布失败可重试。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("CAPABILITY_STATUS_TRANSACTION_ACTIVE",
                "Capability status event must publish outside a transaction");
        }
        AggregateKey.ParsedEventKey key = CommercialEventKeys.parse(event, AgreementCapabilityStatusChangedPayload.AGGREGATE_TYPE,
            AgreementCapabilityStatusChangedPayload.EVENT_TYPE);
        if (key == null) {
            return CompensationHandlerResult.terminalFailure("CAPABILITY_STATUS_EVENT_INVALID",
                "Capability status event is malformed");
        }
        AgreementCapabilityStatusChangedPayload payload = publicationService.loadAgreementCapabilityStatusChanged(
            Long.parseLong(key.aggregateKey().aggregateId()), key.aggregateVersion());
        if (payload == null) {
            return CompensationHandlerResult.terminalFailure("CAPABILITY_STATUS_NOT_PUBLISHABLE",
                "Agreement has no capability execution change at the event version");
        }
        try {
            domainEventPublisher.publish(CommercialEventKeys.envelope(AgreementCapabilityStatusChangedPayload.EVENT_TYPE,
                AgreementCapabilityStatusChangedPayload.SCHEMA_VERSION, key, payload.getOccurredAt(),
                CommercialEventPayloads.json(payload), eventProperties));
        } catch (RuntimeException unavailable) {
            // 不把 Broker 返回信息写入任务错误摘要。
            return CompensationHandlerResult.retryableFailure("CAPABILITY_STATUS_UNPUBLISHED",
                "Capability status event publication requires retry");
        }
        return CompensationHandlerResult.success();
    }
}
