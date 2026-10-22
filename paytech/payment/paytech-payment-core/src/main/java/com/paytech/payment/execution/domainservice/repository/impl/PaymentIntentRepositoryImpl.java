package com.paytech.payment.execution.domainservice.repository.impl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAttempt;
import com.paytech.payment.execution.core.dal.dataobject.PaymentIntent;
import com.paytech.payment.execution.core.dal.mapper.PaymentIntentMapper;
import com.paytech.payment.execution.core.enums.IntentStatus;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;

import lombok.AllArgsConstructor;

/** 支付意图数据访问 */
@Repository
@AllArgsConstructor
public class PaymentIntentRepositoryImpl implements PaymentIntentRepository {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final PaymentIntentMapper paymentIntentMapper;

    @Override
    public PaymentIntent findIntentBySource(String sourceType, long sourceId) {
        return paymentIntentMapper.findIntentBySource(sourceType, sourceId);
    }

    @Override
    public void insertIntent(PaymentIntent intent) {
        intent.setPaymentIntentId(idGenerator.nextId());
        if (paymentIntentMapper.insertIntent(intent) != 1) {
            throw new ConcurrentRequestException("支付意图已过期，无法创建");
        }
    }

    @Override
    public PaymentIntent findIntent(long intentId) {
        return paymentIntentMapper.findIntent(intentId);
    }

    /** 按原版本把意图写为原因对应的终态，更新失败不修改内存对象。 */
    @Override
    public void terminateIntent(PaymentIntent intent, IntentStatus targetStatus) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (paymentIntentMapper.terminateIntent(intent, targetStatus, now) != 1) {
            throw new ConcurrentRequestException("支付意图状态或活动尝试已变更，无法终止");
        }
        intent.setIntentStatus(targetStatus);
        intent.setVersion(intent.getVersion() + 1);
        intent.setUpdateTime(now);
    }

    @Override
    public void bindAttempt(PaymentIntent intent, PaymentAttempt attempt) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (paymentIntentMapper.bindAttempt(intent.getPaymentIntentId(), attempt.getPaymentAttemptId(),
            intent.getVersion(), now) != 1) {
            throw new ConcurrentRequestException("bindAttempt失败");
        }
        intent.setActiveAttemptId(attempt.getPaymentAttemptId());
        intent.setVersion(intent.getVersion() + 1);
        intent.setUpdateTime(attempt.getCreateTime());
    }

    @Override
    public void releaseAttempt(PaymentIntent intent) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int rows = paymentIntentMapper.releaseAttempt(intent.getPaymentIntentId(), intent.getActiveAttemptId(),
            intent.getVersion(), now);
        if (rows != 1) {
            throw new ConcurrentRequestException("更新releaseAttempt失败");
        }
        intent.setActiveAttemptId(null);
        intent.setUpdateTime(now);
        intent.setVersion(intent.getVersion() + 1);
    }

    @Override
    public void attemptSucceed(PaymentIntent intent, long attemptId, IntentStatus newStatus) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (paymentIntentMapper.attemptSucceed(intent, attemptId, newStatus, now) != 1) {
            throw new ConcurrentRequestException("支付意图的当前尝试已变更");
        }
        intent.setIntentStatus(newStatus);
        intent.setActiveAttemptId(null);
        intent.setSuccessfulAttemptId(newStatus == IntentStatus.SUCCEEDED ? attemptId : null);
        intent.setVersion(intent.getVersion() + 1);
        intent.setUpdateTime(now);
    }
}
