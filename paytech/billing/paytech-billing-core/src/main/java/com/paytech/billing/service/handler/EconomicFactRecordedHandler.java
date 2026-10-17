package com.paytech.billing.service.handler;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.billing.core.configuration.BillingEventProperties;
import com.paytech.billing.domain.EconomicFactContext;
import com.paytech.billing.domain.EconomicFactPayloads;
import com.paytech.billing.domainservice.service.EconomicFactPublicationService;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.messaging.api.AggregateKey;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventPublisher;

import lombok.AllArgsConstructor;

/**
 * 经济事实批准提交后发布 EconomicFactRecorded（design/04 §2、§5）
 * <li>eventKey 为 {@code AggregateKey.eventKey}：{@code ECONOMIC_FACT:<factId>:EconomicFactRecorded:<factVersion>}；Handler 从事实头与
 * 全部明细行重建 Payload，补偿记录不保存业务内容。</li>
 * <li>经 {@link DomainEventPublisher} 发布到 billing.events，收到 Broker 确认后写 published_at；发布前后崩溃都只会重复发布同一 eventId，
 * 消费方按 eventId 与 factId + factVersion 幂等。</li>
 * <li>事实不存在、未批准或版本不符为终态失败，保留记录供人工处理；Broker 未确认或连接失败可重试。</li>
 * <li>事实在发布前已被替代（SUPERSEDED）仍按原版本发布，消费方依次收到版本 1 与替代它的版本 2。</li>
 */
@Component
@AllArgsConstructor
public class EconomicFactRecordedHandler implements CompensationEventHandler {
    /** 补偿事件类型。 */
    public static final String EVENT_TYPE = "ECONOMIC_FACT_RECORDED";

    /** 无业务子类型的占位值。 */
    static final String NO_SUB_EVENT_TYPE = "-";

    private final Clock clock;
    private final DomainEventPublisher domainEventPublisher;
    private final BillingEventProperties eventProperties;
    private final EconomicFactPublicationService publicationService;

    /**
     * 纯工厂：只能与事实批准在同一本地事务登记
     *
     * @param factId 经济事实 ID
     * @param factVersion 批准时定稿的事实版本
     * @return 登记请求
     */
    public static CompensationEventRequest buildEvent(long factId, long factVersion) {
        if (factId <= 0 || factVersion <= 0) {
            throw new IllegalArgumentException("Invalid economic fact identity");
        }
        String eventKey = AggregateKey.of(EconomicFactPayload.AGGREGATE_TYPE, Long.toString(factId))
            .eventKey(EconomicFactPayload.EVENT_TYPE, factVersion);
        return new CompensationEventRequest(eventKey, EVENT_TYPE, NO_SUB_EVENT_TYPE);
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
     * 重建并发布经济事实事件
     * <li>必须在事务外执行；事件键格式非法或事实不可发布为终态失败，发布失败可重试。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("ECONOMIC_FACT_TRANSACTION_ACTIVE",
                "Economic fact must publish outside a transaction");
        }
        AggregateKey.ParsedEventKey key = parse(event);
        if (key == null) {
            return CompensationHandlerResult.terminalFailure("ECONOMIC_FACT_EVENT_INVALID",
                "Economic fact event is malformed");
        }
        EconomicFactContext context = publicationService.loadContext(Long.parseLong(key.aggregateKey().aggregateId()));
        if (!publishable(context, key.aggregateVersion())) {
            return CompensationHandlerResult.terminalFailure("ECONOMIC_FACT_NOT_PUBLISHABLE",
                "Economic fact is not an approved fact of this version");
        }
        try {
            domainEventPublisher.publish(envelope(context, eventProperties));
        } catch (RuntimeException unavailable) {
            // 不把 Broker 返回信息写入任务错误摘要。
            return CompensationHandlerResult.retryableFailure("ECONOMIC_FACT_UNPUBLISHED",
                "Economic fact publication requires retry");
        }
        publicationService.markPublished(context, clock.instant().truncatedTo(ChronoUnit.MICROS));
        return CompensationHandlerResult.success();
    }

    /** 解析并核对事件键：聚合类型、事件类型与十进制事实 ID；不符合返回空。 */
    static AggregateKey.ParsedEventKey parse(CompensationEvent event) {
        if (event.payloadJson() != null || !NO_SUB_EVENT_TYPE.equals(event.subEventType())) {
            return null;
        }
        try {
            AggregateKey.ParsedEventKey key = AggregateKey.parseEventKey(event.eventKey());
            boolean expected = EconomicFactPayload.AGGREGATE_TYPE.equals(key.aggregateKey().aggregateType())
                && EconomicFactPayload.EVENT_TYPE.equals(key.eventType())
                && key.aggregateKey().aggregateId().matches("[1-9][0-9]{0,18}");
            return expected ? key : null;
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /** 事实存在、已批准（含随后被替代）且版本等于事件键版本。 */
    static boolean publishable(EconomicFactContext context, long factVersion) {
        return context != null && context.recorded() && context.getFact().getFactVersion() == factVersion;
    }

    /** EconomicFactRecorded Schema v1 信封；eventId 默认取四段式事件键，补偿重试不变。 */
    static DomainEvent envelope(EconomicFactContext context, BillingEventProperties properties) {
        EconomicFactPayload payload = context.toPayload();
        return DomainEvent.builder().eventType(EconomicFactPayload.EVENT_TYPE)
            .schemaVersion(EconomicFactPayload.SCHEMA_VERSION)
            .aggregate(EconomicFactPayload.AGGREGATE_TYPE, Long.toString(payload.getFactId()))
            .aggregateVersion(payload.getFactVersion()).occurredAt(payload.getRecordedTime())
            .sourceService(properties.getSourceService()).instanceId(properties.getInstanceId())
            .payload(EconomicFactPayloads.json(payload)).build();
    }
}
