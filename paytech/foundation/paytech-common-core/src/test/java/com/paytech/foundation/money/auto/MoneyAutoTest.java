package com.paytech.foundation.money.auto;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.money.Money;

/** 金额校验、等价关系与精确运算反例。 */
class MoneyAutoTest {

    @Test
    void test_factoriesAreExactAndScaleIndependent() {
        Money value = Money.of("USD", "1.00");
        Assertions.assertEquals(Money.of("USD", new BigDecimal("1.0")), value);
        Assertions.assertEquals(Money.of("USD", "1").hashCode(), value.hashCode());
        Assertions.assertEquals(1, new HashSet<>(List.of(value, Money.of("USD", "1.0"), Money.of("USD", "1"))).size());
        Assertions.assertEquals("USD", value.getCurrency());
        Assertions.assertEquals(BigDecimal.ONE, value.getAmount());
        Assertions.assertEquals("USD 1", value.toString());
        Assertions.assertEquals(0, value.compareTo(Money.of("USD", "1.000")));
        Assertions.assertNotEquals(value, Money.of("AED", "1"));
        Assertions.assertNotEquals(value, null);
        Assertions.assertNotEquals(value, "USD 1");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"usd", " USD", "USD ", "US", "USDD", "ZZZ", "XXX", "XTS", "XAU", "123"})
    void test_invalidCurrencyIsRejected(String currency) {
        Assertions.assertThrows(InvalidParameterException.class, () -> Money.of(currency, "1"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" 1", "1 ", "+1", "01", ".1", "1.", "1e2", "1E+2", "NaN", "Infinity", "1,000.00", "--1",
        "١", "1\n", "0.0000000000000000001", "999999999999999999999999999999999999999"})
    void test_invalidDecimalTextIsRejected(String amount) {
        Assertions.assertThrows(InvalidParameterException.class, () -> Money.of("USD", amount));
    }

    @Test
    void test_amountMustRespectCurrencyFractionDigits() {
        Assertions.assertEquals(Money.of("USD", "1.23"), Money.of("USD", "1.2300"));
        Assertions.assertEquals(Money.of("JPY", "1"), Money.of("JPY", "1.000"));
        Assertions.assertEquals(Money.of("KWD", "1.123"), Money.of("KWD", "1.1230"));

        Assertions.assertThrows(InvalidParameterException.class, () -> Money.of("USD", "1.001"));
        Assertions.assertThrows(InvalidParameterException.class, () -> Money.of("JPY", "1.1"));
        Assertions.assertThrows(InvalidParameterException.class, () -> Money.of("KWD", "1.0001"));
        Assertions.assertThrows(InvalidParameterException.class,
            () -> Money.of("USD", new BigDecimal("0.00000001")));
    }

    @Test
    void test_bigDecimalBoundsAreCheckedBeforeNormalization() {
        Assertions.assertThrows(InvalidParameterException.class, () -> Money.of("USD", (BigDecimal)null));
        for (BigDecimal amount : new BigDecimal[] {new BigDecimal("1E+38"), new BigDecimal("1E-19"),
            new BigDecimal("0E-2147483647"), new BigDecimal("1E+2147483647")}) {
            Assertions.assertThrows(InvalidParameterException.class, () -> Money.of("USD", amount));
        }
        Assertions.assertThrows(InvalidParameterException.class, () -> Money.of("USD", "0".repeat(10000)));
        Assertions.assertEquals("1000", Money.of("USD", new BigDecimal("1E+3")).getAmount().toPlainString());
        Assertions.assertEquals(38, Money.of("USD", "9".repeat(38)).getAmount().precision());
    }

    @Test
    void test_currencyPaddingDoesNotIncreaseSignificantPrecision() {
        String maximumInteger = "9".repeat(Money.MAX_PRECISION);
        Assertions.assertEquals(Money.of("USD", maximumInteger), Money.of("USD", maximumInteger + ".00"));
        Assertions.assertThrows(InvalidParameterException.class,
            () -> Money.of("USD", "9".repeat(37) + ".99"));
        Assertions.assertThrows(InvalidParameterException.class,
            () -> Money.of("KWD", "9".repeat(36) + ".999"));
    }

    @Test
    void test_arithmeticIsImmutableAndDoesNotRound() {
        Money left = Money.of("USD", "0.10");
        Money right = Money.of("USD", "0.20");
        Assertions.assertEquals(Money.of("USD", "0.30"), left.add(right));
        Assertions.assertEquals(Money.of("USD", "-0.10"), left.subtract(right));
        Assertions.assertEquals(Money.of("USD", "0.10"), left);
        Assertions.assertTrue(left.isPositive());
        Assertions.assertTrue(left.negate().isNegative());
        Assertions.assertEquals(left, left.negate().abs());
        Assertions.assertEquals(left, left.abs());
        Assertions.assertTrue(Money.zero("USD").isZero());
        Assertions.assertFalse(Money.zero("USD").isPositive());
        Assertions.assertFalse(Money.zero("USD").isNegative());
        Assertions.assertEquals(Money.zero("USD"), Money.of("USD", "-0.000"));
        Assertions.assertTrue(left.compareTo(right) < 0);
    }

    @Test
    void test_currencyMismatchAndOverflowFailClosed() {
        Money usd = Money.of("USD", "1");
        Money aed = Money.of("AED", "1");
        Assertions.assertThrows(InvalidParameterException.class, () -> usd.add(aed));
        Assertions.assertThrows(InvalidParameterException.class, () -> usd.subtract(aed));
        Assertions.assertThrows(InvalidParameterException.class, () -> usd.compareTo(aed));
        Assertions.assertThrows(InvalidParameterException.class, () -> usd.add(null));
        Assertions.assertThrows(InvalidParameterException.class, () -> usd.subtract(null));
        Assertions.assertThrows(InvalidParameterException.class, () -> usd.compareTo(null));
        Assertions.assertThrows(InvalidParameterException.class, () -> Money.of("USD", "9".repeat(38)).add(usd));
    }

    @Test
    void test_minorUnitsUseExplicitScaleWithinCurrencyLimit() {
        Assertions.assertEquals(Money.of("USD", "1.23"), Money.ofMinorUnits("USD", 123, 2));
        Assertions.assertEquals(Money.of("JPY", "123"), Money.ofMinorUnits("JPY", 123, 0));
        Assertions.assertEquals(Money.of("KWD", "0.123"), Money.ofMinorUnits("KWD", 123, 3));
        Assertions.assertEquals(Money.of("USD", "-92233720368547758.08"), Money.ofMinorUnits("USD", Long.MIN_VALUE, 2));
        Assertions.assertThrows(InvalidParameterException.class, () -> Money.ofMinorUnits("USD", 1, -1));
        Assertions.assertThrows(InvalidParameterException.class, () -> Money.ofMinorUnits("USD", 1, 19));
        Assertions.assertThrows(InvalidParameterException.class, () -> Money.ofMinorUnits("USD", 1, 3));
        Assertions.assertThrows(InvalidParameterException.class, () -> Money.ofMinorUnits("JPY", 1, 1));
    }

    @Test
    void test_noPublicConstructorOrFloatingPointFactory() {
        Assertions.assertEquals(0, Money.class.getConstructors().length);
        for (var method : Money.class.getDeclaredMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                Assertions.assertNotEquals(double.class, parameter);
                Assertions.assertNotEquals(float.class, parameter);
            }
        }
    }
}
