package com.paytech.platform.support.integration.domainservice.function;

import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.integration.domain.execution.ExecutionErrorException;

/**
 * MONEY_MAJOR_UNITS：最小单位整数字符串 + ISO 4217 币种 → 主单位十进制（MONEY_MINOR_UNITS 的反向）。
 *
 * <p>结果的小数位数等于币种精度（USD {@code "100"} → {@code 1.00}，JPY {@code "100"} → {@code 100}）。
 * 金额必须是非负整数字符串（不接受小数、符号与指数）；币种规则与 MONEY_MINOR_UNITS 相同。
 */
@Component
public class MoneyMajorUnitsFunction implements PureFunction {

    /** 注册函数编码。 */
    public static final String CODE = "MONEY_MAJOR_UNITS";

    private static final Pattern MINOR_AMOUNT = Pattern.compile("0|[1-9][0-9]*");

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Object apply(Map<String, Object> args) {
        if (!(args.get("amount") instanceof String text) || !MINOR_AMOUNT.matcher(text).matches()) {
            throw new ExecutionErrorException("最小单位金额必须是非负整数字符串");
        }
        int digits = MoneyMinorUnitsFunction.fractionDigits(args.get("currency"));
        return new BigDecimal(text).movePointLeft(digits).setScale(digits);
    }
}
