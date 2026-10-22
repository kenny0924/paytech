package com.paytech.counterparty.ext.rest.external.clientapi.v1.customer;

import java.util.List;

import com.paytech.counterparty.api.client.v1.customer.response.CustomerResponseBody;
import com.paytech.counterparty.api.client.v1.customer.response.ListStoredCardsResponseBody;
import com.paytech.counterparty.core.dal.dataobject.Customer;
import com.paytech.counterparty.integration.card.StoredCardClient.StoredCard;
import com.paytech.counterparty.service.processor.request.ClientCustomerRequest;

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

    /** 已存卡列表；只返回掩码卡号、卡组织与有效期，不返回其他卡要素。 */
    static ListStoredCardsResponseBody storedCards(List<StoredCard> cards) {
        return new ListStoredCardsResponseBody(cards.stream()
            .map(card -> new ListStoredCardsResponseBody.StoredCardInfo(card.storedCardToken(), card.maskedPan(),
                card.cardBrand(), card.expiryMonth(), card.expiryYear(), card.createdAt()))
            .toList());
    }
}
