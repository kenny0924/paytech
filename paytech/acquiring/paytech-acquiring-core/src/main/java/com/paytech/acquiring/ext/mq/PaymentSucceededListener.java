package com.paytech.acquiring.ext.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.acquiring.service.processor.order.ApplyPaymentSucceededProcessor;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.messaging.api.ConsumeTransaction;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventConsumer;
import com.paytech.foundation.messaging.api.DomainEventHandler;
import com.paytech.foundation.messaging.api.DomainEventRejectedException;

/**
 * 消费 Payment PaymentSucceeded 新信封（payment.events → acquiring.payment.hash → acquiring.payment.p0..p3，design/08 §3）
 * <li>paytech-messaging 先按 eventId 跳过重放、按 aggregateVersion 跳过旧版本，再调用本 Handler；本 Handler 调用必须在事务外执行的
 * Processor，故声明 {@link ConsumeTransaction#HANDLER_MANAGED}，业务幂等（事件键 + 载荷 Hash、成功尝试）保持现状。</li>
 * <li>信封内容不合法或业务明确拒绝：抛出 {@link DomainEventRejectedException}，直接进入死信队列 acquiring.payment.dlq。</li>
 * <li>并发冲突及其他失败（数据库暂不可用等）：原样抛出，由 paytech-messaging 退避后重新投递，投递上限后进入死信队列。</li>
 */
@Component
@DomainEventHandler(eventType = PaymentSucceededMessageConverter.EVENT_TYPE, strictOrder = false,
    transaction = ConsumeTransaction.HANDLER_MANAGED)
public class PaymentSucceededListener implements DomainEventConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentSucceededListener.class);

    private final ApplyPaymentSucceededProcessor processor;

    /** 只依赖支付成功落地用例。 */
    public PaymentSucceededListener(ApplyPaymentSucceededProcessor processor) {
        this.processor = processor;
    }

    /** 日志只记录事件 ID，不记录正文。 */
    @Override
    public void consume(DomainEvent event) {
        try {
            var result = processor.process(PaymentSucceededMessageConverter.convert(event));
            LOG.info("Payment succeeded event {} handled: {}", event.eventId(), result);
        } catch (ConcurrentRequestException conflict) {
            LOG.warn("Payment succeeded event {} conflicted and will be redelivered", event.eventId());
            throw conflict;
        } catch (IllegalArgumentException | FailException rejected) {
            LOG.warn("Payment succeeded event {} rejected to dead letter", event.eventId());
            throw new DomainEventRejectedException("支付成功事件被拒绝: " + event.eventId(), rejected);
        }
    }
}
