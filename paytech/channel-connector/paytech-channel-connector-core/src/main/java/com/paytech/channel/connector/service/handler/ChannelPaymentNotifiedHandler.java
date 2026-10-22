package com.paytech.channel.connector.service.handler;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.channel.connector.api.event.ChannelPaymentNotifiedPayload;
import com.paytech.channel.connector.core.configuration.ChannelEventProperties;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCallback;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCallbackItem;
import com.paytech.channel.connector.core.enums.CallbackMappingStatus;
import com.paytech.channel.connector.core.enums.CallbackSignatureStatus;
import com.paytech.channel.connector.core.enums.CallbackStatus;
import com.paytech.channel.connector.core.enums.CallbackTargetType;
import com.paytech.channel.connector.domainservice.service.ChannelCallbackService;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventPublisher;

import lombok.AllArgsConstructor;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 发布 ChannelPaymentNotified（3DS 全链路 technique 3.3.5）：渠道通知验签通过且映射到支付尝试时同事务登记，提交后执行。
 * <li>eventKey 是 channel_callback_id；Handler 从 t_channel_callback 与唯一的回调项重建事件，补偿记录不保存业务内容。</li>
 * <li>经 {@link DomainEventPublisher} 向 channel-connector.events 发布 Schema v1 信封（aggregateType=CHANNEL_CALLBACK、aggregateId=回调 ID、
 * aggregateVersion=1）；收到 Broker 确认且路由到订阅队列（payment.channel-connector.p0..p3）后把回调置为 PROCESSED。</li>
 * <li>Payload 不含通知原文、签名或金额；发布失败可重试，消费方按 eventId 防重。回调不是验签通过的已映射支付通知时为终态失败，转人工。</li>
 */
@Component
@AllArgsConstructor
public class ChannelPaymentNotifiedHandler implements CompensationEventHandler {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "CHANNEL_PAYMENT_NOTIFIED";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Clock clock;
    private final DomainEventPublisher domainEvents;
    private final ChannelEventProperties eventProperties;
    private final ChannelCallbackService channelCallbackService;

    /** 纯工厂：只能与回调入库在同一本地事务登记。 */
    public static CompensationEventRequest buildEvent(long callbackId) {
        if (callbackId <= 0) {
            throw new IllegalArgumentException("Invalid channel callback identity");
        }
        return new CompensationEventRequest(Long.toString(callbackId), EVENT_TYPE, "-");
    }

    /** 事件类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 每次 20 秒，最多 12 次，5 秒起指数退避至 30 分钟。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(20), 12, Duration.ofSeconds(5), Duration.ofMinutes(30), 2,
            0.2);
    }

    /**
     * 发布渠道支付通知
     * <li>回调不存在、未验签通过、没有唯一的已映射支付尝试回调项为终态失败；已是 PROCESSED 视为已发布。</li>
     * <li>Broker 未确认或连接失败可重试；发布后置 PROCESSED 遇到并发变化视为已由其他执行者完成。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("CHANNEL_PAYMENT_NOTIFIED_TRANSACTION_ACTIVE",
                "Channel notification must publish outside a transaction");
        }
        if (event.payloadJson() != null || !event.eventKey().matches("[1-9][0-9]{0,18}")) {
            return CompensationHandlerResult.terminalFailure("CHANNEL_PAYMENT_NOTIFIED_INVALID", "Channel notification event is malformed");
        }
        long callbackId = Long.parseLong(event.eventKey());
        ChannelCallback callback = channelCallbackService.findCallback(callbackId);
        ChannelCallbackItem item = mappedItem(callback, channelCallbackService.findItems(callbackId));
        if (item == null) {
            return CompensationHandlerResult.terminalFailure("CHANNEL_PAYMENT_NOTIFIED_INVALID", "Channel callback is not a mapped payment notice");
        }
        if (callback.getCallbackStatus() == CallbackStatus.PROCESSED) {
            return CompensationHandlerResult.success();
        }
        try {
            domainEvents.publish(envelope(callback, item, eventProperties));
        } catch (RuntimeException unavailable) {
            // 不把 Broker 返回信息写入任务错误摘要。
            return CompensationHandlerResult.retryableFailure("CHANNEL_PAYMENT_NOTIFIED_UNPUBLISHED", "Channel notification publication requires retry");
        }
        try {
            channelCallbackService.markPublished(callback, clock.instant().truncatedTo(ChronoUnit.MICROS));
        } catch (ConcurrentRequestException alreadyChanged) {
            return CompensationHandlerResult.success();
        }
        return CompensationHandlerResult.success();
    }

    /** 验签通过、状态为 RECEIVED / PROCESSED 且唯一回调项已映射到支付尝试时返回该回调项，否则为 null。 */
    static ChannelCallbackItem mappedItem(ChannelCallback callback, List<ChannelCallbackItem> items) {
        if (callback == null || callback.getSignatureStatus() != CallbackSignatureStatus.VERIFIED
            || !(callback.getCallbackStatus() == CallbackStatus.RECEIVED || callback.getCallbackStatus() == CallbackStatus.PROCESSED)
            || items.size() != 1) {
            return null;
        }
        ChannelCallbackItem item = items.getFirst();
        return item.getMappingStatus() == CallbackMappingStatus.MAPPED && item.getTargetType() == CallbackTargetType.PAYMENT_ATTEMPT
            && item.getTargetId() != null ? item : null;
    }

    /** ChannelPaymentNotified Schema v1 信封；eventId 默认取 AggregateKey 四段式事件键，补偿重试不变。 */
    static DomainEvent envelope(ChannelCallback callback, ChannelCallbackItem item, ChannelEventProperties properties) {
        return DomainEvent.builder().eventType(ChannelPaymentNotifiedPayload.EVENT_TYPE)
            .schemaVersion(ChannelPaymentNotifiedPayload.SCHEMA_VERSION)
            .aggregate(ChannelPaymentNotifiedPayload.AGGREGATE_TYPE, Long.toString(callback.getChannelCallbackId()))
            .aggregateVersion(ChannelPaymentNotifiedPayload.AGGREGATE_VERSION).occurredAt(callback.getReceivedAt())
            .sourceService(properties.getSourceService()).instanceId(properties.getInstanceId())
            .payload(payload(callback, item)).build();
    }

    /** Payload 按目录字段顺序；ID 为十进制字符串，时间为 UTC RFC 3339。 */
    static ObjectNode payload(ChannelCallback callback, ChannelCallbackItem item) {
        var payload = new ChannelPaymentNotifiedPayload();
        payload.setChannelId(Long.toString(callback.getChannelId()));
        payload.setChannelAccountId(Long.toString(callback.getChannelAccountId()));
        payload.setMerchantReference(Long.toString(item.getTargetId()));
        payload.setChannelTransactionRef(item.getExternalTransactionId());
        payload.setReportedStatus(item.getNormalizedStatus());
        payload.setReceivedAt(callback.getReceivedAt().toString());
        return JSON.valueToTree(payload);
    }
}
