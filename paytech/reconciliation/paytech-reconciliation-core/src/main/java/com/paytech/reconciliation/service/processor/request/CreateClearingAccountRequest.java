package com.paytech.reconciliation.service.processor.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import lombok.Builder;
import lombok.Getter;

/** 新建清算账户命令。 */
@Getter
@Builder
public class CreateClearingAccountRequest {
    /** 清算账户编码，全局唯一 */
    @NotBlank
    private final String clearingAccountCode;

    /** 渠道编码 */
    @NotBlank
    private final String channelCode;

    /** 经营主体编号 */
    @NotBlank
    private final String entityNo;

    /** 清算币种 */
    @NotBlank
    private final String currency;

    /** 到账银行账户主键；可为空 */
    private final Long settlementCashAccountId;

    /** 未匹配等待天数 */
    @NotNull
    @PositiveOrZero
    private final Integer unmatchedWaitDays;

    /** 成本容差，非负且不超过币种精度 */
    @NotNull
    @PositiveOrZero
    private final BigDecimal costToleranceAmount;

    /** 到账宽限天数 */
    @NotNull
    @PositiveOrZero
    private final Integer cashGraceDays;
}
