package com.paytech.commerce.core.dal.dataobject.order;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.commerce.domain.enums.FundsAvailabilityStatus;

/** Orders 用例读取的本库事实，不跨 HTTP 暴露。 */
public record OrderFundsRecord(
        /** 资金可用状态。 */
        FundsAvailabilityStatus status,
        /** 已可用金额；未确认为空。 */
        BigDecimal availableAmount,
        /** 资金币种。 */
        String currency,
        /** Finance 冻结的预计可用时间。 */
        Instant scheduledAt,
        /** Finance 确认的可用时间。 */
        Instant availableAt,
        /** 投影已应用事实时点。 */
        Instant asOf,
        /** 投影版本。 */
        long version,
        /** 本地更新时间。 */
        Instant updateTime) {
}
