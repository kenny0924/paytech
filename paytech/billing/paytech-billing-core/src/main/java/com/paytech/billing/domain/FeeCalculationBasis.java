package com.paytech.billing.domain;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 计算一个 commercial 价格时可用的基数与币种精度（技术方案 4.6.1）
 * <li>clientFeeAmount 为已算出的 Σ CLIENT_FEE 记账金额；计算 Client 定价本身时为空，此时 ACTUAL_CLIENT_FEE / NET_AMOUNT 基数属于自引用。</li>
 */
@Getter
@AllArgsConstructor
public class FeeCalculationBasis {
    /** 事实毛额（capturedAmount），GROSS_AMOUNT 基数 */
    private final BigDecimal grossAmount;

    /**
     * Σ CLIENT_FEE 记账金额
     * <li>Client 定价计算时为空。</li>
     */
    private final BigDecimal clientFeeAmount;

    /** 事实币种：固定金额、上下限与阶梯固定金额必须同币种 */
    private final String currency;

    /** 事实币种精度 0–8 */
    private final int minorUnit;

    /** Client 定价计算：没有可引用的 Client 手续费。 */
    public static FeeCalculationBasis clientPricing(BigDecimal grossAmount, String currency, int minorUnit) {
        return new FeeCalculationBasis(grossAmount, null, currency, minorUnit);
    }
}
