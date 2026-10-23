package com.paytech.billing.ext.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.billing.domain.OrderPaymentSucceededFact;
import com.paytech.billing.domain.view.BillingFactView;
import com.paytech.billing.service.processor.RecordPaymentFactProcessor;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.messaging.api.ConsumeTransaction;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventConsumer;
import com.paytech.foundation.messaging.api.DomainEventHandler;
import com.paytech.foundation.messaging.api.DomainEventRejectedException;

/**
 * 消费 acquiring OrderPaymentSucceeded（acquiring.events → billing.acquiring.hash → billing.acquiring.p0..p3），登记自动 PAYMENT 计费事实
 * <li>paytech-messaging 先按 eventId 跳过重放、按 aggregateVersion 跳过旧版本（strictOrder = false），再调用本 Handler；登记用例必须在事务外
 * 读取上游，故声明 {@link ConsumeTransaction#HANDLER_MANAGED}，业务幂等由来源事件键 + 载荷 Hash 保证。</li>
 * <li>信封不合法、事件内部或与上游事实不一致、定价不可表达（本地 {@link FailException}）：抛出 {@link DomainEventRejectedException}，直接进入
 * billing.acquiring.dlq。</li>
 * <li>并发冲突与其他失败（上游未就绪或不可用、日历缺失、数据库不可用等）：原样抛出，由 paytech-messaging 退避后重投，投递上限后进入 DLQ；
 * 上游补齐后由运维重放 DLQ 恢复（技术方案 4.8）。</li>
 * <li>日志只记事件 ID、支付尝试与结果事实 ID 或拒绝原因，不记载荷与金额。</li>
 */
@Component
@DomainEventHandler(eventType = OrderPaymentSucceededEventReader.EVENT_TYPE, strictOrder = false,
    transaction = ConsumeTransaction.HANDLER_MANAGED)
public class OrderPaymentSucceededBillingHandler implements DomainEventConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(OrderPaymentSucceededBillingHandler.class);

    private final RecordPaymentFactProcessor processor;

    /** 只依赖自动 PAYMENT 事实登记用例。 */
    public OrderPaymentSucceededBillingHandler(RecordPaymentFactProcessor processor) {
        this.processor = processor;
    }

    /** 读取信封后登记；本地业务拒绝转入 DLQ，并发冲突与系统失败交由消息重投。 */
    @Override
    public void consume(DomainEvent event) {
        OrderPaymentSucceededFact payment = OrderPaymentSucceededEventReader.read(event);
        try {
            BillingFactView fact = processor.process(payment);
            LOG.info("Order payment succeeded event {} recorded: attempt {}, fact {}", event.eventId(),
                payment.getPaymentAttemptId(), fact.getPayload().getFactId());
        } catch (ConcurrentRequestException conflict) {
            LOG.warn("Order payment succeeded event {} conflicted and will be redelivered: attempt {}", event.eventId(),
                payment.getPaymentAttemptId());
            throw conflict;
        } catch (FailException rejected) {
            LOG.warn("Order payment succeeded event {} rejected to dead letter: attempt {}, {}", event.eventId(),
                payment.getPaymentAttemptId(), rejected.getMessage());
            throw new DomainEventRejectedException("订单支付成功事件被拒绝: " + event.eventId(), rejected);
        }
    }
}
