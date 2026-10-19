package com.paytech.billing.domain;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一个渠道成本组件的计算结果，字段与计费事实明细行的 basis / rate / raw / posted 列一一对应。 */
@Getter
@AllArgsConstructor
public class ChannelCostComponentAmount {
    /** 被计算的组件。 */
    private final ChannelCostComponentRule component;

    /** 实际使用的计费基数。 */
    private final BigDecimal basisAmount;

    /**
     * 实际使用的费率
     * <li>纯固定费组件为空。</li>
     */
    private final BigDecimal rateValue;

    /** 受上下限约束后、按 8 位小数的原始金额。 */
    private final BigDecimal rawAmount;

    /** 按币种精度与组件舍入方式归整后的金额。 */
    private final BigDecimal postedAmount;
}
