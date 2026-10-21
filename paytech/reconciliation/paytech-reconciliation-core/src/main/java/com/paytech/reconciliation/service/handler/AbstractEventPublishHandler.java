package com.paytech.reconciliation.service.handler;

import java.time.Duration;
import java.time.Instant;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.messaging.api.AggregateKey;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventPublisher;
import com.paytech.reconciliation.core.configuration.ReconciliationEventProperties;

import tools.jackson.databind.node.ObjectNode;

/**
 * 四类对账事件发布 Handler 的公共执行骨架（参照 account AccountPostedHandler；决定 I-01、I-08）
 * <li>事件键为 {@code AggregateKey.eventKey}：{@code <aggregateType>:<id>:<EventType>:1}，聚合版本固定为 1，无业务子类型、无 Payload；
 * 子类从本库权威事实重建载荷。</li>
 * <li>经 {@link DomainEventPublisher} 发布到 reconciliation.events，Broker 确认后才算成功；未路由按 FAIL 策略视为发布失败，可重试，
 * 达到上限后保留 FAILED 待人工补发。事实不存在为终态失败。</li>
 *
 * @param <P> 载荷类型
 */
public abstract class AbstractEventPublishHandler<P> implements CompensationEventHandler {
    /** 无业务子类型的占位值。 */
    static final String NO_SUB_EVENT_TYPE = "-";

    /** 四类事件的聚合版本固定为 1（决定 I-01）。 */
    static final long AGGREGATE_VERSION = 1L;

    private final DomainEventPublisher domainEventPublisher;

    private final ReconciliationEventProperties eventProperties;

    /** 以发布器与信封身份配置构造。 */
    protected AbstractEventPublishHandler(DomainEventPublisher domainEventPublisher,
        ReconciliationEventProperties eventProperties) {
        this.domainEventPublisher = domainEventPublisher;
        this.eventProperties = eventProperties;
    }

    /**
     * 纯工厂：登记请求的事件键与身份
     *
     * @param compensationType 补偿事件类型
     * @param aggregateType 领域事件聚合类型
     * @param aggregateId 聚合 ID
     * @param domainEventType 领域事件类型
     * @return 登记请求
     */
    static CompensationEventRequest request(String compensationType, String aggregateType, long aggregateId,
        String domainEventType) {
        String eventKey = AggregateKey.of(aggregateType, String.valueOf(aggregateId)).eventKey(domainEventType,
            AGGREGATE_VERSION);
        return new CompensationEventRequest(eventKey, compensationType, NO_SUB_EVENT_TYPE);
    }

    /** 每次尝试 20 秒，最多 12 次，5 秒起指数退避至 30 分钟。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(20), 12, Duration.ofSeconds(5), Duration.ofMinutes(30),
            2, 0.2);
    }

    /**
     * 重建并发布事件
     * <li>必须在事务外执行；事件键非法或事实不存在为终态失败，发布失败可重试。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("RECON_EVENT_TRANSACTION_ACTIVE",
                "Reconciliation event must publish outside a transaction");
        }
        Long aggregateId = parse(event);
        if (aggregateId == null) {
            return CompensationHandlerResult.terminalFailure("RECON_EVENT_INVALID", "Reconciliation event is malformed");
        }
        P payload = loadPayload(aggregateId);
        if (payload == null) {
            return CompensationHandlerResult.terminalFailure("RECON_EVENT_SOURCE_NOT_FOUND",
                "Reconciliation event source does not exist");
        }
        try {
            domainEventPublisher.publish(envelope(aggregateId, payload));
        } catch (RuntimeException unavailable) {
            // 不把 Broker 返回信息写入任务错误摘要。
            return CompensationHandlerResult.retryableFailure("RECON_EVENT_UNPUBLISHED",
                "Reconciliation event publication requires retry");
        }
        return CompensationHandlerResult.success();
    }

    /** 解析并核对事件键：聚合类型、领域事件类型、版本 1、无子类型与 Payload；不符合返回空。 */
    Long parse(CompensationEvent event) {
        if (event.payloadJson() != null || !NO_SUB_EVENT_TYPE.equals(event.subEventType())) {
            return null;
        }
        try {
            AggregateKey.ParsedEventKey key = AggregateKey.parseEventKey(event.eventKey());
            boolean expected = aggregateType().equals(key.aggregateKey().aggregateType())
                && domainEventType().equals(key.eventType()) && key.aggregateVersion() == AGGREGATE_VERSION;
            return expected ? Long.valueOf(key.aggregateKey().aggregateId()) : null;
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /** Schema v1 信封；eventId 默认取事件键，补偿重试不变。 */
    DomainEvent envelope(long aggregateId, P payload) {
        return DomainEvent.builder().eventType(domainEventType()).schemaVersion(schemaVersion())
            .aggregate(aggregateType(), String.valueOf(aggregateId)).aggregateVersion(AGGREGATE_VERSION)
            .occurredAt(occurredAt(payload)).sourceService(eventProperties.getSourceService())
            .instanceId(eventProperties.getInstanceId()).payload(json(payload)).build();
    }

    /** 领域事件类型（事件目录登记名）。 */
    protected abstract String domainEventType();

    /** 载荷 Schema 版本。 */
    protected abstract int schemaVersion();

    /** 领域事件聚合类型。 */
    protected abstract String aggregateType();

    /** 从本库权威事实重建载荷；事实不存在或不再满足发布条件时返回空。 */
    protected abstract P loadPayload(long aggregateId);

    /** 载荷 JSON。 */
    protected abstract ObjectNode json(P payload);

    /** 事件 occurredAt。 */
    protected abstract Instant occurredAt(P payload);
}
