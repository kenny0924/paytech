package com.paytech.payment.ext.rest.card;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.payment.api.card.StoredCardApi;
import com.paytech.payment.api.card.request.InvalidateStoredCardsRequestBody;
import com.paytech.payment.api.card.response.InvalidateStoredCardsResponseBody;
import com.paytech.payment.api.card.response.StoredCardListResponseBody;
import com.paytech.payment.card.service.StoredCardService;
import com.paytech.payment.ext.rest.converter.StoredCardConverter;

import lombok.AllArgsConstructor;

/** 已存卡私网入口：只读或单表本地写入，直接委托 Card 的已存卡服务。 */
@RestController
@AllArgsConstructor
public class StoredCardController implements StoredCardApi {
    final StoredCardConverter converter;
    final StoredCardService storedCardService;

    /** 列出某 Customer 有效且未过期的已存卡 */
    @Override
    public StoredCardListResponseBody listStoredCards(String clientId, String customerId) {
        return converter.convertList(storedCardService.listStoredCards(Long.parseLong(clientId), Long.parseLong(customerId)));
    }

    /** 删除一张已存卡 */
    @Override
    public ResponseEntity<Void> deleteStoredCard(String storedCardToken, String clientId, String customerId) {
        storedCardService.deleteStoredCard(Long.parseLong(clientId), Long.parseLong(customerId), storedCardToken);
        return ResponseEntity.noContent().build();
    }

    /** 让某 Customer 的全部有效已存卡失效 */
    @Override
    public InvalidateStoredCardsResponseBody invalidateStoredCards(String customerId,
        InvalidateStoredCardsRequestBody body) {
        return converter.convertInvalidation(
            storedCardService.invalidateCustomer(Long.parseLong(body.getClientId()), Long.parseLong(customerId)));
    }
}
