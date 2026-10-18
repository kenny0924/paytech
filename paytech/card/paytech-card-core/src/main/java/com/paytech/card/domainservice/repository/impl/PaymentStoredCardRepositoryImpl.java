package com.paytech.card.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.card.core.dal.dataobject.PaymentStoredCard;
import com.paytech.card.core.dal.mapper.PaymentStoredCardMapper;
import com.paytech.card.core.enums.StoredCardStatus;
import com.paytech.card.domainservice.repository.PaymentStoredCardRepository;

import lombok.AllArgsConstructor;

/** 已存卡数据访问 */
@Repository
@AllArgsConstructor
public class PaymentStoredCardRepositoryImpl implements PaymentStoredCardRepository {
    private final PaymentStoredCardMapper paymentStoredCardMapper;

    /** 插入一行；影响行数不为 1 视为系统错误。 */
    @Override
    public void insertStoredCard(PaymentStoredCard storedCard) {
        if (paymentStoredCardMapper.insertStoredCard(storedCard) != 1) {
            throw new ErrorException("已存卡保存失败: " + storedCard.getPaymentStoredCardId());
        }
    }

    @Override
    public PaymentStoredCard findByToken(String storedCardToken) {
        return paymentStoredCardMapper.findByToken(storedCardToken);
    }

    @Override
    public PaymentStoredCard findActive(long clientId, long customerId, long paymentCardId) {
        return paymentStoredCardMapper.findActive(clientId, customerId, paymentCardId);
    }

    @Override
    public PaymentStoredCard lockActive(long clientId, long customerId, long paymentCardId) {
        return paymentStoredCardMapper.lockActive(clientId, customerId, paymentCardId);
    }

    @Override
    public List<PaymentStoredCard> listActive(long clientId, long customerId) {
        return paymentStoredCardMapper.listActive(clientId, customerId);
    }

    /** 仍有效时更新并同步凭证、同意时间与版本；已删除时不改。 */
    @Override
    public void updateCredential(PaymentStoredCard storedCard, long paymentCardCredentialId, Instant consentTime) {
        if (paymentStoredCardMapper.updateCredential(storedCard.getPaymentStoredCardId(), paymentCardCredentialId,
            consentTime) != 1) {
            return;
        }
        storedCard.setPaymentCardCredentialId(paymentCardCredentialId);
        storedCard.setConsentTime(consentTime);
        storedCard.setVersion(storedCard.getVersion() + 1);
        storedCard.setUpdateTime(consentTime);
    }

    /** 条件删除成功后同步状态与版本。 */
    @Override
    public void markDeleted(PaymentStoredCard storedCard, Instant deletedTime) {
        if (paymentStoredCardMapper.markDeleted(storedCard.getPaymentStoredCardId(), deletedTime,
            storedCard.getVersion()) != 1) {
            throw new ConcurrentRequestException("已存卡已被并发修改: " + storedCard.getPaymentStoredCardId());
        }
        storedCard.setStoredCardStatus(StoredCardStatus.DELETED);
        storedCard.setActiveCardId(null);
        storedCard.setDeletedTime(deletedTime);
        storedCard.setVersion(storedCard.getVersion() + 1);
        storedCard.setUpdateTime(deletedTime);
    }

    @Override
    public int invalidateCustomer(long clientId, long customerId, Instant deletedTime) {
        return paymentStoredCardMapper.invalidateCustomer(clientId, customerId, deletedTime);
    }
}
