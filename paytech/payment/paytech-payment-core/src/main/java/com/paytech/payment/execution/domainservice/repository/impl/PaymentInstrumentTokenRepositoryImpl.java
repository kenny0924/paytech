package com.paytech.payment.execution.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.payment.execution.core.dal.dataobject.PaymentInstrumentToken;
import com.paytech.payment.execution.core.dal.mapper.PaymentInstrumentTokenMapper;
import com.paytech.payment.execution.domainservice.repository.PaymentInstrumentTokenRepository;

import lombok.AllArgsConstructor;

/** 支付工具数据访问 */
@Repository
@AllArgsConstructor
public class PaymentInstrumentTokenRepositoryImpl implements PaymentInstrumentTokenRepository {
    private final IdGenerator idGenerator;
    private final PaymentInstrumentTokenMapper paymentInstrumentTokenMapper;

    @Override
    public PaymentInstrumentToken findTokenBySubmission(long clientId, long intentId, long sourceSubmissionId) {
        return paymentInstrumentTokenMapper.findTokenBySubmission(clientId, intentId, sourceSubmissionId);
    }

    @Override
    public void insertToken(PaymentInstrumentToken token) {
        token.setPaymentInstrumentTokenId(idGenerator.nextId());
        if (paymentInstrumentTokenMapper.insertToken(token) != 1) {
            throw new ConcurrentRequestException("支付意图或工具申请条件已变更");
        }
    }

    /** 查询新受理提供的工具。 */
    @Override
    public PaymentInstrumentToken findTokenByValue(String token) {
        return paymentInstrumentTokenMapper.findTokenByValue(token);
    }

    /** 查询原 Attempt 使用的工具。 */
    @Override
    public PaymentInstrumentToken findToken(long tokenId) {
        return paymentInstrumentTokenMapper.findToken(tokenId);
    }

    /** 锁定仍有效且归属未变的工具。 */
    @Override
    public void lockAvailableToken(PaymentInstrumentToken token) {
        if (paymentInstrumentTokenMapper.lockAvailableToken(token) == null) {
            throw new ConcurrentRequestException("支付工具已失效或变更");
        }
    }
}
