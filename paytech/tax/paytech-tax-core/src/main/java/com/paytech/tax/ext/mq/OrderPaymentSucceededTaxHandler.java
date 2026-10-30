package com.paytech.tax.ext.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.messaging.api.ConsumeTransaction;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventConsumer;
import com.paytech.foundation.messaging.api.DomainEventHandler;
import com.paytech.foundation.messaging.api.DomainEventRejectedException;
import com.paytech.tax.api.enums.TaxCalculationStatus;
import com.paytech.tax.domain.registration.OrderPaymentTaxFact;
import com.paytech.tax.service.processor.RegisterTaxTransactionProcessor;

/**
 * 消费 acquiring OrderPaymentSucceeded（acquiring.events → tax.acquiring.hash → tax.acquiring.p0..p3），登记正式税务交易与底稿
 * （technique 9.2）
 * <li>paytech-messaging 先按 eventId 跳过重放、按 aggregateVersion 跳过旧版本（strictOrder = false），再调用本 Handler；登记在事务外
 * 确定期间与汇率，故声明 {@link ConsumeTransaction#HANDLER_MANAGED}，业务幂等由源事实身份 + 载荷 Hash 保证。</li>
 * <li>没有 taxCalculationId（存量订单）或 NOT_MANAGED：不建事实，正常返回（消费去重照常登记，AC-12、AC-04）。</li>
 * <li>信封不合法、已付款订单的计算不是 READY、事件与冻结计算不一致或同身份内容不同（本地 {@link FailException}）：抛出
 * {@link DomainEventRejectedException} 进入 tax.acquiring.dlq，并以 WARN 日志告警（AC-08）。</li>
 * <li>并发冲突与其他失败（数据库不可用等）原样抛出，由 paytech-messaging 退避重投，投递上限后进入 DLQ。</li>
 * <li>日志只记事件 ID、订单、计算与结果交易 ID 或拒绝原因，不记载荷与金额。</li>
 */
@Component
@DomainEventHandler(eventType = OrderPaymentSucceededEventReader.EVENT_TYPE, strictOrder = false,
    transaction = ConsumeTransaction.HANDLER_MANAGED)
public class OrderPaymentSucceededTaxHandler implements DomainEventConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(OrderPaymentSucceededTaxHandler.class);

    private final RegisterTaxTransactionProcessor processor;

    /** 只依赖正式交易登记用例。 */
    public OrderPaymentSucceededTaxHandler(RegisterTaxTransactionProcessor processor) {
        this.processor = processor;
    }

    /** 读取信封；不由平台计税的订单跳过，其余登记；本地业务拒绝转入 DLQ。 */
    @Override
    public void consume(DomainEvent event) {
        OrderPaymentTaxFact fact = OrderPaymentSucceededEventReader.read(event);
        if (fact.getTaxCalculationId() == null || TaxCalculationStatus.NOT_MANAGED.getCode().equals(fact.getTaxStatus())) {
            LOG.info("Order payment succeeded event {} skipped by tax: order {}, calculation {}", event.eventId(),
                fact.getOrderId(), fact.getTaxCalculationId());
            return;
        }
        try {
            long transactionId = processor.process(fact).getId();
            LOG.info("Order payment succeeded event {} registered by tax: order {}, calculation {}, transaction {}",
                event.eventId(), fact.getOrderId(), fact.getTaxCalculationId(), transactionId);
        } catch (ConcurrentRequestException conflict) {
            LOG.warn("Order payment succeeded event {} conflicted and will be redelivered: order {}", event.eventId(),
                fact.getOrderId());
            throw conflict;
        } catch (FailException rejected) {
            LOG.warn("ALERT tax registration rejected order payment succeeded event {} to dead letter: order {}, "
                + "calculation {}, {}", event.eventId(), fact.getOrderId(), fact.getTaxCalculationId(), rejected.getMessage());
            throw new DomainEventRejectedException("订单支付成功事件被 tax 拒绝: " + event.eventId(), rejected);
        }
    }
}
