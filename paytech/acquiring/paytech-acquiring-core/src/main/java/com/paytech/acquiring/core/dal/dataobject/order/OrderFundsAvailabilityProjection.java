package com.paytech.acquiring.core.dal.dataobject.order;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.acquiring.domain.enums.FundsAvailabilityStatus;

import lombok.Builder;

/** 单笔 Order 资金成熟事实的本地投影。 */
@Builder
public record OrderFundsAvailabilityProjection(
    /** 本库预生成的资金投影主键。 */
    long orderFundsAvailabilityProjectionId,
    /** 所属 Commerce Order 主键。 */
    long orderId,
    /** Finance Economics 标识；尚未计算时为空。 */ Long financeEconomicsId,
    /** Finance 成熟 Journal 标识；尚未成熟时为空。 */ Long maturityJournalId,
    /** 当前资金成熟闭合状态。 */ FundsAvailabilityStatus fundsAvailabilityStatus,
    /** 已成熟进入 Client 可用余额的金额；未成熟时为空。 */ BigDecimal availableAmount,
    /** ISO 4217 三位币种代码。 */ String currency,
    /** Finance 冻结的计划可用时刻；未知时为空。 */ Instant scheduledAt,
    /** Finance 确认资金可用的时刻；未成熟时为空。 */ Instant availableAt,
    /** EconomicsCalculated 大小写敏感事件键；未消费时为空。 */ String economicsEventKey,
    /** EconomicsCalculated 来源版本；未消费时为空。 */ Long economicsSourceVersion,
    /** EconomicsCalculated 来源载荷 Hash；未消费时为空。 */ String economicsSourcePayloadHash,
    /** PayableBalanceMatured 大小写敏感事件键；未消费时为空。 */ String maturityEventKey,
    /** PayableBalanceMatured 来源版本；未消费时为空。 */ Long maturitySourceVersion,
    /** PayableBalanceMatured 来源载荷 Hash；未消费时为空。 */ String maturitySourcePayloadHash,
    /** 已应用来源事实 occurredAt 的最大值。 */ Instant asOf,
    /** 投影条件更新使用的单调版本。 */ long version,
    /** 本记录创建 UTC 时刻。 */ Instant createTime,
    /** 本记录最后更新 UTC 时刻。 */ Instant updateTime
) {
}
