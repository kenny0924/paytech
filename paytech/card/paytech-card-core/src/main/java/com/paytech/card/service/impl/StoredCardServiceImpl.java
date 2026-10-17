package com.paytech.card.service.impl;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.card.core.dal.dataobject.PaymentCard;
import com.paytech.card.core.dal.dataobject.PaymentCardCredential;
import com.paytech.card.core.dal.dataobject.PaymentStoredCard;
import com.paytech.card.core.enums.StoredCardStatus;
import com.paytech.card.domain.StoredCardView;
import com.paytech.card.domainservice.repository.PaymentCardCredentialRepository;
import com.paytech.card.domainservice.repository.PaymentCardRepository;
import com.paytech.card.domainservice.repository.PaymentStoredCardRepository;
import com.paytech.card.service.StoredCardService;

import lombok.AllArgsConstructor;

/** 已存卡管理：列出、删除与按 Customer 失效 */
@Service
@AllArgsConstructor
public class StoredCardServiceImpl implements StoredCardService {
    private final Clock clock;
    private final PaymentStoredCardRepository paymentStoredCardRepository;
    private final PaymentCardRepository paymentCardRepository;
    private final PaymentCardCredentialRepository paymentCardCredentialRepository;

    /** 过期的已存卡不返回；掩码卡号取 BIN 前 6 位与后 4 位。 */
    @Override
    public List<StoredCardView> listStoredCards(long clientId, long customerId) {
        YearMonth currentMonth = YearMonth.now(clock.withZone(ZoneOffset.UTC));
        List<StoredCardView> views = new ArrayList<>();
        for (PaymentStoredCard storedCard : paymentStoredCardRepository.listActive(clientId, customerId)) {
            PaymentCardCredential credential =
                paymentCardCredentialRepository.findCredential(storedCard.getPaymentCardCredentialId());
            PaymentCard card = paymentCardRepository.findCard(storedCard.getPaymentCardId());
            if (credential == null || card == null) {
                throw new ErrorException("已存卡关联的卡或凭证不存在: " + storedCard.getPaymentStoredCardId());
            }
            if (expiryMonth(credential).isBefore(currentMonth)) {
                continue;
            }
            views.add(view(storedCard, card, credential));
        }
        return views;
    }

    /** 跨 Client 或跨 Customer 视为不存在。 */
    @Override
    public void deleteStoredCard(long clientId, long customerId, String storedCardToken) {
        PaymentStoredCard storedCard = paymentStoredCardRepository.findByToken(storedCardToken);
        if (storedCard == null || storedCard.getClientId() != clientId || storedCard.getCustomerId() != customerId) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "已存卡不存在");
        }
        if (storedCard.getStoredCardStatus() == StoredCardStatus.DELETED) {
            return;
        }
        paymentStoredCardRepository.markDeleted(storedCard, clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    @Override
    public int invalidateCustomer(long clientId, long customerId) {
        return paymentStoredCardRepository.invalidateCustomer(clientId, customerId,
            clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    /** 凭证有效期所在月份。 */
    static YearMonth expiryMonth(PaymentCardCredential credential) {
        return YearMonth.of(Integer.parseInt(credential.getExpiryYear()), Integer.parseInt(credential.getExpiryMonth()));
    }

    /** 对外视图：不含内部 ID、指纹与完整 BIN。 */
    static StoredCardView view(PaymentStoredCard storedCard, PaymentCard card, PaymentCardCredential credential) {
        String maskedPan = card.getCardBin().substring(0, 6) + "******" + card.getPanLast4();
        return new StoredCardView(storedCard.getStoredCardToken(), maskedPan, card.getPanLast4(),
            credential.getExpiryMonth(), credential.getExpiryYear(), storedCard.getCreateTime());
    }
}
