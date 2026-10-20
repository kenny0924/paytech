package com.paytech.counterparty.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.counterparty.api.enums.CounterpartyErrorCode;
import com.paytech.counterparty.core.dal.dataobject.Customer;
import com.paytech.counterparty.api.enums.CustomerStatus;
import com.paytech.counterparty.domainservice.repository.CustomerRepository;
import com.paytech.counterparty.integration.card.StoredCardClient;
import com.paytech.counterparty.service.processor.request.ClientCustomerRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** 删除一张已存卡：核对 Customer 归属与状态后在事务外调用 Payment；已删除 Customer 的卡已失效，视为不存在。 */
@Component
@AllArgsConstructor
public class DeleteStoredCardProcessor extends AbstractProcessor<ClientCustomerRequest, Void> {
    private final CustomerRepository customerRepository;
    private final StoredCardClient storedCards;

    @Override
    protected Void processRequest(ClientCustomerRequest request) {
        Customer customer = customerRepository.findByNo(request.getClientId(), request.getCustomerNo());
        if (customer == null) {
            throw new FailException(CounterpartyErrorCode.CUSTOMER_NOT_FOUND);
        }
        if (customer.getCustomerStatus() == CustomerStatus.DELETED
            || !storedCards.delete(customer.getClientId(), customer.getCustomerId(), request.getStoredCardToken())) {
            throw new FailException(CounterpartyErrorCode.STORED_CARD_NOT_FOUND);
        }
        return null;
    }
}
