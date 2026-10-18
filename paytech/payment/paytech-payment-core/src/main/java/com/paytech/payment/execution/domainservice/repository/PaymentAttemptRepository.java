package com.paytech.payment.execution.domainservice.repository;

import java.math.BigDecimal;

import com.paytech.payment.execution.core.dal.dataobject.PaymentAttempt;

/** 支付尝试数据访问 */
public interface PaymentAttemptRepository {

    /**
     * 插入支付企图
     *
     * @param attempt
     *            支付企图
     */
    void insertAttempt(PaymentAttempt attempt);

    /**
     * 绑定activeActionId
     * 
     * @param attempt
     *            支付企图
     * @param activeActionId
     *            actionId
     */
    void bindAction(PaymentAttempt attempt, long activeActionId);

    /**
     * Action执行明确失败
     *
     * @param attempt
     *            支付企图
     */
    void updateToFail(PaymentAttempt attempt);

    /**
     * 根据提交ID查询
     *
     * @param sourceSubmissionId
     *            提交ID
     * @return 支付企图
     */
    PaymentAttempt findAttemptBySubmission(long sourceSubmissionId);

    /**
     * 查询最大尝试编号
     *
     * @param intentId
     *            支付意图
     * @return 最大的尝试编号
     */
    int maxAttemptNo(long intentId);

    /**
     * 当前Action执行完成功
     * 
     * @param attempt
     *            支付意图
     * @param nextActionId
     *            下一个actionId
     */
    void actionSuccess(PaymentAttempt attempt, Long nextActionId);

    /**
     * 授权完成
     * 
     * @param attempt
     *            支付企图
     * @param authorizedAmount
     *            授权金额
     */
    void authorizeCompleted(PaymentAttempt attempt, BigDecimal authorizedAmount);

    /**
     * Pay完成
     * 
     * @param attempt
     *            支付企图
     * @param payAmount
     *            支付金额
     */
    void payCompleted(PaymentAttempt attempt, BigDecimal payAmount);

    /**
     * 按支付尝试 ID 查询
     * 
     * @param paymentAttemptId
     *            支付企图ID
     */
    PaymentAttempt selectById(Long paymentAttemptId);
}
