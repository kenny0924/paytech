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
        CardInput card = new CardInput();
        card.setPan(body.getCard().getPan());
        card.setExpiryMonth(body.getCard().getExpiryMonth());
        card.setExpiryYear(body.getCard().getExpiryYear());
        card.setCardholderName(body.getCard().getCardholderName());
        card.setCvv(body.getCard().getCvv());
        request.setCard(card);
        return request;
    }

    /** 转换响应 */
    public ApplyCardInstrumentResponseBody convertResponse(ApplyCardInstrumentResponse response) {
        ApplyCardInstrumentResponseBody body = new ApplyCardInstrumentResponseBody();
        body.setPaymentInstrumentToken(response.getPaymentInstrumentToken());
        body.setExpiresAt(response.getExpiresAt().toString());
        return body;
    }
}
