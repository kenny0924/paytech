package com.paytech.commerce.service.processor.customer;

import org.springframework.stereotype.Component;

import com.paytech.commerce.client.api.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.commerce.core.dal.dataobject.customer.Customer;
import com.paytech.commerce.domainservice.repository.customer.CustomerRepository;
import com.paytech.commerce.service.processor.customer.request.ClientCustomerRequest;
import com.paytech.commerce.service.processor.customer.response.ClientCustomerResponse;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** 按公开编号（含已删除）或按引用（仅有效）读取 Customer；未知与跨 Client 都返回不存在。 */
@Component
@AllArgsConstructor
public class GetCustomerProcessor extends AbstractProcessor<ClientCustomerRequest, ClientCustomerResponse> {
    private final CustomerRepository customerRepository;

    @Override
    protected ClientCustomerResponse processRequest(ClientCustomerRequest request) {
        Customer customer = request.getCustomerNo() != null
            ? customerRepository.findByNo(request.getClientId(), request.getCustomerNo())
            : customerRepository.findActive(request.getClientId(), request.getConsumerReference());
        if (customer == null) {
            throw new FailException(CommerceCheckoutErrorCode.CUSTOMER_NOT_FOUND);
        }
        return new ClientCustomerResponse(customer, false);
    }
}
