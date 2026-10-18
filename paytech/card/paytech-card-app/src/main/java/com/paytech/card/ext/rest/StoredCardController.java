package com.paytech.card.ext.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.storedcard.StoredCardApi;
import com.paytech.card.api.storedcard.request.InvalidateStoredCardsRequestBody;
import com.paytech.card.api.storedcard.response.InvalidateStoredCardsResponseBody;
import com.paytech.card.api.storedcard.response.StoredCardListResponseBody;
import com.paytech.card.ext.rest.converter.StoredCardConverter;
import com.paytech.card.service.CardCallerService;
import com.paytech.card.service.StoredCardService;

import lombok.AllArgsConstructor;

/** 已存卡私网入口（自 Payment 迁入，design/05 §3）：按调用方白名单授权后，只读或单表本地写入，直接委托 Card 的已存卡服务。 */
@RestController
@AllArgsConstructor
public class StoredCardController implements StoredCardApi {
    final CardCallerResolver callers;
    final CardCallerService cardCallerService;
    final StoredCardConverter converter;
    final StoredCardService storedCardService;

    /** 列出某 Customer 有效且未过期的已存卡 */
    @Override
    public StoredCardListResponseBody listStoredCards(String caller, String clientId, String customerId) {
        cardCallerService.require(callers.resolve(caller), CardApiConstants.ACQUIRING_SERVICE,
            CardApiConstants.CONSUMER_SERVICE);
        return converter.convertList(storedCardService.listStoredCards(Long.parseLong(clientId), Long.parseLong(customerId)));
    }

    /** 删除一张已存卡 */
    @Override
    public ResponseEntity<Void> deleteStoredCard(String caller, String storedCardToken, String clientId,
        String customerId) {
        cardCallerService.require(callers.resolve(caller), CardApiConstants.CONSUMER_SERVICE);
        storedCardService.deleteStoredCard(Long.parseLong(clientId), Long.parseLong(customerId), storedCardToken);
        return ResponseEntity.noContent().build();
    }

    /** 让某 Customer 的全部有效已存卡失效 */
    @Override
    public InvalidateStoredCardsResponseBody invalidateStoredCards(String caller, String customerId,
        InvalidateStoredCardsRequestBody body) {
        cardCallerService.require(callers.resolve(caller), CardApiConstants.CONSUMER_SERVICE);
        return converter.convertInvalidation(
            storedCardService.invalidateCustomer(Long.parseLong(body.getClientId()), Long.parseLong(customerId)));
    }
}
