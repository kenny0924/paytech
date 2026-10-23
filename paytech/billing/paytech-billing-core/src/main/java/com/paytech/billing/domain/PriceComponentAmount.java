package com.paytech.billing.domain;

import java.math.BigDecimal;

import com.paytech.billing.api.enums.CalculationMethod;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一个 commercial 价格组件的计算结果，字段与明细行 basis / rate / raw / posted 列及计算证据一一对应。 */
@Getter
@AllArgsConstructor
public class PriceComponentAmount {
    /** 被计算的组件 */
    private final CommercialPriceComponent component;

    /** 本组件采用的计算方法 */
    private final CalculationMethod calculationMethod;

    /** 实际使用的计费基数；FIXED_PER_EVENT 记事实毛额 */
    private final BigDecimal basisAmount;

    /**
     * 实际使用的费率
     * <li>FIXED 与累进阶梯为空。</li>
     */
    private final BigDecimal rateValue;

    /**
     * 基数所在档位
     * <li>非 TIERED 为空。</li>
     */
    private final CommercialPriceBand band;

    /** 受上下限约束后、按 8 位小数的原始金额 */
    private final BigDecimal rawAmount;

    /** 按币种精度 HALF_UP 归整后的金额，可能为零 */
    private final BigDecimal postedAmount;
}
