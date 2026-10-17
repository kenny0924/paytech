package com.paytech.commerce.domain.order;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.money.Money;

/** Commerce 金额与数量的无符号十进制、正数及 DECIMAL(24,8) 业务边界，不持有币种或金额状态。 */
public final class CommerceDecimalRules {
    private static final Pattern DECIMAL = Pattern.compile("(?:0|[1-9][0-9]{0,15})(?:\\.[0-9]{1,8})?");

    /** 业务规则只提供纯静态操作。 */
    private CommerceDecimalRules() {}

    /** 解析非负金额文本；保留原接口的位数限制，并规范无意义的尾随零。 */
    public static BigDecimal parseNonNegative(String value) {
        if (value == null || !DECIMAL.matcher(value).matches()) {
            throw new IllegalArgumentException("金额或数量必须是范围内的无符号纯十进制字符串");
        }
        BigDecimal amount = new BigDecimal(value);
        return amount.signum() == 0 ? BigDecimal.ZERO : amount.stripTrailingZeros();
    }

    /** 数量和退款请求必须为正数；币种精度在取得实际币种后由公共 Money 校验。 */
    public static BigDecimal parsePositive(String value) {
        BigDecimal amount = parseNonNegative(value);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("金额或数量必须为正数");
        }
        return amount;
    }

    /** 校验计算或持久化金额的非负性与数据库范围，不隐式截断或舍入。 */
    public static void requireRange(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("金额不能为空");
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (value.signum() < 0 || normalized.scale() > 8
            || normalized.precision() - normalized.scale() > 16) {
            throw new IllegalArgumentException("金额或数量超出 DECIMAL(24,8) 范围");
        }
    }

    /**
     * 将本库非负金额按币种精度输出为主单位字符串，订单查询与 Webhook 对象共用同一格式。
     *
     * @return 金额为空时返回 null
     * @throws ErrorException 持久化金额与币种精度不一致
     */
    public static String toAmountString(String currency, BigDecimal value) {
        if (value == null) {
            return null;
        }
        try {
            return Money.of(currency, parseNonNegative(value.toPlainString())).toAmountString();
        } catch (IllegalArgumentException | InvalidParameterException exception) {
            throw new ErrorException("订单金额币种精度不一致", exception);
        }
    }
}
