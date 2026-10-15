package com.paytech.payment.card.service.impl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.payment.card.core.dal.dataobject.PaymentCard;
import com.paytech.payment.card.core.dal.dataobject.PaymentCardInstrument;
import com.paytech.payment.card.domain.CardInstrumentContext;
import com.paytech.payment.card.domain.CardInstrumentRecord;
import com.paytech.payment.card.domain.CardRiskAttributes;
import com.paytech.payment.card.domain.PreparedInstrument;
import com.paytech.payment.card.domainservice.repository.PaymentCardInstrumentRepository;
import com.paytech.payment.card.service.CardService;
import com.paytech.payment.card.service.CardStorageService;

import lombok.AllArgsConstructor;

/** Card 本地能力：卡交易工具保存与风控属性读取 */
@Service
@AllArgsConstructor
public class CardServiceImpl implements CardService {
    private final Clock clock;
    private final CardStorageService cardStorageService;
    private final PaymentCardInstrumentRepository paymentCardInstrumentRepository;

    /** 把准备结果写成交易工具；归属取自已核对的上下文。 */
    @Override
    public void applyInstrument(CardInstrumentContext context) {
        PreparedInstrument prepared = context.getPreparedInstrument();
        if (prepared == null
            || !String.valueOf(prepared.getPaymentCardInstrumentId()).equals(context.getInstrumentRef())) {
            throw new ErrorException("卡交易工具尚未准备或与工具引用不一致");
        }
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        PaymentCardInstrument instrument = new PaymentCardInstrument();
        instrument.setPaymentCardInstrumentId(prepared.getPaymentCardInstrumentId());
        instrument.setPaymentCardCredentialId(prepared.getPaymentCardCredentialId());
        instrument.setClientId(context.getClientId());
        instrument.setCreateTime(now);
        instrument.setUpdateTime(now);
        paymentCardInstrumentRepository.insertInstrument(instrument);
    }

    /** 只读卡的 BIN 与指纹。 */
    @Override
    public CardRiskAttributes riskAttributes(String instrumentRef) {
        CardInstrumentRecord record = cardStorageService.findInstrument(instrumentRef);
        if (record == null) {
            return null;
        }
        PaymentCard card = record.card();
        return new CardRiskAttributes(card.getCardBin(), card.getCardFingerprint());
    }
}
