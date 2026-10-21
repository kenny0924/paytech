package com.paytech.reconciliation.domain;

import java.math.BigDecimal;
import java.util.Currency;

import com.paytech.foundation.common.error.BizCheckFailException;

/** 金额精度检查：主货币单位十进制不得超过币种精度（第一方金额格式规范 §2.1）。 */
public final class Amounts {
    private Amounts() {
    }

    /**
     * 金额小数位不超过币种精度，否则业务拒绝
     *
     * @param currency 币种
     * @param amount 金额
     * @param label 带业务上下文的字段说明
     */
    public static void checkPrecision(String currency, BigDecimal amount, String label) {
        int digits = Currency.getInstance(currency).getDefaultFractionDigits();
        if (amount.stripTrailingZeros().scale() > digits) {
            throw new BizCheckFailException(label + " 的小数位超过币种 " + currency + " 的精度 " + digits + ": "
                + amount.toPlainString());
        }
    }
}
