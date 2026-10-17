package com.paytech.commerce.service.processor.customer;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.commerce.client.api.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.commerce.core.dal.dataobject.customer.Customer;
import com.paytech.commerce.domain.enums.CustomerStatus;
import com.paytech.commerce.domainservice.repository.customer.CustomerRepository;
import com.paytech.commerce.domainservice.service.customer.CustomerService;
import com.paytech.commerce.service.processor.customer.request.ClientCustomerRequest;
import com.paytech.commerce.service.processor.customer.response.ClientCustomerResponse;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 删除 Customer，不可撤销；已删除的直接返回原删除结果。
 *
 * <p>
 * 本地删除与“失效已存卡”任务同一事务保存，Payment 在提交后由任务调用；并发删除由版本条件保护，失败的一方读取已删除的结果返回。
 */
@Component
@AllArgsConstructor
public class DeleteCustomerProcessor extends AbstractProcessor<ClientCustomerRequest, ClientCustomerResponse> {
    private final Clock clock;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;

    @Override
    protected ClientCustomerResponse processRequest(ClientCustomerRequest request) {
        Customer customer = customerRepository.findByNo(request.getClientId(), request.getCustomerNo());
        if (customer == null) {
            throw new FailException(CommerceCheckoutErrorCode.CUSTOMER_NOT_FOUND);
        }
        if (customer.getCustomerStatus() == CustomerStatus.DELETED) {
            return new ClientCustomerResponse(customer, false);
        }
        customerService.deleteCustomer(customer, clock.instant().truncatedTo(ChronoUnit.MICROS));
        return new ClientCustomerResponse(customer, false);
    }
}
