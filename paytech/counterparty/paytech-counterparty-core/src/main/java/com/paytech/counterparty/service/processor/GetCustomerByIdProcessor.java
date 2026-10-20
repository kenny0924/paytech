package com.paytech.counterparty.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.counterparty.api.enums.CounterpartyErrorCode;
import com.paytech.counterparty.core.dal.dataobject.Customer;
import com.paytech.counterparty.domainservice.repository.CustomerRepository;
import com.paytech.counterparty.service.processor.request.CustomerIdRequest;
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
            throw new FailException(CounterpartyErrorCode.CUSTOMER_NOT_FOUND);
        }
        return customer;
    }
}
