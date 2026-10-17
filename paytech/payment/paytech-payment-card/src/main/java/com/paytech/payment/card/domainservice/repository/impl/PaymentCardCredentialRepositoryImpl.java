package com.paytech.payment.card.domainservice.repository.impl;

import java.time.Instant;

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

    /** 条件更新只在未验证时生效；成功后同步对象。 */
    @Override
    public void markVerified(PaymentCardCredential credential, Instant verifiedTime) {
        if (credential.getVerifiedTime() != null) {
            return;
        }
        if (paymentCardCredentialMapper.markVerified(credential.getPaymentCardCredentialId(), verifiedTime) == 1) {
            credential.setVerifiedTime(verifiedTime);
            credential.setUpdateTime(verifiedTime);
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
