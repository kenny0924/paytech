package com.paytech.tax.domain.calculation;

import java.math.BigDecimal;

import com.paytech.tax.core.enums.TaxCalculationMethod;

/**
 * 一行按一个税率计算出的金额
 *
 * @param net 行净额
 * @param tax 取整后税额
 * @param gross 行含税金额 = net + tax
 * @param roundingDelta 取整后税额 − 未取整税额，八位小数
 * @param method 使用的算法
 */
public record LineTaxAmounts(BigDecimal net, BigDecimal tax, BigDecimal gross, BigDecimal roundingDelta,
    TaxCalculationMethod method) {
}
