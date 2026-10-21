package com.paytech.billing.domain;

import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一条币种规则：Platform 币种主数据（t_currency_spec）在本服务内存快照中的形态
 * <li>Owner 为 platform-support；本服务不持久化，只经 {@code CurrencySpecQueryApi} 整体读取后放入快照。</li>
 */
@Getter
@AllArgsConstructor
public class CurrencyRule {
    /** ISO 4217 三位大写币种代码。 */
    private final String currency;

    /** 金额允许保留的小数位数，0..8。 */
    private final int minorUnit;

    /** 金额归整到币种精度的方式。 */
    private final CurrencyRoundingMode roundingMode;

    /** Platform 状态：DRAFT 从未生效；ACTIVE、INACTIVE 为启用过。 */
    private final CurrencySpecStatus status;

    /** 是否启用过（ACTIVE 或 INACTIVE）：已有事实与登记按启用过判断，停用只拦截新用途（Q2）。 */
    public boolean usable() {
        return status == CurrencySpecStatus.ACTIVE || status == CurrencySpecStatus.INACTIVE;
    }
}
