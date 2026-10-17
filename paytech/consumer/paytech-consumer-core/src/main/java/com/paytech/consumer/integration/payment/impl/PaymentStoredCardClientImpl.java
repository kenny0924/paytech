package com.paytech.commerce.integration.payment.impl;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.commerce.domain.checkout.CheckoutUpstreamProtocolException;
import com.paytech.commerce.integration.payment.PaymentStoredCardClient;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.payment.api.card.StoredCardApi;
import com.paytech.payment.api.card.request.InvalidateStoredCardsRequestBody;
import com.paytech.payment.api.card.response.InvalidateStoredCardsResponseBody;
import com.paytech.payment.api.card.response.StoredCardListResponseBody;

import lombok.AllArgsConstructor;

/** 只适配 Payment 已存卡 DTO；HTTP、MDC 和错误解码交给公共客户端。 */
@Component
@AllArgsConstructor
public class PaymentStoredCardClientImpl implements PaymentStoredCardClient {
    private final StoredCardApi storedCards;

    /** 校验每张卡的必填字段，不接受缺失或格式不符的响应。 */
    @Override
    public List<StoredCard> list(long clientId, long customerId) {
        StoredCardListResponseBody body =
            storedCards.listStoredCards(Long.toString(clientId), Long.toString(customerId));
        if (body == null || body.getStoredCards() == null) {
            throw new CheckoutUpstreamProtocolException("Payment 已存卡列表响应无效");
        }
        return body.getStoredCards().stream().map(PaymentStoredCardClientImpl::storedCard).toList();
    }

    /** Payment 返回不存在时转为 false，其余错误原样抛出。 */
    @Override
    public boolean delete(long clientId, long customerId, String storedCardToken) {
        try {
            storedCards.deleteStoredCard(storedCardToken, Long.toString(clientId), Long.toString(customerId));
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
        InvalidateStoredCardsResponseBody body = storedCards.invalidateStoredCards(Long.toString(customerId), request);
        if (body == null || body.getInvalidatedCount() < 0) {
            throw new CheckoutUpstreamProtocolException("Payment 已存卡失效响应无效");
        }
        return body.getInvalidatedCount();
    }

    /** 单张已存卡：Token、掩码、有效期与时间必须齐全且格式正确。 */
    static StoredCard storedCard(StoredCardListResponseBody.StoredCard card) {
        if (card == null || card.getStoredCardToken() == null || card.getMaskedPan() == null
            || !card.getMaskedPan().matches("[0-9]{6}\\*{6}[0-9]{4}") || card.getExpiryMonth() == null
            || !card.getExpiryMonth().matches("0[1-9]|1[0-2]") || card.getExpiryYear() == null
            || !card.getExpiryYear().matches("[0-9]{4}") || card.getCreatedAt() == null) {
            throw new CheckoutUpstreamProtocolException("Payment 已存卡响应无效");
        }
        try {
            return new StoredCard(card.getStoredCardToken(), card.getMaskedPan(), card.getLast4(), card.getExpiryMonth(),
                card.getExpiryYear(), Instant.parse(card.getCreatedAt()));
        } catch (DateTimeException invalidTime) {
            throw new CheckoutUpstreamProtocolException("Payment 已存卡时间无效", invalidTime);
        }
    }
}
