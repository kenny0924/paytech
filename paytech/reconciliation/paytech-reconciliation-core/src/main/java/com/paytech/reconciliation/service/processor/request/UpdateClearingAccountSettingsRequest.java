package com.paytech.reconciliation.service.processor.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import lombok.Builder;
import lombok.Getter;

/** 修改清算账户配置命令：整体替换三项配置与到账银行账户。 */
@Getter
@Builder
public class UpdateClearingAccountSettingsRequest {
    /** 清算账户主键 */
    @NotNull
    private final Long clearingAccountId;

    /** 到账银行账户主键；为空表示解除 */
    private final Long settlementCashAccountId;

    /** 未匹配等待天数 */
    @NotNull
    @PositiveOrZero
    private final Integer unmatchedWaitDays;

    /** 成本容差 */
    @NotNull
    @PositiveOrZero
    private final BigDecimal costToleranceAmount;

    /** 到账宽限天数 */
    @NotNull
    @PositiveOrZero
    private final Integer cashGraceDays;
}
