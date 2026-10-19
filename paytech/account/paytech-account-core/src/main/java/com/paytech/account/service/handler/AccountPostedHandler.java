package com.paytech.account.service.handler;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.account.core.configuration.AccountEventProperties;
import com.paytech.account.domain.AccountPostedPayloads;
import com.paytech.account.domain.view.AccountPostingView;
import com.paytech.account.domainservice.service.AccountPostingQueryService;
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
 * 入账批次提交后发布 AccountPosted（technique 2.1「链路与事务」）
 * <li>eventKey 为 {@code AggregateKey.eventKey}：{@code ACCOUNT_POSTING:{OE|FE}.{entityId}.{currency}.{shardNo}:AccountPosted:<postingSequence>}；
 * Handler 从批次、流水、Owner 账户与来源记录重建 Payload，补偿记录不保存业务内容。</li>
 * <li>经 {@link DomainEventPublisher} 发布到 account.events，收到 Broker 确认后写 published_time；发布前后崩溃只会重复发布同一 eventId，
 * ledger 按聚合版本幂等。发布端按聚合串行（messaging.producer.sequencer），同一流的批次按序号顺序发布。</li>
 * <li>批次不存在为终态失败，保留记录供人工处理；Broker 未确认或连接失败可重试。策略同 billing BillingFactRecorded。</li>
 */
@Component
@AllArgsConstructor
public class AccountPostedHandler implements CompensationEventHandler {
    /** 补偿事件类型。 */
    public static final String EVENT_TYPE = "ACCOUNT_POSTED";

    /** 无业务子类型的占位值。 */
    static final String NO_SUB_EVENT_TYPE = "-";

    private final Clock clock;
    private final DomainEventPublisher domainEventPublisher;
    private final AccountEventProperties eventProperties;
    private final AccountPostingQueryService accountPostingQueryService;

    /**
     * 纯工厂：只能与入账批次在同一本地事务登记
     *
     * @param entityType 流主体类型
     * @param entityId 流主体标识
     * @param currency 流币种
     * @param shardNo 流分片号
     * @param postingSequence 批次序号
     * @return 登记请求
     */
    public static CompensationEventRequest buildEvent(AccountEntityType entityType, String entityId, String currency,
        int shardNo, long postingSequence) {
        String aggregateId = AccountPostedPayload.aggregateId(entityType, entityId, currency, shardNo);
        String eventKey = AggregateKey.of(AccountPostedPayload.AGGREGATE_TYPE, aggregateId)
            .eventKey(AccountPostedPayload.EVENT_TYPE, postingSequence);
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
     * 重建并发布入账批次事件
     * <li>必须在事务外执行；事件键格式非法或批次不存在为终态失败，发布失败可重试。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("ACCOUNT_POSTED_TRANSACTION_ACTIVE",
                "Account posting must publish outside a transaction");
        }
        AggregateKey.ParsedEventKey key = parse(event);
        if (key == null) {
            return CompensationHandlerResult.terminalFailure("ACCOUNT_POSTED_EVENT_INVALID",
                "Account posting event is malformed");
        }
        AccountPostedPayload.StreamKey stream = AccountPostedPayload.parseAggregateId(key.aggregateKey().aggregateId());
        AccountPostingView view = accountPostingQueryService.findPosting(stream.getEntityType(), stream.getEntityId(),
            stream.getCurrency(), stream.getShardNo(), key.aggregateVersion());
        if (view == null) {
            return CompensationHandlerResult.terminalFailure("ACCOUNT_POSTING_NOT_FOUND",
                "Account posting does not exist");
        }
        try {
            domainEventPublisher.publish(envelope(view, eventProperties));
        } catch (RuntimeException unavailable) {
            // 不把 Broker 返回信息写入任务错误摘要。
            return CompensationHandlerResult.retryableFailure("ACCOUNT_POSTED_UNPUBLISHED",
                "Account posting publication requires retry");
        }
        accountPostingQueryService.markPublished(view.getPosting(), clock.instant().truncatedTo(ChronoUnit.MICROS));
        return CompensationHandlerResult.success();
    }

    /** 解析并核对事件键：聚合类型、事件类型与合法的过账流聚合 ID；不符合返回空。 */
    static AggregateKey.ParsedEventKey parse(CompensationEvent event) {
        if (event.payloadJson() != null || !NO_SUB_EVENT_TYPE.equals(event.subEventType())) {
            return null;
        }
        try {
            AggregateKey.ParsedEventKey key = AggregateKey.parseEventKey(event.eventKey());
            AccountPostedPayload.parseAggregateId(key.aggregateKey().aggregateId());
            boolean expected = AccountPostedPayload.AGGREGATE_TYPE.equals(key.aggregateKey().aggregateType())
                && AccountPostedPayload.EVENT_TYPE.equals(key.eventType());
            return expected ? key : null;
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /** AccountPosted Schema v1 信封；eventId 默认取四段式事件键，补偿重试不变。 */
    static DomainEvent envelope(AccountPostingView view, AccountEventProperties properties) {
        AccountPostedPayload payload = AccountPostedPayloads.of(view);
        return DomainEvent.builder().eventType(AccountPostedPayload.EVENT_TYPE)
            .schemaVersion(AccountPostedPayload.SCHEMA_VERSION)
            .aggregate(AccountPostedPayload.AGGREGATE_TYPE, AccountPostedPayload.aggregateId(
                view.getPosting().getEntityType(), payload.getEntityId(), payload.getCurrency(), payload.getShardNo()))
            .aggregateVersion(payload.getPostingSequence()).occurredAt(payload.getPostedTime())
            .sourceService(properties.getSourceService()).instanceId(properties.getInstanceId())
            .payload(AccountPostedPayloads.json(payload)).build();
    }
}
