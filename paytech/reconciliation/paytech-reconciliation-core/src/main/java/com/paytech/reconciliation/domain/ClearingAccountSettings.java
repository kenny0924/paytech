package com.paytech.reconciliation.domain;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 清算账户的三项配置与到账银行账户（整体替换，设计 Q7）。 */
@Getter
@AllArgsConstructor
public class ClearingAccountSettings {
    /** 到账银行账户主键；为空表示未配置 */
    private final Long settlementCashAccountId;

    /** 账单行未命中平台流水的等待天数 */
    private final Integer unmatchedWaitDays;

    /** 单笔平台流水实际渠道费与预估之差的容差 */
    private final BigDecimal costToleranceAmount;

    /** 预期银行流水到账宽限天数 */
    private final Integer cashGraceDays;
}
