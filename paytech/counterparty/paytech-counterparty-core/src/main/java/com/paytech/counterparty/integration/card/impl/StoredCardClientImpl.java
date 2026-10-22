package com.paytech.counterparty.integration.card.impl;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.counterparty.domain.CounterpartyUpstreamProtocolException;
import com.paytech.counterparty.integration.card.StoredCardClient;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.enums.CardBrand;
import com.paytech.card.api.storedcard.StoredCardApi;
import com.paytech.card.api.storedcard.request.InvalidateStoredCardsRequestBody;
import com.paytech.card.api.storedcard.response.InvalidateStoredCardsResponseBody;
import com.paytech.card.api.storedcard.response.StoredCardListResponseBody;

import lombok.AllArgsConstructor;

/** 只适配 card 已存卡（第 12 步自 Payment 迁入 card-service） DTO；HTTP、MDC 和错误解码交给公共客户端。 */
@Component
@AllArgsConstructor
public class StoredCardClientImpl implements StoredCardClient {
    /** card 按调用方白名单授权（design/05 §4）。 */
    static final String CALLER = CardApiConstants.COUNTERPARTY_SERVICE;

    private final StoredCardApi storedCards;

    /** 校验每张卡的必填字段，不接受缺失或格式不符的响应。 */
    @Override
    public List<StoredCard> list(long clientId, long customerId) {
        StoredCardListResponseBody body =
            storedCards.listStoredCards(CALLER, Long.toString(clientId), Long.toString(customerId));
        if (body == null || body.getStoredCards() == null) {
            throw new CounterpartyUpstreamProtocolException("card 已存卡列表响应无效");
        }
        return body.getStoredCards().stream().map(StoredCardClientImpl::storedCard).toList();
    }

    /** card 返回不存在时转为 false，其余错误原样抛出。 */
    @Override
    public boolean delete(long clientId, long customerId, String storedCardToken) {
        try {
            storedCards.deleteStoredCard(CALLER, storedCardToken, Long.toString(clientId), Long.toString(customerId));
            return true;
        } catch (FailException failure) {
            if (CommonErrorCode.NOT_FOUND.getCode().equals(failure.getCode())) {
                return false;
            }
            throw failure;
        }
    }

    @Override
    public int invalidate(long clientId, long customerId) {
        var request = new InvalidateStoredCardsRequestBody();
        request.setClientId(Long.toString(clientId));
        InvalidateStoredCardsResponseBody body = storedCards.invalidateStoredCards(CALLER, Long.toString(customerId), request);
        if (body == null || body.getInvalidatedCount() < 0) {
            throw new CounterpartyUpstreamProtocolException("card 已存卡失效响应无效");
        }
        return body.getInvalidatedCount();
    }

    /** 单张已存卡：Token、掩码、有效期与时间必须齐全且格式正确；卡组织可空，有值时必须是 card 发布的 Code。 */
    static StoredCard storedCard(StoredCardListResponseBody.StoredCard card) {
        if (card == null || card.getStoredCardToken() == null || card.getMaskedPan() == null
            || !card.getMaskedPan().matches("[0-9]{6}\\*{6}[0-9]{4}") || card.getExpiryMonth() == null
            || !card.getExpiryMonth().matches("0[1-9]|1[0-2]") || card.getExpiryYear() == null
            || !card.getExpiryYear().matches("[0-9]{4}") || card.getCreatedAt() == null || !knownBrand(card.getCardBrand())) {
            throw new CounterpartyUpstreamProtocolException("card 已存卡响应无效");
        }
        try {
            return new StoredCard(card.getStoredCardToken(), card.getMaskedPan(), card.getLast4(), card.getCardBrand(),
                card.getExpiryMonth(), card.getExpiryYear(), Instant.parse(card.getCreatedAt()));
        } catch (DateTimeException invalidTime) {
            throw new CounterpartyUpstreamProtocolException("card 已存卡时间无效", invalidTime);
        }
    }

    /** 卡组织为空，或是 card 发布的 {@link CardBrand} Code；未知 Code 失败关闭。 */
    static boolean knownBrand(String cardBrand) {
        return cardBrand == null || Arrays.stream(CardBrand.values()).anyMatch(brand -> brand.getCode().equals(cardBrand));
    }
}
