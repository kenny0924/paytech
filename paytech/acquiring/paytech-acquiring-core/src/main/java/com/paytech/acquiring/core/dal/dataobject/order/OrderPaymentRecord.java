package com.paytech.commerce.core.dal.dataobject.order;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.commerce.domain.enums.PaymentAttemptStatus;
import com.paytech.commerce.domain.enums.PaymentIntentStatus;

/** Orders 用例读取的本库事实，不跨 HTTP 暴露。 */
public record OrderPaymentRecord(
        /** Payment Intent 标识。 */
        long paymentIntentId,
        /** 本地 Intent 状态。 */
        PaymentIntentStatus intentStatus,
        /** 活跃 Attempt；没有时为空。 */
        Long activeAttemptId,
        /** 最近 Attempt；没有时为空。 */
        Long lastAttemptId,
        /** 最近 Attempt 状态；没有时为空。 */
        PaymentAttemptStatus lastAttemptStatus,
        /** 成功 Attempt；没有时为空。 */
        Long successfulAttemptId,
        /** 已成功收取的金额。 */
        BigDecimal capturedAmount,
        /** 投影币种。 */
        String currency,
        /** 最新已应用 Payment 事实时间。 */
        Instant lastEventTime,
        /** 投影版本。 */
        long version,
        /** 本地更新时间。 */
        Instant updateTime) {
}
