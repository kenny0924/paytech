package com.paytech.foundation.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.regex.Pattern;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.money.json.MoneyDeserializer;
import com.paytech.foundation.money.json.MoneySerializer;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

/** 不可变的币种与精确金额；不执行隐式舍入或汇率转换。 */
@JsonSerialize(using = MoneySerializer.class)
@JsonDeserialize(using = MoneyDeserializer.class)
public final class Money implements Comparable<Money> {
    public static final int MAX_PRECISION = 38;
    public static final int MAX_SCALE = 18;
    public static final int MAX_AMOUNT_TEXT_LENGTH = 58;
    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");
    private static final Pattern DECIMAL_TEXT = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?");
    private final String currency;
    private final BigDecimal amount;

    private Money(String currency, BigDecimal amount) {
        Currency recognizedCurrency = validateCurrency(currency);
        validateAmount(amount, recognizedCurrency.getDefaultFractionDigits());
        this.currency = currency;
        this.amount = normalize(amount);
    }

    /** 从普通十进制字符串构建，不接受空白、指数或浮点数。 */
    public static Money of(String currency, String amount) {
        if (amount == null || amount.length() > MAX_AMOUNT_TEXT_LENGTH || !DECIMAL_TEXT.matcher(amount).matches()) {
            throw new InvalidParameterException("金额必须为长度受限的普通十进制字符串");
        }
        return of(currency, new BigDecimal(amount));
    }

    /** 调用方必须使用精确来源构建 BigDecimal，不能从 double 转换。 */
    public static Money of(String currency, BigDecimal amount) {
        return new Money(currency, amount);
    }

    public static Money zero(String currency) {
        return of(currency, BigDecimal.ZERO);
    }

    /** 供外部渠道协议适配使用；第一方 HTTP 金额使用主单位，不调用此工厂解析。 */
    public static Money ofMinorUnits(String currency, long units, int minorUnitScale) {
        if (minorUnitScale < 0 || minorUnitScale > MAX_SCALE) {
            throw new InvalidParameterException("最小货币单位精度超出支持范围");
        }
        return of(currency, BigDecimal.valueOf(units, minorUnitScale));
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    /** 按币种精度输出主单位金额字符串，供独立金额字段和 JSON Codec 复用。 */
    public String toAmountString() {
        return amount.setScale(Currency.getInstance(currency).getDefaultFractionDigits(), RoundingMode.UNNECESSARY)
            .toPlainString();
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return of(currency, amount.add(other.amount));
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return of(currency, amount.subtract(other.amount));
    }

    public Money negate() {
        return of(currency, amount.negate());
    }

    public Money abs() {
        return isNegative() ? negate() : this;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof Money money && currency.equals(money.currency) && amount.equals(money.amount);
    }

    @Override
    public int hashCode() {
        return 31 * currency.hashCode() + amount.hashCode();
    }

    @Override
    public String toString() {
        return currency + " " + amount.toPlainString();
    }

    private void requireSameCurrency(Money other) {
        if (other == null || !currency.equals(other.currency)) {
            throw new InvalidParameterException("金额运算要求非空且币种一致");
        }
    }

    private static Currency validateCurrency(String currency) {
        if (currency == null || !CURRENCY_CODE.matcher(currency).matches()) {
            throw new InvalidParameterException("币种必须为三个大写英文字母");
        }
        Currency recognized;
        try {
            recognized = Currency.getInstance(currency);
        } catch (IllegalArgumentException exception) {
            throw new InvalidParameterException("币种不是可识别的货币代码");
        }
        if (recognized.getDefaultFractionDigits() < 0 || currency.equals("XTS") || currency.equals("XXX")) {
            throw new InvalidParameterException("不支持测试币种、无币种或非普通货币代码");
        }
        return recognized;
    }

    private static void validateAmount(BigDecimal amount, int currencyFractionDigits) {
        if (amount == null || amount.scale() < -MAX_PRECISION || amount.scale() > MAX_SCALE
            || (long)amount.precision() - amount.scale() > MAX_PRECISION) {
            throw new InvalidParameterException("金额精度超出支持范围");
        }
        // 币种补零不增加有效精度，保证最大金额也能按 JSON 输出后无损读回。
        BigDecimal normalized = amount.stripTrailingZeros();
        if (normalized.precision() > MAX_PRECISION) {
            throw new InvalidParameterException("金额精度超出支持范围");
        }
        int effectiveScale = Math.max(0, normalized.scale());
        if (effectiveScale > currencyFractionDigits) {
            throw new InvalidParameterException("金额小数位超过币种支持范围");
        }
    }

    private static BigDecimal normalize(BigDecimal amount) {
        if (amount.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal normalized = amount.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
    }
}
