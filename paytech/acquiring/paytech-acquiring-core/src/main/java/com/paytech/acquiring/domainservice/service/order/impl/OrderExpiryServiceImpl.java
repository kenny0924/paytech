package com.paytech.acquiring.domainservice.service.order.impl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.paytech.acquiring.domain.order.OrderExpiryContext;
import com.paytech.acquiring.domain.webhook.ClientWebhookEvent;
import com.paytech.acquiring.domainservice.repository.checkout.CheckoutSessionRepository;
import com.paytech.acquiring.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.acquiring.domainservice.repository.order.OrderPaymentProjectionRepository;
import com.paytech.acquiring.domainservice.service.order.OrderExpiryService;
import com.paytech.acquiring.ext.job.ClientWebhookSubmissionHandler;
import com.paytech.acquiring.service.transaction.CommerceTransactionExecutor;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;

import lombok.AllArgsConstructor;

/** 只保存已经验证的本地事实，不查询完整订单、不调用外部服务。 */
@Service
@AllArgsConstructor
public class OrderExpiryServiceImpl implements OrderExpiryService {
    private final Clock clock;
    private final CommerceOrderRepository commerceOrderRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final CommerceTransactionExecutor transactionExecutor;
    private final OrderPaymentProjectionRepository orderPaymentProjectionRepository;
    /** Registry 启动时需要创建全部 Handler，登记器在业务调用时取得，避免相互构造。 */
    private final ObjectProvider<CompensationEventRegistrar> registrars;

    /** 单独提交终止意图，成功后 Processor 才能调用 Payment。 */
    @Override
    public void requestTermination(OrderExpiryContext context) {
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
     * 本地过期与 order.expired Webhook 事件在一次 REQUIRED 事务完成；状态或版本已变时抛出冲突，不自动重试。
     * <li>订单 SQL 先以投影原版本确认无成功支付，再推进投影版本；已过期订单不会再被选中，不重复登记事件。</li>
     */
    @Override
    public void expire(OrderExpiryContext context) {
        transactionExecutor.write(() -> {
            var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            var order = context.getOrder();
            if (order.getTerminationRequestedAt() == null) {
                commerceOrderRepository.requestTermination(order, now);
            }
            commerceOrderRepository.expire(order, context.getPayment(), context.canExpireLocally(), now);
            var session = context.getSession();
            if (session != null) {
                checkoutSessionRepository.expire(session, now);
            }
            if (context.getPayment() != null) {
                orderPaymentProjectionRepository.recordTerminated(context.getPayment(), now);
            }
            registrars.getObject()
                .register(ClientWebhookSubmissionHandler.buildEvent(ClientWebhookEvent.orderExpired(order, session, now)));
            return null;
        });
    }
}
