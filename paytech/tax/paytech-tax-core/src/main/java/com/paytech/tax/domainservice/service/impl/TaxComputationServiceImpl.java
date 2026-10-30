package com.paytech.tax.domainservice.service.impl;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import com.paytech.tax.api.enums.TaxPriceMode;
import com.paytech.tax.core.enums.TaxCalculationMethod;
import com.paytech.tax.domain.calculation.LineTaxAmounts;
import com.paytech.tax.domainservice.service.TaxComputationService;

/**
 * TaxComputationService 实现
 * <li>未税：tax = round(base × rate)，gross = base + tax。含税：tax = round(gross × rate / (1 + rate))，net = gross − tax。</li>
 * <li>取整逐行进行，尾差 = 取整后税额 − 未取整税额（按 DECIMAL128 精度计算后保留八位），头级不再分摊。</li>
 */
@Service
public class TaxComputationServiceImpl implements TaxComputationService {
    /** 尾差保存的小数位，与 DECIMAL(24,8) 一致。 */
    static final int DELTA_SCALE = 8;

    /** 无税率时税额为 0；有税率按价格口径分别计算。 */
    @Override
    public LineTaxAmounts computeLine(TaxPriceMode priceMode, BigDecimal lineAmount, BigDecimal rate,
        RoundingMode roundingMode, int scale) {
        if (rate == null) {
            return new LineTaxAmounts(lineAmount, BigDecimal.ZERO, lineAmount, BigDecimal.ZERO, TaxCalculationMethod.NONE);
        }
        if (priceMode == TaxPriceMode.INCLUSIVE) {
            BigDecimal exact = lineAmount.multiply(rate).divide(BigDecimal.ONE.add(rate), MathContext.DECIMAL128);
            BigDecimal tax = exact.setScale(scale, roundingMode);
            return new LineTaxAmounts(lineAmount.subtract(tax), tax, lineAmount, delta(tax, exact),
                TaxCalculationMethod.PERCENTAGE_INCLUSIVE);
        }
        BigDecimal exact = lineAmount.multiply(rate);
        BigDecimal tax = exact.setScale(scale, roundingMode);
        return new LineTaxAmounts(lineAmount, tax, lineAmount.add(tax), delta(tax, exact),
            TaxCalculationMethod.PERCENTAGE_EXCLUSIVE);
    }

    /** 取整尾差，八位小数。 */
    static BigDecimal delta(BigDecimal rounded, BigDecimal exact) {
        return rounded.subtract(exact).setScale(DELTA_SCALE, RoundingMode.HALF_UP);
    }
}
