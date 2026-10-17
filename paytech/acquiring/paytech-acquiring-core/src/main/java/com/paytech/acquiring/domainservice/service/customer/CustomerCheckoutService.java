package com.paytech.acquiring.domainservice.service.customer;
import com.paytech.acquiring.domain.customer.SaveConsumerContext;
/** 消费者本地资料写入。 */
public interface CustomerCheckoutService {
    /** REQUIRED 事务保存已校验的消费者资料。 */
    void saveConsumerContext(SaveConsumerContext context);
}
