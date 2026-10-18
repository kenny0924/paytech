package com.paytech.card.ext.rest.converter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.card.api.storedcard.response.InvalidateStoredCardsResponseBody;
import com.paytech.card.api.storedcard.response.StoredCardListResponseBody;
import com.paytech.card.domain.StoredCardView;

/** 已存卡协议转换 */
@Component
public class StoredCardConverter {
    /** 转换已存卡列表 */
    public StoredCardListResponseBody convertList(List<StoredCardView> views) {
        StoredCardListResponseBody body = new StoredCardListResponseBody();
        body.setStoredCards(views.stream().map(StoredCardConverter::convertCard).toList());
        return body;
    }

    /** 转换失效条数 */
    public InvalidateStoredCardsResponseBody convertInvalidation(int invalidatedCount) {
        InvalidateStoredCardsResponseBody body = new InvalidateStoredCardsResponseBody();
        body.setInvalidatedCount(invalidatedCount);
        return body;
    }

    /** 单张已存卡 */
    static StoredCardListResponseBody.StoredCard convertCard(StoredCardView view) {
        StoredCardListResponseBody.StoredCard card = new StoredCardListResponseBody.StoredCard();
        card.setStoredCardToken(view.storedCardToken());
        card.setMaskedPan(view.maskedPan());
        card.setLast4(view.last4());
        card.setExpiryMonth(view.expiryMonth());
        card.setExpiryYear(view.expiryYear());
        card.setCreatedAt(view.createdAt().toString());
        return card;
    }
}
