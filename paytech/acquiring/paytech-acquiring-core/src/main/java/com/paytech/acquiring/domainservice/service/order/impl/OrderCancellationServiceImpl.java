package com.paytech.acquiring.domainservice.service.order.impl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.paytech.acquiring.domain.order.OrderCancellationContext;
import com.paytech.acquiring.domain.webhook.ClientWebhookEvent;
import com.paytech.acquiring.domainservice.repository.checkout.CheckoutSessionRepository;
import com.paytech.acquiring.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.acquiring.domainservice.service.order.OrderCancellationService;
import com.paytech.acquiring.ext.job.ClientWebhookSubmissionHandler;
import com.paytech.acquiring.service.transaction.CommerceTransactionExecutor;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;

import lombok.AllArgsConstructor;

/** 只保存已经验证的本地事实，不查询完整订单、不调用外部服务。 */
@Service
@AllArgsConstructor
public class OrderCancellationServiceImpl implements OrderCancellationService {
    private final Clock clock;
    private final CommerceOrderRepository commerceOrderRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final CommerceTransactionExecutor transactionExecutor;
    /** Registry 启动时需要创建全部 Handler，登记器在业务调用时取得，避免相互构造。 */
    private final ObjectProvider<CompensationEventRegistrar> registrars;

    /** 单独提交终止意图，成功后 Processor 才能调用 Payment。 */
    @Override
    public void requestTermination(OrderCancellationContext context) {
        if (context.getOrder().getTerminationRequestedAt() != null) {
            return;
        }
        transactionExecutor.write(() -> {
            var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            commerceOrderRepository.requestTermination(context.getOrder(), now);
            return null;
        });
    }

    /**
     * 本地取消与 order.cancelled Webhook 事件在一次 REQUIRED 事务完成；状态或版本已变时抛出冲突，不自动重试。
     * <li>已取消订单在 Processor 中提前返回，不会重复登记事件。</li>
     */
    @Override
    public void cancel(OrderCancellationContext context) {
        transactionExecutor.write(() -> {
            var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            var order = context.getOrder();
            if (order.getTerminationRequestedAt() == null) {
                commerceOrderRepository.requestTermination(order, now);
            }
            commerceOrderRepository.cancel(order, context.getPayment(), context.canCancelLocally(), now);
            var session = context.getSession();
            if (session != null) {
                checkoutSessionRepository.cancel(session, now);
            }
            registrars.getObject()
                .register(ClientWebhookSubmissionHandler.buildEvent(ClientWebhookEvent.orderCancelled(order, session, now)));
            return null;
        });
    }
}
