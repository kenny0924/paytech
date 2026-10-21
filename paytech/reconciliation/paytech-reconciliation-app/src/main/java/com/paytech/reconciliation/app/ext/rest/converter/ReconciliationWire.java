package com.paytech.reconciliation.app.ext.rest.converter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.money.Money;

/** reconciliation HTTP Wire 的公共转换：字符串主键、金额、日期与枚举 Code。 */
public final class ReconciliationWire {
    private ReconciliationWire() {
    }

    /** 可空主键字符串转 Long（格式已由合同校验）。 */
    public static Long id(String value) {
        return value == null ? null : Long.valueOf(value);
    }

    /** 可空主键转字符串。 */
    public static String text(Long value) {
        return value == null ? null : value.toString();
    }

    /** 可空十进制字符串转 BigDecimal（格式已由合同校验）。 */
    public static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    /** 按币种精度补齐的金额字符串；为空时为空。 */
    public static String amount(String currency, BigDecimal value) {
        return value == null ? null : Money.of(currency, value).toAmountString();
    }

    /** 可空枚举 Code。 */
    public static String code(CodeEnum value) {
        return value == null ? null : value.getCode();
    }

    /** 按 Code 解析可空枚举（取值已由合同正则校验）。 */
    public static <E extends Enum<E> & CodeEnum> E parse(Class<E> type, String code) {
        if (code == null) {
            return null;
        }
        for (E constant : type.getEnumConstants()) {
            if (constant.getCode().equals(code)) {
                return constant;
            }
        }
        throw new InvalidParameterException("不支持的取值: " + code);
    }

    /** 可空 YYYY-MM-DD 日期。 */
    public static LocalDate date(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new InvalidParameterException("日期格式非法: " + value);
        }
    }

    /** 日期当天开始的 UTC 时刻；为空时为空。 */
    public static Instant startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
