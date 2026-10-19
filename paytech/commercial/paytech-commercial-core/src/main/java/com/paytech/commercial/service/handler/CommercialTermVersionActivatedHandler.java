package com.paytech.commercial.service.handler;

import java.time.Duration;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.commercial.api.event.CommercialTermVersionActivatedPayload;
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
 * 商业条款版本生效提交后发布 CommercialTermVersionActivated（technique.md §2.8）
 * <li>调用方在版本激活的同一本地事务内用 {@link #buildEvent(long, long)} 与 CompensationEventRegistrar 登记任务；Handler 在提交后
 * 从方案、唯一 ACTIVE 版本与 COMMERCIAL_TERM_PLAN 根修订重建 Payload，补偿记录不保存业务内容。</li>
 * <li>经 {@link DomainEventPublisher} 发布到 commercial.events，Broker 确认后返回成功；重复执行只会重发同一 eventId。</li>
 * <li>事件键非法、方案不存在、根修订已不等于事件版本或没有唯一 ACTIVE 版本为终态失败；Broker 未确认或连接失败可重试。</li>
 */
@AllArgsConstructor
public class CommercialTermVersionActivatedHandler implements CompensationEventHandler {
    /** 补偿事件类型，登记于 compensation-event-catalog.yaml。 */
    public static final String EVENT_TYPE = "COMMERCIAL_TERM_VERSION_ACTIVATED";

    private final DomainEventPublisher domainEventPublisher;
    private final CommercialEventProperties eventProperties;
    private final CommercialEventPublicationService publicationService;

    /**
     * 纯工厂：只能与版本激活在同一本地事务登记
     *
     * @param planId 方案 ID
     * @param changeVersion 本次激活提交后的 COMMERCIAL_TERM_PLAN 根修订版本
     * @return 登记请求
     */
    public static CompensationEventRequest buildEvent(long planId, long changeVersion) {
        return CommercialEventKeys.request(CommercialTermVersionActivatedPayload.AGGREGATE_TYPE,
            CommercialTermVersionActivatedPayload.EVENT_TYPE, EVENT_TYPE, planId, changeVersion);
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
     * 重建并发布商业条款版本生效事件
     * <li>必须在事务外执行；事件键非法或方案不可发布为终态失败，发布失败可重试。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("TERM_VERSION_TRANSACTION_ACTIVE",
                "Term version event must publish outside a transaction");
        }
        AggregateKey.ParsedEventKey key = CommercialEventKeys.parse(event,
            CommercialTermVersionActivatedPayload.AGGREGATE_TYPE, CommercialTermVersionActivatedPayload.EVENT_TYPE);
        if (key == null) {
            return CompensationHandlerResult.terminalFailure("TERM_VERSION_EVENT_INVALID",
                "Term version event is malformed");
        }
        CommercialTermVersionActivatedPayload payload = publicationService.loadTermVersionActivated(
            Long.parseLong(key.aggregateKey().aggregateId()), key.aggregateVersion());
        if (payload == null) {
            return CompensationHandlerResult.terminalFailure("TERM_VERSION_NOT_PUBLISHABLE",
                "Term plan is not at the event change version");
        }
        try {
            domainEventPublisher.publish(CommercialEventKeys.envelope(CommercialTermVersionActivatedPayload.EVENT_TYPE,
                CommercialTermVersionActivatedPayload.SCHEMA_VERSION, key, payload.getOccurredAt(),
                CommercialEventPayloads.json(payload), eventProperties));
        } catch (RuntimeException unavailable) {
            // 不把 Broker 返回信息写入任务错误摘要。
            return CompensationHandlerResult.retryableFailure("TERM_VERSION_UNPUBLISHED",
                "Term version event publication requires retry");
        }
        return CompensationHandlerResult.success();
    }
}
