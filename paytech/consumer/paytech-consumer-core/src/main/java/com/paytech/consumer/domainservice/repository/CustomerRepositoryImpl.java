package com.paytech.commerce.domainservice.repository.customer;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.commerce.core.dal.dataobject.customer.Customer;
import com.paytech.commerce.core.dal.mapper.customer.CustomerMapper;
import com.paytech.commerce.domain.enums.CustomerStatus;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** Customer 数据访问。 */
@Repository
@AllArgsConstructor
public class CustomerRepositoryImpl implements CustomerRepository {
    private final CustomerMapper mapper;

    @Override
    public void insert(Customer customer) {
        requireOne(mapper.insert(customer));
    }

    @Override
    public Customer findById(long customerId) {
        return mapper.findById(customerId);
    }

    @Override
    public Customer findByNo(long clientId, String customerNo) {
        return mapper.findByNo(clientId, customerNo);
    }

    @Override
    public Customer findActive(long clientId, String consumerReference) {
        return mapper.findActive(clientId, consumerReference);
    }

    /** 条件删除成功后同步状态、版本与时间。 */
    @Override
    public void delete(Customer customer, Instant now) {
        requireOne(mapper.delete(customer, now));
        customer.setCustomerStatus(CustomerStatus.DELETED);
        customer.setActiveReference(null);
        customer.setDeletedTime(now);
        customer.setVersion(customer.getVersion() + 1);
        customer.setUpdateTime(now);
    }

    private void requireOne(int changed) {
        if (changed != 1) {
            throw new ErrorException("Customer 写入影响行数异常: " + changed);
        }
    }
}
