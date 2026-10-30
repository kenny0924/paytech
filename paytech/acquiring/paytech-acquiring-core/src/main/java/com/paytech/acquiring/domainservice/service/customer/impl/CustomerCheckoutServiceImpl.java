package com.paytech.acquiring.domainservice.service.customer.impl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import com.paytech.acquiring.domain.customer.SaveConsumerContext;
import com.paytech.acquiring.domainservice.repository.customer.ConsumerContextRepository;
import com.paytech.acquiring.domainservice.repository.customer.ConsumerContractRepository;
import com.paytech.acquiring.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.acquiring.domainservice.repository.order.OrderTaxCalculationRepository;
import com.paytech.acquiring.domainservice.service.customer.CustomerCheckoutService;
import com.paytech.acquiring.service.transaction.CommerceTransactionExecutor;

import lombok.AllArgsConstructor;

/** 消费者本地资料写入；外部保护、tax 重算与合同渲染由 Processor 在事务外完成。 */
@Service
@AllArgsConstructor
public class CustomerCheckoutServiceImpl implements CustomerCheckoutService {
    private final Clock clock;
    private final CommerceOrderRepository orderRepository;
    private final ConsumerContextRepository consumerRepository;
    private final ConsumerContractRepository contractRepository;
    private final CommerceTransactionExecutor transactions;
    private final OrderTaxCalculationRepository taxCalculationRepository;

    /**
     * REQUIRED 本地事务保存订单版本、消费者资料与新报价；独立调用时也具备原子性
     * <li>有新计算时同一事务追加计算记录、把订单当前报价改为该计算并追加绑定该计算的合同；订单已有消费者接受时条件更新失败，整体回滚。</li>
     */
    @Override
    public void saveConsumerContext(SaveConsumerContext context) {
        transactions.write(() -> {
            var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            context.getOrder().checkExpiry(now);
            orderRepository.recordConsumerChange(context.getOrder(), context.getSession(), context.getQuote(), now);
            consumerRepository.saveConsumer(context.getConsumer(), context.getDetails(), context.getProtectedValues(), now);
            if (context.getQuote() != null) {
                taxCalculationRepository.insert(context.getQuote());
                contractRepository.insert(context.getContract());
            }
            return null;
        });
    }
}
