package com.paytech.consumer.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.consumer.api.enums.ConsumerErrorCode;
import com.paytech.consumer.core.dal.dataobject.Customer;
import com.paytech.consumer.api.enums.CustomerStatus;
import com.paytech.consumer.domainservice.repository.CustomerRepository;
import com.paytech.consumer.integration.payment.PaymentStoredCardClient;
import com.paytech.consumer.integration.payment.PaymentStoredCardClient.StoredCard;
import com.paytech.consumer.service.processor.request.ClientCustomerRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** 列出 Customer 的已存卡：核对归属后在事务外查询 Payment；已删除的 Customer 返回空列表，不调用 Payment。 */
@Component
@AllArgsConstructor
public class ListStoredCardsProcessor extends AbstractProcessor<ClientCustomerRequest, List<StoredCard>> {
    private final CustomerRepository customerRepository;
    private final PaymentStoredCardClient storedCards;

    @Override
    protected List<StoredCard> processRequest(ClientCustomerRequest request) {
        Customer customer = customerRepository.findByNo(request.getClientId(), request.getCustomerNo());
        if (customer == null) {
            throw new FailException(ConsumerErrorCode.CUSTOMER_NOT_FOUND);
        }
        if (customer.getCustomerStatus() == CustomerStatus.DELETED) {
            return List.of();
        }
        return storedCards.list(customer.getClientId(), customer.getCustomerId());
    }
}
