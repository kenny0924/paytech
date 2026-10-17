package com.paytech.platform.support.integration.domainservice.function;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.integration.domain.execution.ExecutionErrorException;

/**
 * MONEY_MINOR_UNITS：主单位金额字符串 + ISO 4217 币种 → 最小单位整数。
 *
 * <p>币种精度取 JDK 币种表，不访问外部服务。金额必须是非负十进制字符串（不接受指数）；
 * 超出币种精度的非零小数、未知币种、无小数单位的币种或超出 64 位整数一律失败，不舍入。
 */
@Component
public class MoneyMinorUnitsFunction implements PureFunction {

    /** 注册函数编码。 */
    public static final String CODE = "MONEY_MINOR_UNITS";

    private static final Pattern AMOUNT = Pattern.compile("(0|[1-9][0-9]*)(\\.[0-9]+)?");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Object apply(Map<String, Object> args) {
        BigDecimal amount = amount(args.get("amount"));
        int digits = fractionDigits(args.get("currency"));
        try {
            return amount.movePointRight(digits).longValueExact();
        } catch (ArithmeticException exception) {
            throw new ExecutionErrorException("金额超出币种精度或整数范围");
        }
    }

    /** 非负十进制字符串。 */
    BigDecimal amount(Object value) {
        if (!(value instanceof String text) || !AMOUNT.matcher(text).matches()) {
            throw new ExecutionErrorException("金额必须是非负十进制字符串");
        }
        return new BigDecimal(text);
    }

    /** 已知且有小数单位定义的 ISO 4217 币种的小数位数；MONEY_MAJOR_UNITS 共用。 */
    static int fractionDigits(Object value) {
        if (!(value instanceof String code) || !CURRENCY.matcher(code).matches()) {
            throw new ExecutionErrorException("币种必须是三位大写字母");
        }
        try {
            int digits = Currency.getInstance(code).getDefaultFractionDigits();
            if (digits < 0) {
                throw new ExecutionErrorException("币种没有最小单位定义");
            }
            return digits;
        } catch (IllegalArgumentException exception) {
            throw new ExecutionErrorException("币种未知");
        }
    }
}
