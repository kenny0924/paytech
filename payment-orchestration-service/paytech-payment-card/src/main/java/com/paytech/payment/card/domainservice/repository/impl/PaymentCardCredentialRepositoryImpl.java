package com.paytech.payment.card.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.payment.card.core.dal.dataobject.PaymentCardCredential;
import com.paytech.payment.card.core.dal.mapper.PaymentCardCredentialMapper;
import com.paytech.payment.card.domainservice.repository.PaymentCardCredentialRepository;

import lombok.AllArgsConstructor;

/** 卡凭证数据访问 */
@Repository
@AllArgsConstructor
public class PaymentCardCredentialRepositoryImpl implements PaymentCardCredentialRepository {
    private final PaymentCardCredentialMapper paymentCardCredentialMapper;

    /** 插入一行；影响行数不为 1 视为系统错误。 */
    @Override
    public void insertCredential(PaymentCardCredential credential) {
        if (paymentCardCredentialMapper.insertCredential(credential) != 1) {
            throw new ErrorException("卡凭证保存失败: " + credential.getPaymentCardCredentialId());
        }
    }

    @Override
    public PaymentCardCredential findCredential(long paymentCardCredentialId) {
        return paymentCardCredentialMapper.findCredential(paymentCardCredentialId);
    }

    @Override
    public PaymentCardCredential findCredentialByDigest(long paymentCardId, String credentialDigest) {
        return paymentCardCredentialMapper.findCredentialByDigest(paymentCardId, credentialDigest);
    }
}
