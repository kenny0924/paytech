package com.paytech.payment.execution.core.dal.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.payment.execution.core.dal.dataobject.PaymentIntent;
import com.paytech.payment.execution.core.enums.IntentStatus;

/** 支付意图 SQL 映射 */
@Mapper
public interface PaymentIntentMapper {

    /** 按来源查询原支付意图 */
    PaymentIntent findIntentBySource(@Param("sourceType") String sourceType, @Param("sourceId") long sourceId);

    /** 插入仍在有效期内的支付意图 */
    int insertIntent(PaymentIntent intent);

    /** 查询 Intent 完整持久化事实。 */
    PaymentIntent findIntent(@Param("intentId") long intentId);

    /** 仅终止原版本仍开放且没有活动或成功尝试的意图，写入指定终态。 */
    int terminateIntent(@Param("intent") PaymentIntent intent, @Param("status") IntentStatus status,
        @Param("now") Instant now);

    /** 条件更新 Intent 的活动尝试与版本。 */
    int bindAttempt(@Param("paymentIntentId") Long paymentIntentId, @Param("activeAttemptId") Long activeAttemptId,
        @Param("version") Long version, @Param("now") Instant now);

    /** 按原 Intent 版本和 Attempt 身份释放占位。 */
    int attemptSucceed(@Param("intent") PaymentIntent intent, @Param("attemptId") long attemptId,
        @Param("intentStatus") IntentStatus intentStatus, @Param("now") Instant now);

    int releaseAttempt(@Param("paymentIntentId") Long paymentIntentId, @Param("activeAttemptId") Long activeAttemptId,
        @Param("version") Long version, @Param("now") Instant now);
}
