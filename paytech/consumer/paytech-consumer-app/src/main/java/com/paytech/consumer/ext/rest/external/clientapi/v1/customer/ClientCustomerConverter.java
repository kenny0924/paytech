package com.paytech.commerce.ext.rest.external.clientapi.v1.customer;

import java.util.List;

import com.paytech.commerce.client.api.v1.customer.response.CustomerResponseBody;
import com.paytech.commerce.client.api.v1.customer.response.ListStoredCardsResponseBody;
import com.paytech.commerce.core.dal.dataobject.customer.Customer;
import com.paytech.commerce.integration.payment.PaymentStoredCardClient.StoredCard;
import com.paytech.commerce.service.processor.customer.request.ClientCustomerRequest;

/** Customer 与已存卡接口的协议转换。 */
final class ClientCustomerConverter {
    private ClientCustomerConverter() {}

    /** 按编号或引用构造内部输入。 */
    static ClientCustomerRequest request(long clientId, String customerNo, String consumerReference,
        String storedCardToken) {
        return new ClientCustomerRequest(clientId, customerNo, consumerReference, storedCardToken);
    }

    /** Customer 视图；有效时省略删除时间。 */
    static CustomerResponseBody customer(Customer customer) {
        return new CustomerResponseBody(customer.getCustomerNo(), customer.getConsumerReference(),
            customer.getCustomerStatus().getCode(), customer.getCreateTime(), customer.getDeletedTime());
    }

    /** 已存卡列表；不返回后四位以外的卡要素。 */
    static ListStoredCardsResponseBody storedCards(List<StoredCard> cards) {
        return new ListStoredCardsResponseBody(cards.stream()
            .map(card -> new ListStoredCardsResponseBody.StoredCardInfo(card.storedCardToken(), card.maskedPan(),
                card.expiryMonth(), card.expiryYear(), card.createdAt()))
            .toList());
    }
}
