package com.paytech.acquiring.domainservice.service.order.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.paytech.acquiring.domain.order.PaymentSucceededContext;
import com.paytech.acquiring.domain.webhook.ClientWebhookEvent;
import com.paytech.acquiring.domainservice.repository.checkout.CheckoutSessionRepository;
import com.paytech.acquiring.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.acquiring.domainservice.repository.order.OrderFundsAvailabilityProjectionRepository;
import com.paytech.acquiring.domainservice.repository.order.OrderPaymentProjectionRepository;
import com.paytech.acquiring.domainservice.repository.order.OrderRefundPositionRepository;
import com.paytech.acquiring.domainservice.service.order.OrderPaymentService;
import com.paytech.acquiring.ext.job.ClientWebhookSubmissionHandler;
import com.paytech.acquiring.service.transaction.CommerceTransactionExecutor;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;

import lombok.AllArgsConstructor;

/** 只保存已经核对的支付结果，不重复查询、不调用外部服务。 */
@Service
@AllArgsConstructor
public class OrderPaymentServiceImpl implements OrderPaymentService {
    private final Clock clock;
    private final CommerceOrderRepository commerceOrderRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final CommerceTransactionExecutor transactionExecutor;
    private final OrderRefundPositionRepository orderRefundPositionRepository;
    private final OrderPaymentProjectionRepository orderPaymentProjectionRepository;
    private final OrderFundsAvailabilityProjectionRepository orderFundsAvailabilityProjectionRepository;
    /** Registry 启动时需要创建全部 Handler，登记器在业务调用时取得，避免相互构造。 */
    private final ObjectProvider<CompensationEventRegistrar> registrars;

    /**
     * 五项事实与 order.paid Webhook 事件在一次 REQUIRED 事务完成；版本或状态已变时抛出冲突，不自动重试。
     * <li>只有首次进入 PAID 才到达这里；重复消息与已关闭订单的补记不登记事件。</li>
     */
    @Override
    public void applySucceeded(PaymentSucceededContext context) {
        transactionExecutor.write(() -> {
            var now = now();
            recordPayment(context, now);
            commerceOrderRepository.markPaid(context.getOrder(), context.getPaidAt(), now);
            if (context.getSession() != null) {
                checkoutSessionRepository.complete(context.getSession(), context.getPaidAt(), now);
            }
            orderRefundPositionRepository.establishBase(context.getRefundPosition(), context.getCapturedAmount(), now);
            orderFundsAvailabilityProjectionRepository.markPending(context.getOrder().getOrderId(),
                context.getOccurredAt(), now);
            registrars.getObject().register(ClientWebhookSubmissionHandler.buildEvent(
                ClientWebhookEvent.orderPaid(context.getOrder(), context.getCapturedAmount(), now)));
            return null;
        });
    }

    /** 单表条件更新同样走 REQUIRED 事务，保持与正常路径相同的边界。 */
    @Override
    public void recordClosedOrderPayment(PaymentSucceededContext context) {
        transactionExecutor.write(() -> {
            recordPayment(context, now());
            return null;
        });
    }

    /** 投影记录成功尝试、已捕获金额和事件幂等事实。 */
    void recordPayment(PaymentSucceededContext context, Instant now) {
        orderPaymentProjectionRepository.recordSucceeded(context.getPayment(), context.getAttemptId(),
            context.getCapturedAmount(), context.getEventKey(), context.getPayloadHash(),
            context.getAttemptSourceVersion(), context.getOccurredAt(), now);
    }

    /** 数据库 DATETIME(6) 精度的当前时间。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
