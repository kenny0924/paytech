package com.paytech.card.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.card.core.dal.dataobject.PaymentCard;
import com.paytech.card.core.dal.mapper.PaymentCardMapper;
import com.paytech.card.domainservice.repository.PaymentCardRepository;

import lombok.AllArgsConstructor;

/** 卡数据访问 */
@Repository
@AllArgsConstructor
public class PaymentCardRepositoryImpl implements PaymentCardRepository {
    private final PaymentCardMapper paymentCardMapper;

    /** 插入一行；影响行数不为 1 视为系统错误。 */
    @Override
    public void insertCard(PaymentCard card) {
        if (paymentCardMapper.insertCard(card) != 1) {
            throw new ErrorException("卡保存失败: " + card.getPaymentCardId());
        }
    }

    @Override
    public PaymentCard findCard(long paymentCardId) {
        return paymentCardMapper.findCard(paymentCardId);
    }

    @Override
    public PaymentCard findCardByFingerprint(String cardFingerprint) {
        return paymentCardMapper.findCardByFingerprint(cardFingerprint);
    }
}
