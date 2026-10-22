package com.paytech.payment.execution.ext.mq;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.event.ChannelPaymentNotifiedPayload;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.messaging.api.ConsumeTransaction;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventConsumer;
import com.paytech.foundation.messaging.api.DomainEventHandler;
import com.paytech.foundation.messaging.api.DomainEventRejectedException;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAttempt;
import com.paytech.payment.execution.service.PaymentExecutionService;
import com.paytech.payment.execution.service.processor.ConfirmPayResultProcessor;
import com.paytech.payment.execution.service.processor.request.ConfirmPayResultRequest;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * 消费 channel-connector 的 ChannelPaymentNotified（channel-connector.events → payment.channel-connector.hash →
 * payment.channel-connector.p0..p3，3DS 全链路 technique 3.3.5）：渠道支付通知只是“去查单”的信号，不采信其中的状态。
 *
 * <p>
 * paytech-messaging 先按 eventId 跳过重放，再调用本 Handler；本 Handler 调用必须在事务外执行的 {@link ConfirmPayResultProcessor}，故声明
 * {@link ConsumeTransaction#HANDLER_MANAGED}，重复投递由确认流程的终态 CAS 吸收（终态只写一次）。
 * <li>商户订单号不是支付尝试 ID、尝试不存在、或冻结的渠道与账号与通知不一致：抛出 {@link DomainEventRejectedException} 进入死信队列
 * payment.channel-connector.dlq 并告警，不影响交易（查单恢复任务兜底）。</li>
 * <li>Intent 正被其他请求处理：确认消费，由仍持锁的一方或查单恢复任务推进；其他失败原样抛出，由 paytech-messaging 重新投递。</li>
 * <li>日志只记录事件与尝试 ID，不记录 Payload。</li>
 */
@Slf4j
@Component
@AllArgsConstructor
@DomainEventHandler(eventType = ChannelPaymentNotifiedPayload.EVENT_TYPE, strictOrder = false,
    transaction = ConsumeTransaction.HANDLER_MANAGED)
public class ChannelPaymentNotifiedListener implements DomainEventConsumer {
    private final PaymentExecutionService paymentExecutionService;
    private final ConfirmPayResultProcessor confirmPayResultProcessor;

    @Override
    public void consume(DomainEvent event) {
        PaymentAttempt attempt = getAndCheckAttempt(event);
        try {
            var response = confirmPayResultProcessor
                .process(new ConfirmPayResultRequest(attempt.getPaymentIntentId(), attempt.getPaymentAttemptId()));
            log.info("渠道支付通知已触发确认: eventId={}, attemptId={}, confirmation={}", event.eventId(),
                attempt.getPaymentAttemptId(), response.getConfirmation().getCode());
        } catch (ConcurrentRequestException busy) {
            log.info("渠道支付通知对应的 Intent 正在处理，确认消费: eventId={}, attemptId={}", event.eventId(),
                attempt.getPaymentAttemptId());
        }
    }

    /** 商户订单号定位支付尝试，并核对冻结的渠道与账号；任何不一致都拒绝进入死信。 */
    PaymentAttempt getAndCheckAttempt(DomainEvent event) {
        JsonNode payload = event.payload();
        String reference = text(payload, "merchantReference");
        if (reference == null || !reference.matches("[1-9][0-9]{0,18}")) {
            throw rejected(event, "商户订单号不是支付尝试 ID");
        }
        PaymentAttempt attempt = paymentExecutionService.findAttempt(Long.parseLong(reference));
        if (attempt == null) {
            throw rejected(event, "支付尝试不存在");
        }
        if (!Objects.equals(String.valueOf(attempt.getChannelId()), text(payload, "channelId"))
            || !Objects.equals(String.valueOf(attempt.getChannelAccountId()), text(payload, "channelAccountId"))) {
            throw rejected(event, "通知渠道或账号与支付尝试冻结坐标不一致");
        }
        return attempt;
    }

    /** 字符串字段；不存在或类型不符为 null。 */
    static String text(JsonNode payload, String field) {
        JsonNode value = payload.path(field);
        return value.isString() ? value.asString() : null;
    }

    /** 拒绝异常：只带安全上下文。 */
    static DomainEventRejectedException rejected(DomainEvent event, String reason) {
        log.warn("渠道支付通知被拒绝进入死信: eventId={}, reason={}", event.eventId(), reason);
        return new DomainEventRejectedException("渠道支付通知被拒绝: " + event.eventId(), new ErrorException(reason));
    }
}
