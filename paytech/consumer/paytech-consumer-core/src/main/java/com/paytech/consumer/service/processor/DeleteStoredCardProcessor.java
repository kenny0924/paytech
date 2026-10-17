package com.paytech.consumer.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.consumer.api.enums.ConsumerErrorCode;
import com.paytech.consumer.core.dal.dataobject.Customer;
import com.paytech.consumer.api.enums.CustomerStatus;
import com.paytech.consumer.domainservice.repository.CustomerRepository;
import com.paytech.consumer.integration.payment.PaymentStoredCardClient;
import com.paytech.consumer.service.processor.request.ClientCustomerRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** 删除一张已存卡：核对 Customer 归属与状态后在事务外调用 Payment；已删除 Customer 的卡已失效，视为不存在。 */
@Component
@AllArgsConstructor
public class DeleteStoredCardProcessor extends AbstractProcessor<ClientCustomerRequest, Void> {
    private final CustomerRepository customerRepository;
    private final PaymentStoredCardClient storedCards;

    @Override
    protected Void processRequest(ClientCustomerRequest request) {
        Customer customer = customerRepository.findByNo(request.getClientId(), request.getCustomerNo());
        if (customer == null) {
            throw new FailException(ConsumerErrorCode.CUSTOMER_NOT_FOUND);
        }
        if (customer.getCustomerStatus() == CustomerStatus.DELETED
            || !storedCards.delete(customer.getClientId(), customer.getCustomerId(), request.getStoredCardToken())) {
            throw new FailException(ConsumerErrorCode.STORED_CARD_NOT_FOUND);
        }
        return null;
    }
}
