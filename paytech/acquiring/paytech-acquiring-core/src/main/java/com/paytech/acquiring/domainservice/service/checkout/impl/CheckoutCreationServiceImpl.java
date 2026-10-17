package com.paytech.acquiring.domainservice.service.checkout.impl;

import org.springframework.stereotype.Service;

import com.paytech.acquiring.domain.checkout.CreateCheckoutContext;
import com.paytech.acquiring.domainservice.repository.checkout.CheckoutSessionRepository;
import com.paytech.acquiring.domainservice.repository.customer.ConsumerContractRepository;
import com.paytech.acquiring.domainservice.repository.order.*;
import com.paytech.acquiring.domainservice.service.checkout.CheckoutCreationService;
import com.paytech.acquiring.service.transaction.CommerceTransactionExecutor;

import lombok.AllArgsConstructor;

/** 订单与商业、法律及合同事实在同一本库事务中原子保存。 */
@Service
@AllArgsConstructor
public class CheckoutCreationServiceImpl implements CheckoutCreationService {
    private final OrderContextRepository contextRepository;
    private final OrderLegalRepository legalRepository;
    private final ConsumerContractRepository contractRepository;
    private final OrderItemRepository orderItemRepository;
    private final CommerceOrderRepository commerceOrderRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final CommerceTransactionExecutor transactionExecutor;
    private final OrderRefundPositionRepository orderRefundPositionRepository;
    private final OrderFundsAvailabilityProjectionRepository orderFundsAvailabilityProjectionRepository;

    /** 保存本次 Context；SQL 失败时全部事实整体回滚。 */
    @Override
    public void saveCreateCheckoutContext(CreateCheckoutContext context) {
        transactionExecutor.write(() -> {
            commerceOrderRepository.insert(context.getOrder());
            context.getItems().forEach(orderItemRepository::insert);
            orderRefundPositionRepository.insert(context.getRefundPosition());
            orderFundsAvailabilityProjectionRepository.insert(context.getFundsAvailability());
            checkoutSessionRepository.insert(context.getSession());
            contextRepository.insert(context.getFrozen());
            legalRepository.insert(context.getFrozen());
            contractRepository.insert(context.getFrozen());
            return null;
        });
    }

}
