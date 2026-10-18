package com.paytech.card.service.impl;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.card.core.crypto.CardCipher;
import com.paytech.card.core.dal.dataobject.PaymentCard;
import com.paytech.card.core.dal.dataobject.PaymentCardCredential;
import com.paytech.card.core.dal.dataobject.PaymentCardInstrument;
import com.paytech.card.domain.CardInstrumentRecord;
import com.paytech.card.domainservice.repository.PaymentCardCredentialRepository;
import com.paytech.card.domainservice.repository.PaymentCardInstrumentRepository;
import com.paytech.card.domainservice.repository.PaymentCardRepository;
import com.paytech.card.service.CardStorageService;

import lombok.AllArgsConstructor;

/** 卡与卡凭证的本地存取：唯一冲突改读已有记录 */
@Service
@AllArgsConstructor
public class CardStorageServiceImpl implements CardStorageService {
    private final CardCipher cardCipher;
    private final PaymentCardRepository paymentCardRepository;
    private final PaymentCardCredentialRepository paymentCardCredentialRepository;
    private final PaymentCardInstrumentRepository paymentCardInstrumentRepository;

    /** 按卡指纹唯一索引查询。 */
    @Override
    public PaymentCard findCard(String pan) {
        return paymentCardRepository.findCardByFingerprint(cardCipher.fingerprint(pan));
    }

    /** 插入；撞唯一键时读取赢得竞争的那条。 */
    @Override
    public PaymentCard saveCard(PaymentCard card) {
        try {
            paymentCardRepository.insertCard(card);
            return card;
        } catch (DuplicateKeyException concurrent) {
            PaymentCard existing = paymentCardRepository.findCardByFingerprint(card.getCardFingerprint());
            if (existing == null) {
                throw new ErrorException("卡唯一冲突后未找到已有卡: " + card.getPaymentCardId(), concurrent);
            }
            return existing;
        }
    }

    /** 插入；撞唯一键时读取已有凭证。 */
    @Override
    public PaymentCardCredential saveCredential(PaymentCardCredential credential) {
        try {
            paymentCardCredentialRepository.insertCredential(credential);
            return credential;
        } catch (DuplicateKeyException concurrent) {
            PaymentCardCredential existing = paymentCardCredentialRepository
                .findCredentialByDigest(credential.getPaymentCardId(), credential.getCredentialDigest());
            if (existing == null) {
                throw new ErrorException("卡凭证唯一冲突后未找到已有凭证: " + credential.getPaymentCardCredentialId(),
                    concurrent);
            }
            return existing;
        }
    }

    /** 工具 → 凭证 → 卡；工具存在而凭证或卡缺失属于数据损坏。 */
    @Override
    public CardInstrumentRecord findInstrument(String instrumentRef) {
        PaymentCardInstrument instrument = paymentCardInstrumentRepository.findInstrument(instrumentRef);
        if (instrument == null) {
            return null;
        }
        PaymentCardCredential credential =
            paymentCardCredentialRepository.findCredential(instrument.getPaymentCardCredentialId());
        PaymentCard card = credential == null ? null : paymentCardRepository.findCard(credential.getPaymentCardId());
        if (card == null) {
            throw new ErrorException("卡交易工具关联的凭证或卡不存在: " + instrument.getPaymentCardInstrumentId());
        }
        return new CardInstrumentRecord(instrument, credential, card);
    }
}
