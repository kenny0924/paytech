package com.paytech.card.service.impl;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.card.core.dal.dataobject.PaymentCard;
import com.paytech.card.core.dal.dataobject.PaymentCardInstrument;
import com.paytech.card.core.dal.dataobject.PaymentStoredCard;
import com.paytech.card.core.enums.StoredCardStatus;
import com.paytech.card.domain.CardInstrumentContext;
import com.paytech.card.domain.CardInstrumentRecord;
import com.paytech.card.domain.PreparedInstrument;
import com.paytech.card.domainservice.repository.PaymentCardCredentialRepository;
import com.paytech.card.domainservice.repository.PaymentCardInstrumentRepository;
import com.paytech.card.domainservice.repository.PaymentStoredCardRepository;
import com.paytech.card.service.CardService;
import com.paytech.card.service.CardStorageService;

import lombok.AllArgsConstructor;

/** Card 本地能力：卡交易工具保存与支付成功后的凭证验证和存卡 */
@Service
@AllArgsConstructor
public class CardServiceImpl implements CardService {
    /** 对外已存卡 Token 前缀。 */
    static final String STORED_CARD_TOKEN_PREFIX = "sc_";

    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final Clock clock;
    private final IdGenerator idGenerator;
    private final CardStorageService cardStorageService;
    private final PaymentCardInstrumentRepository paymentCardInstrumentRepository;
    private final PaymentCardCredentialRepository paymentCardCredentialRepository;
    private final PaymentStoredCardRepository paymentStoredCardRepository;

    /** 把准备结果写成交易工具；归属、Customer 与是否保存取自已核对的上下文。 */
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
        instrument.setSourceSubmissionId(context.getSourceSubmissionId());
        instrument.setCustomerId(context.getCustomerId());
        instrument.setSaveRequested(context.isSaveCard());
        instrument.setPaymentStoredCardId(prepared.getPaymentStoredCardId());
        instrument.setExpiresAt(context.getExpiresAt().truncatedTo(ChronoUnit.MICROS));
        instrument.setCreateTime(now);
        instrument.setUpdateTime(now);
        paymentCardInstrumentRepository.insertInstrument(instrument);
    }

    /** 标记凭证已验证；同意保存且有 Customer 时存卡。 */
    @Override
    public void paymentSucceeded(String instrumentRef) {
        CardInstrumentRecord record = cardStorageService.findInstrument(instrumentRef);
        if (record == null) {
            throw new ErrorException("支付成功的卡交易工具不存在");
        }
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        paymentCardCredentialRepository.markVerified(record.credential(), now);
        PaymentCardInstrument instrument = record.instrument();
        if (!instrument.isSaveRequested() || instrument.getCustomerId() == null) {
            return;
        }
        storeCard(record, now);
    }

    /** 同一 Customer 同一张卡已有有效已存卡时更新凭证与同意时间，否则新建；并发新建撞唯一键时改为更新。 */
    void storeCard(CardInstrumentRecord record, Instant now) {
        PaymentCardInstrument instrument = record.instrument();
        long credentialId = record.credential().getPaymentCardCredentialId();
        PaymentStoredCard existing = paymentStoredCardRepository.findActive(instrument.getClientId(),
            instrument.getCustomerId(), record.card().getPaymentCardId());
        if (existing != null) {
            paymentStoredCardRepository.updateCredential(existing, credentialId, now);
            return;
        }
        try {
            paymentStoredCardRepository.insertStoredCard(newStoredCard(record, now));
        } catch (DuplicateKeyException concurrent) {
            PaymentStoredCard saved = paymentStoredCardRepository.lockActive(instrument.getClientId(),
                instrument.getCustomerId(), record.card().getPaymentCardId());
            if (saved == null) {
                throw new ErrorException("已存卡唯一冲突后未找到已有记录: " + instrument.getPaymentCardInstrumentId(),
                    concurrent);
            }
            paymentStoredCardRepository.updateCredential(saved, credentialId, now);
        }
    }

    /** 新已存卡：随机对外 Token，指向本次卡与凭证。 */
    PaymentStoredCard newStoredCard(CardInstrumentRecord record, Instant now) {
        PaymentCardInstrument instrument = record.instrument();
        PaymentStoredCard storedCard = new PaymentStoredCard();
        storedCard.setPaymentStoredCardId(idGenerator.nextId());
        storedCard.setStoredCardToken(newStoredCardToken());
        storedCard.setClientId(instrument.getClientId());
        storedCard.setCustomerId(instrument.getCustomerId());
        storedCard.setPaymentCardId(record.card().getPaymentCardId());
        storedCard.setActiveCardId(record.card().getPaymentCardId());
        storedCard.setPaymentCardCredentialId(record.credential().getPaymentCardCredentialId());
        storedCard.setStoredCardStatus(StoredCardStatus.ACTIVE);
        storedCard.setConsentTime(now);
        storedCard.setSourceCardInstrumentId(instrument.getPaymentCardInstrumentId());
        storedCard.setVersion(1L);
        storedCard.setCreateTime(now);
        storedCard.setUpdateTime(now);
        return storedCard;
    }

    /** sc_ 前缀加 21 字节随机数的 Base64URL，共 31 位；不含业务身份。 */
    static String newStoredCardToken() {
        byte[] bytes = new byte[21];
        TOKEN_RANDOM.nextBytes(bytes);
        return STORED_CARD_TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
