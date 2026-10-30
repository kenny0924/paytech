package com.paytech.tax.app.ext.rest.converter;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.paytech.foundation.money.Money;

/** 响应数值格式：金额按币种精度补齐，税率、数量、汇率去无意义尾零；空值保持为空。 */
final class TaxAmounts {

    /** 纯静态格式化。 */
    private TaxAmounts() {
    }

    /** 按币种精度输出主单位金额字符串。 */
    static String amount(String currency, BigDecimal value) {
        return value == null ? null : Money.of(currency, value.stripTrailingZeros()).toAmountString();
    }

    /** 去尾零的普通十进制字符串。 */
    static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    /** 十进制 ID。 */
    static String id(Long value) {
        return value == null ? null : value.toString();
    }

    /** yyyy-MM-dd。 */
    static String date(LocalDate value) {
        return value == null ? null : value.toString();
    }
}
