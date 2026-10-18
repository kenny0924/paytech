package com.paytech.acquiring.domainservice.service.customer.impl;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import com.paytech.acquiring.domain.customer.SaveConsumerContext;
import com.paytech.acquiring.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.acquiring.domainservice.repository.customer.ConsumerContextRepository;
import com.paytech.acquiring.domainservice.service.customer.CustomerCheckoutService;
import com.paytech.acquiring.service.transaction.CommerceTransactionExecutor;
import lombok.AllArgsConstructor;
/** 消费者本地资料写入；外部保护由 Processor 在事务外完成。 */
@Service
@AllArgsConstructor
public class CustomerCheckoutServiceImpl implements CustomerCheckoutService {
    private final Clock clock;
    private final CommerceOrderRepository orderRepository;
    private final ConsumerContextRepository consumerRepository;
    private final CommerceTransactionExecutor transactions;
    /** REQUIRED 本地事务保存订单版本与消费者资料；独立调用时也具备原子性。 */
    @Override
    public void saveConsumerContext(SaveConsumerContext context) {
        transactions.write(() -> {
            var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            context.getOrder().checkExpiry(now);
            orderRepository.recordConsumerChange(context.getOrder(), context.getSession(), now);
            consumerRepository.saveConsumer(context.getConsumer(), context.getDetails(), context.getProtectedValues(), now);
            return null;
        });
    }

}
