package com.paytech.card.ext.rest.converter;

import java.time.DateTimeException;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.paytech.card.api.instrument.request.CreateCardInstrumentRequestBody;
import com.paytech.card.api.instrument.response.CreateCardInstrumentResponseBody;
import com.paytech.card.domain.CardInstrumentContext;
import com.paytech.card.domain.CardInstrumentContext.CardInput;
import com.paytech.card.domain.CreatedCardInstrument;
import com.paytech.card.domain.binlibrary.CardAttributes;
import com.paytech.foundation.common.error.InvalidParameterException;

/** 卡交易工具创建协议转换；不记录、不缓存卡值。 */
@Component
public class CardInstrumentConverter {
    /** 转换请求：手输卡与已存卡恰好一种；已存卡的卡输入只含本次 CVV。 */
    public CardInstrumentContext convertRequest(CreateCardInstrumentRequestBody body) {
        if ((body.getCard() == null) == (body.getStoredCard() == null)) {
            throw new InvalidParameterException("卡信息与已存卡必须且只能提供一种");
        }
        CardInput card = new CardInput();
        String storedCardToken = null;
        if (body.getCard() != null) {
            card.setPan(body.getCard().getPan());
            card.setExpiryMonth(body.getCard().getExpiryMonth());
            card.setExpiryYear(body.getCard().getExpiryYear());
            card.setCardholderName(body.getCard().getCardholderName());
            card.setCvv(body.getCard().getCvv());
        } else {
            card.setCvv(body.getStoredCard().getCvv());
            storedCardToken = body.getStoredCard().getStoredCardToken();
        }
        var context = new CardInstrumentContext(Long.valueOf(body.getClientId()),
            Long.valueOf(body.getSourceSubmissionId()), card, instant(body.getExpiresAt()));
        context.setStoredCardToken(storedCardToken);
        context.setCustomerId(body.getCustomerId() == null ? null : Long.valueOf(body.getCustomerId()));
        context.setSaveCard(Boolean.TRUE.equals(body.getConsentToSave()));
        return context;
    }

    /** 转换响应。 */
    public CreateCardInstrumentResponseBody convertResponse(CreatedCardInstrument created) {
        var risk = new CreateCardInstrumentResponseBody.RiskAttributes();
        risk.setBin(created.riskAttributes().getBin());
        risk.setFingerprint(created.riskAttributes().getFingerprint());
        var body = new CreateCardInstrumentResponseBody();
        body.setInstrumentRef(created.instrumentRef());
        body.setMaskedPan(created.maskedPan());
        body.setExpiresAt(created.expiresAt().toString());
        body.setRiskAttributes(risk);
        body.setCardAttributes(convertAttributes(created.cardAttributes()));
        return body;
    }

    /** BIN 库卡属性快照。 */
    static CreateCardInstrumentResponseBody.CardAttributes convertAttributes(CardAttributes attributes) {
        var body = new CreateCardInstrumentResponseBody.CardAttributes();
        body.setRecognized(attributes.isRecognized());
        body.setBinLibraryVersionNo(attributes.getBinLibraryVersionNo());
        body.setMatchedPrefixLength(attributes.getMatchedPrefixLength());
        body.setCardBrand(attributes.getCardBrand());
        body.setFundingType(attributes.getFundingType());
        body.setCardLevel(attributes.getCardLevel());
        body.setCardProduct(attributes.getCardProduct());
        body.setIssuerCountry(attributes.getIssuerCountry());
        body.setIssuerRegion(attributes.getIssuerRegion());
        return body;
    }

    /** ISO-8601 UTC 时间。 */
    static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeException invalid) {
            throw new InvalidParameterException("工具期限上限格式无效");
        }
    }
}
