package com.paytech.payment.execution.domainservice.repository;

import com.paytech.payment.execution.core.dal.dataobject.PaymentAttempt;
import com.paytech.payment.execution.core.dal.dataobject.PaymentIntent;
import com.paytech.payment.execution.core.enums.IntentStatus;

/** 支付意图数据访问 */
public interface PaymentIntentRepository {

    /** 按来源查询原支付意图 */
    PaymentIntent findIntentBySource(String sourceType, long sourceId);

    /** 插入支付意图 */
    void insertIntent(PaymentIntent intent);

    /**
     * 查询
     * 
     * @param intentId
     *            支付意图ID
     */
    PaymentIntent findIntent(long intentId);

    /** 终止开放且无活动或成功尝试的意图，成功后同步原对象。 */
    void terminateIntent(PaymentIntent intent);

    /**
     * 绑定支付意图的activeAttempt
     * 
     * @param intent
     *            支付意图
     * @param attempt
     *            支付尝试
     */
    void bindAttempt(PaymentIntent intent, PaymentAttempt attempt);

    /**
     * 当Attempt已经成功
     * 
     * @param intent
     *            支付意图
     * @param attemptId
     *            支付尝试ID
     * @param newStatus
     *            意图状态
     */
    void attemptSucceed(PaymentIntent intent, long attemptId, IntentStatus newStatus);

    /**
     * 释放支付意图的activeAttempt
     * 
     * @param intent
     *            支付意图
     */
    void releaseAttempt(PaymentIntent intent);
}
