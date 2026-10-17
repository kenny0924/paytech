package com.paytech.payment.ext.rest.converter;

import org.springframework.stereotype.Component;

import com.paytech.payment.api.instrument.request.ApplyCardInstrumentRequestBody;
import com.paytech.payment.api.instrument.response.ApplyCardInstrumentResponseBody;
import com.paytech.payment.card.domain.CardInstrumentContext.CardInput;
import com.paytech.payment.execution.service.processor.request.ApplyCardInstrumentRequest;
import com.paytech.payment.execution.service.processor.response.ApplyCardInstrumentResponse;

/** 卡工具申请协议转换 */
@Component
public class ApplyCardInstrumentConverter {
    /** 转换请求 */
    public ApplyCardInstrumentRequest convertRequest(ApplyCardInstrumentRequestBody body) {
        ApplyCardInstrumentRequest request = new ApplyCardInstrumentRequest();
        request.setClientId(Long.valueOf(body.getClientId()));
        request.setIntentId(Long.valueOf(body.getIntentId()));
        request.setSourceSubmissionId(Long.valueOf(body.getSourceSubmissionId()));
        request.setPaymentMethodCode(body.getPaymentMethodCode());
        request.setCustomerId(body.getCustomerId() == null ? null : Long.valueOf(body.getCustomerId()));
        request.setSaveCard(Boolean.TRUE.equals(body.getSaveCard()));
        if (body.getStoredCard() != null) {
            request.setStoredCardToken(body.getStoredCard().getStoredCardToken());
            request.setCard(storedCardInput(body.getStoredCard()));
            return request;
        }
        CardInput card = new CardInput();
        card.setPan(body.getCard().getPan());
        card.setExpiryMonth(body.getCard().getExpiryMonth());
        card.setExpiryYear(body.getCard().getExpiryYear());
        card.setCardholderName(body.getCard().getCardholderName());
        card.setCvv(body.getCard().getCvv());
        request.setCard(card);
        return request;
    }

    /** 用已存卡时卡输入只含本次 CVV。 */
    static CardInput storedCardInput(ApplyCardInstrumentRequestBody.StoredCard storedCard) {
        CardInput card = new CardInput();
        card.setCvv(storedCard.getCvv());
        return card;
    }

    /** 转换响应 */
    public ApplyCardInstrumentResponseBody convertResponse(ApplyCardInstrumentResponse response) {
        ApplyCardInstrumentResponseBody body = new ApplyCardInstrumentResponseBody();
        body.setPaymentInstrumentToken(response.getPaymentInstrumentToken());
        body.setExpiresAt(response.getExpiresAt().toString());
        return body;
    }
}
