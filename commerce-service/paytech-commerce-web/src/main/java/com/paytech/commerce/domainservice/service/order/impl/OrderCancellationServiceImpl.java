package com.paytech.commerce.domainservice.service.order.impl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import com.paytech.commerce.domain.order.OrderCancellationContext;
import com.paytech.commerce.domainservice.repository.checkout.CheckoutSessionRepository;
import com.paytech.commerce.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.commerce.domainservice.service.order.OrderCancellationService;
import com.paytech.commerce.service.transaction.CommerceTransactionExecutor;

import lombok.AllArgsConstructor;

/** 只保存已经验证的本地事实，不查询完整订单、不调用外部服务。 */
@Service
@AllArgsConstructor
public class OrderCancellationServiceImpl implements OrderCancellationService {
    private final Clock clock;
    private final CommerceOrderRepository commerceOrderRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final CommerceTransactionExecutor transactionExecutor;

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

    /** 本地取消在一次 REQUIRED 事务完成；状态或版本已变时抛出冲突，不自动重试。 */
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
            return null;
        });
    }
}
