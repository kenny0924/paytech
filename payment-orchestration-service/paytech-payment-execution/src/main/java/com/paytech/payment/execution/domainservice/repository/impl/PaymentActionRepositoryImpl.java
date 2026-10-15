package com.paytech.payment.execution.domainservice.repository.impl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.payment.execution.core.action.ActionResult;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;
import com.paytech.payment.execution.core.dal.mapper.PaymentActionMapper;
import com.paytech.payment.execution.domainservice.repository.PaymentActionRepository;

import lombok.AllArgsConstructor;

/** 支付动作持久化与对象同步 */
@Repository
@AllArgsConstructor
public class PaymentActionRepositoryImpl implements PaymentActionRepository {
    private final Clock clock;
    final IdGenerator idGenerator;
    private final PaymentActionMapper paymentActionMapper;

    /** 确认动作实际插入。 */
    @Override
    public void insertAction(PaymentAction action) {
        action.setPaymentActionId(idGenerator.nextId());
        if (paymentActionMapper.insertAction(action) != 1) {
            throw new ConcurrentRequestException("支付动作未能创建");
        }
    }

    /** 保存完整执行结果，确认更新成功后同步原动作。 */
    @Override
    public void saveResult(PaymentAction action, ActionResult result) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (paymentActionMapper.saveResult(action, result, now) != 1) {
            throw new ConcurrentRequestException("支付动作执行版本已失效");
        }
        action.setActionStatus(result.getStatus());
        action.setTransactionAssessmentId(result.getTransactionAssessmentId());
        action.setRiskResult(result.getRiskResult());
        action.setAuthenticationRequirement(result.getAuthenticationRequirement());
        action.setInteractionType(result.getInteractionType());
        action.setInteractionExpiresAt(result.getInteractionExpiresAt());
        action.setChannelResponseCode(result.getChannelResponseCode());
        action.setChannelResponseMsg(result.getChannelResponseMsg());
        action.setChannelResultTime(result.getChannelResultTime());
        action.setVersion(action.getVersion() + 1);
        action.setUpdateTime(now);
    }

    /** 读取原动作，不生成新的执行身份。 */
    @Override
    public PaymentAction selectById(Long activeActionId) {
        return paymentActionMapper.selectById(activeActionId);
    }

    /** 按动作序号读取最后一个动作。 */
    @Override
    public PaymentAction findLatestByAttempt(long attemptId) {
        return paymentActionMapper.selectLatestByAttempt(attemptId);
    }
}
