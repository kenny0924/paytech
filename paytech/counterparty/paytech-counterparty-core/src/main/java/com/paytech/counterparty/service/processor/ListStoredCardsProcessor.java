package com.paytech.counterparty.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.counterparty.api.enums.CounterpartyErrorCode;
import com.paytech.counterparty.core.dal.dataobject.Customer;
import com.paytech.counterparty.api.enums.CustomerStatus;
import com.paytech.counterparty.domainservice.repository.CustomerRepository;
import com.paytech.counterparty.integration.card.StoredCardClient;
import com.paytech.counterparty.integration.card.StoredCardClient.StoredCard;
import com.paytech.counterparty.service.processor.request.ClientCustomerRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** 列出 Customer 的已存卡：核对归属后在事务外查询 Payment；已删除的 Customer 返回空列表，不调用 Payment。 */
@Component
@AllArgsConstructor
public class ListStoredCardsProcessor extends AbstractProcessor<ClientCustomerRequest, List<StoredCard>> {
    private final CustomerRepository customerRepository;
    private final StoredCardClient storedCards;

    @Override
    protected List<StoredCard> processRequest(ClientCustomerRequest request) {
        Customer customer = customerRepository.findByNo(request.getClientId(), request.getCustomerNo());
        if (customer == null) {
            throw new FailException(CounterpartyErrorCode.CUSTOMER_NOT_FOUND);
        }
        if (customer.getCustomerStatus() == CustomerStatus.DELETED) {
            return List.of();
        }
        return storedCards.list(customer.getClientId(), customer.getCustomerId());
    }
}
