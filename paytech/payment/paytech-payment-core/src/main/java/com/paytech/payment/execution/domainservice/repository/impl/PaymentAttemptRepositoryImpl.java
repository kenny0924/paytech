package com.paytech.payment.execution.domainservice.repository.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAttempt;
import com.paytech.payment.execution.core.dal.mapper.PaymentAttemptMapper;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;

import lombok.AllArgsConstructor;

/** 支付尝试数据访问 */
@Repository
@AllArgsConstructor
public class PaymentAttemptRepositoryImpl implements PaymentAttemptRepository {
    private final Clock clock;
    final IdGenerator idGenerator;
    private final PaymentAttemptMapper paymentAttemptMapper;

    @Override
    public void bindAction(PaymentAttempt attempt, long actionId) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (paymentAttemptMapper.activateAction(attempt, actionId, now) != 1) {
            throw new ConcurrentRequestException("支付尝试已有当前动作或已变更");
        }
        attempt.setActiveActionId(actionId);
        attempt.setVersion(attempt.getVersion() + 1);
        attempt.setUpdateTime(now);
    }

    @Override
    public void actionSuccess(PaymentAttempt attempt, Long nextActionId) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (paymentAttemptMapper.actionSuccess(attempt, attempt.getActiveActionId(), nextActionId, now) != 1) {
            throw new ConcurrentRequestException("支付尝试执行位已失效");
        }
        attempt.setActiveActionId(nextActionId);
        attempt.setVersion(attempt.getVersion() + 1);
        attempt.setUpdateTime(now);
    }

    @Override
    public void authorizeCompleted(PaymentAttempt attempt, BigDecimal authorizedAmount) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int rows = paymentAttemptMapper.authorizeCompleted(attempt.getPaymentAttemptId(), attempt.getActiveActionId(),
            attempt.getAttemptStatus(), authorizedAmount, now, attempt.getVersion());
        if (rows != 1) {
            throw new ErrorException("authorizeCompleted更新出错");
        }
        attempt.setAuthorizedAmount(authorizedAmount);
        attempt.setActiveActionId(null);
        attempt.setVersion(attempt.getVersion() + 1);
        attempt.setUpdateTime(now);
    }

    @Override
    public void payCompleted(PaymentAttempt attempt, BigDecimal payAmount) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int rows = paymentAttemptMapper.payCompleted(attempt.getPaymentAttemptId(), attempt.getActiveActionId(),
            attempt.getAttemptStatus(), AttemptStatus.SUCCEEDED, payAmount, now, attempt.getVersion());
        if (rows != 1) {
            throw new ErrorException("payCompleted更新出错");
        }
        attempt.setAuthorizedAmount(payAmount);
        attempt.setCapturedAmount(payAmount);
        attempt.setAttemptStatus(AttemptStatus.SUCCEEDED);
        attempt.setActiveActionId(null);
        attempt.setVersion(attempt.getVersion() + 1);
        attempt.setUpdateTime(now);
    }

    /** 按支付尝试 ID 查询。 */
    @Override
    public PaymentAttempt selectById(Long paymentAttemptId) {
        return paymentAttemptMapper.selectById(paymentAttemptId);
    }

    @Override
    public PaymentAttempt findAttemptBySubmission(long sourceSubmissionId) {
        return paymentAttemptMapper.findAttemptBySubmission(sourceSubmissionId);
    }

    @Override
    public int maxAttemptNo(long intentId) {
        return paymentAttemptMapper.maxAttemptNo(intentId);
    }

    @Override
    public void insertAttempt(PaymentAttempt attempt) {
        attempt.setPaymentAttemptId(idGenerator.nextId());
        if (paymentAttemptMapper.insertAttempt(attempt) != 1) {
            throw new ConcurrentRequestException("支付意图或工具已过期，请重新确认支付条件");
        }
    }

    @Override
    public void updateToFail(PaymentAttempt attempt) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (paymentAttemptMapper.updateToFail(attempt, attempt.getActiveActionId(), now) != 1) {
            throw new ConcurrentRequestException("支付尝试状态或当前动作已变更");
        }
        attempt.setAttemptStatus(AttemptStatus.FAILED);
        attempt.setActiveActionId(null);
        attempt.setVersion(attempt.getVersion() + 1);
        attempt.setUpdateTime(now);
    }
}
