package com.paytech.payment.execution.core.dal.mapper;

import java.math.BigDecimal;
import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.payment.execution.core.dal.dataobject.PaymentAttempt;
import com.paytech.payment.execution.core.enums.AttemptStatus;

/** 支付尝试 SQL 映射 */
@Mapper
public interface PaymentAttemptMapper {

    int activateAction(@Param("attempt") PaymentAttempt attempt, @Param("actionId") long actionId,
        @Param("now") Instant now);

    int actionSuccess(@Param("attempt") PaymentAttempt attempt, @Param("actionId") long actionId,
        @Param("nextActionId") Long nextActionId, @Param("now") Instant now);

    PaymentAttempt findAttemptBySubmission(@Param("sourceSubmissionId") long sourceSubmissionId);

    /** 按支付尝试主键查询。 */
    PaymentAttempt selectById(@Param("paymentAttemptId") Long paymentAttemptId);

    int maxAttemptNo(@Param("intentId") long intentId);

    int insertAttempt(PaymentAttempt attempt);

    int updateToFail(@Param("attempt") PaymentAttempt attempt, @Param("activeActionId") Long activeActionId,
        @Param("now") Instant now);

    int authorizeCompleted(@Param("paymentAttemptId") Long paymentAttemptId,
        @Param("activeActionId") Long activeActionId, @Param("oldStatus") AttemptStatus oldStatus,
        @Param("authorizedAmount") BigDecimal authorizedAmount, @Param("now") Instant now,
        @Param("version") Long version);

    int payCompleted(@Param("paymentAttemptId") Long paymentAttemptId, @Param("activeActionId") Long activeActionId,
        @Param("oldStatus") AttemptStatus oldStatus, @Param("newStatus") AttemptStatus newStatus,
        @Param("payAmount") BigDecimal payAmount, @Param("now") Instant now, @Param("version") Long version);
}
