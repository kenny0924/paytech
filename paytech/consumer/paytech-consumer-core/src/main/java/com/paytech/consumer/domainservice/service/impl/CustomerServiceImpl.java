package com.paytech.consumer.domainservice.service.impl;

import java.time.Instant;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.paytech.consumer.core.dal.dataobject.Customer;
import com.paytech.consumer.api.enums.CustomerStatus;
import com.paytech.consumer.domainservice.repository.CustomerRepository;
import com.paytech.consumer.domainservice.service.CustomerService;
import com.paytech.consumer.ext.job.StoredCardInvalidationHandler;
import com.paytech.consumer.service.transaction.ConsumerTransactionExecutor;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.token.PublicTokenGenerator;

import lombok.AllArgsConstructor;

/** Customer 本地持久化；删除后 Payment 已存卡由提交后的 Handler 在事务外失效。 */
@Service
@AllArgsConstructor
public class CustomerServiceImpl implements CustomerService {
    /** 公开 Customer 编号前缀。 */
    static final String CUSTOMER_NO_PREFIX = "cus_";

    private final CustomerRepository customers;
    private final IdGenerator identifierGenerator;
    private final ConsumerTransactionExecutor transactions;
    /** Registry 启动时需要创建 Handler，登记器在业务调用时取得，避免相互构造。 */
    private final ObjectProvider<CompensationEventRegistrar> registrars;

    /** 先读有效 Customer；没有时单独短事务插入，撞唯一键改读获胜的那条。 */
    @Override
    public Resolution resolveActiveCustomer(long clientId, String consumerReference, Instant now) {
        Customer existing = customers.findActive(clientId, consumerReference);
        if (existing != null) {
            return new Resolution(existing, false);
        }
        Customer customer = newCustomer(clientId, consumerReference, now);
        try {
            transactions.write(() -> {
                customers.insert(customer);
                return null;
            });
            return new Resolution(customer, true);
        } catch (DuplicateKeyException concurrent) {
            Customer winner = customers.findActive(clientId, consumerReference);
            if (winner == null) {
                throw new ErrorException("Customer 唯一冲突后未找到有效记录", concurrent);
            }
            return new Resolution(winner, false);
        }
    }

    /** 条件删除与登记任务在同一事务；任务凭 Customer 主键恢复。 */
    @Override
    public void deleteCustomer(Customer customer, Instant now) {
        transactions.write(() -> {
            customers.delete(customer, now);
            registrars.getObject().register(StoredCardInvalidationHandler.buildEvent(customer.getCustomerId()));
            return null;
        });
    }

    /** 新的有效 Customer：随机公开编号，引用同时写入有效引用列。 */
    Customer newCustomer(long clientId, String consumerReference, Instant now) {
        return Customer.builder().customerId(identifierGenerator.nextId())
            .customerNo(CUSTOMER_NO_PREFIX + PublicTokenGenerator.generate(Long.toString(clientId)))
            .clientId(clientId).consumerReference(consumerReference).activeReference(consumerReference)
            .customerStatus(CustomerStatus.ACTIVE).version(1).createTime(now).updateTime(now).build();
    }
}
