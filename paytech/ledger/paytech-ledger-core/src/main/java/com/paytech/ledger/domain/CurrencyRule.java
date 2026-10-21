package com.paytech.ledger.domain;

import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Platform 币种规则在 ledger 内的只读表示；来自进程内快照，本服务不持久化
 * <li>Owner 为 platform-support；ledger 只读取精度、舍入方式与状态。</li>
 */
@Getter
@AllArgsConstructor
public class CurrencyRule {
    /** ISO 4217 三位大写币种代码。 */
    private final String currency;

    /**
     * 金额允许保留的小数位数
     * <li>0..8。</li>
     */
    private final int minorUnit;

    /** 金额归整到币种精度时的舍入方式。 */
    private final CurrencyRoundingMode roundingMode;

    /**
     * Platform 状态
     * <li>ACTIVE 允许新用途；INACTIVE 只拦新用途，已有账簿照常使用；DRAFT 从未生效。</li>
     */
    private final CurrencySpecStatus status;

    /** 启用过的币种（ACTIVE 或 INACTIVE）可用于已有账簿的过账、调整、试算、关账与查询。 */
    public boolean isUsable() {
        return status == CurrencySpecStatus.ACTIVE || status == CurrencySpecStatus.INACTIVE;
    }
}
