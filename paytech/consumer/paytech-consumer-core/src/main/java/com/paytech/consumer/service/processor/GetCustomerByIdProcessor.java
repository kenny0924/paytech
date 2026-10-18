package com.paytech.consumer.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.consumer.api.enums.ConsumerErrorCode;
import com.paytech.consumer.core.dal.dataobject.Customer;
import com.paytech.consumer.domainservice.repository.CustomerRepository;
import com.paytech.consumer.service.processor.request.CustomerIdRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** acquiring 内部读取：按主键返回 Customer（含已删除），供支付付款人引用使用公开编号；不存在返回 CUSTOMER_NOT_FOUND。 */
@Component
@AllArgsConstructor
public class GetCustomerByIdProcessor extends AbstractProcessor<CustomerIdRequest, Customer> {
    private final CustomerRepository customerRepository;

    @Override
    protected Customer processRequest(CustomerIdRequest request) {
        Customer customer = customerRepository.findById(request.customerId());
        if (customer == null) {
            throw new FailException(ConsumerErrorCode.CUSTOMER_NOT_FOUND);
        }
        return customer;
    }
}
