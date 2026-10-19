package com.paytech.billing.auto.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.billing.core.enums.AmountRoundingMode;
import com.paytech.billing.domain.ChannelCostComponentAmount;
import com.paytech.billing.domain.ChannelCostComponentRule;
import com.paytech.billing.domainservice.service.ChannelCostCalculator;
import com.paytech.billing.domainservice.service.impl.ChannelCostCalculatorImpl;
import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 渠道成本"组件 → 金额"纯计算（纯 Java，无数据库）：百分比、固定费、费率 + 固定费、下限、上限、HALF_UP 舍入、零基数、多组件顺序与非法输入
 * <li>场景与金额沿用原 ChannelCostEstimationTest 的成功路径，只去掉方案 / 版本 / 范围命中部分（已迁 channel-connector）。</li>
 *
 * @see ChannelCostCalculator#calculate(List, BigDecimal, int)
 */
class ChannelCostCalculatorTest {
    /** 收费语义代码（channel-connector 目录，计算器不解释）。 */
    private static final String PROCESSING_FEE = "PROCESSING_FEE";

    /** 计算基数代码（channel-connector 目录，计算器不解释）。 */
    private static final String TRANSACTION_AMOUNT = "TRANSACTION_AMOUNT";

    /** USD 精度。 */
    private static final int USD = 2;

    private final ChannelCostCalculator calculator = new ChannelCostCalculatorImpl();

    /** 2.9% + 0.30：100.00 × 0.029 + 0.30 = 3.20；结果带回组件、基数与费率。 */
    @Test
    void test_rateAndFixed() {
        ChannelCostComponentRule rule = rule("PROCESSING", "0.029", "0.30", null, null);

        ChannelCostComponentAmount amount = calculate("100.00", rule).getFirst();

        assertAll(() -> assertSame(rule, amount.getComponent()),
            () -> assertAmount("100.00", amount.getBasisAmount()),
            () -> assertAmount("0.029", amount.getRateValue()),
            () -> assertEquals(new BigDecimal("3.20000000"), amount.getRawAmount()),
            () -> assertEquals(new BigDecimal("3.20"), amount.getPostedAmount()));
    }

    /** 纯百分比：1.5% × 200.00 = 3.00。 */
    @Test
    void test_percentageOnly() {
        ChannelCostComponentAmount amount = calculate("200.00", rule("PCT", "0.015", null, null, null)).getFirst();

        assertEquals(new BigDecimal("3.00"), amount.getPostedAmount());
    }

    /** 纯固定费：0.25，与基数无关，费率为空。 */
    @Test
    void test_fixedOnly() {
        ChannelCostComponentAmount amount = calculate("999.99", rule("FIXED", null, "0.25", null, null)).getFirst();

        assertAll(() -> assertNull(amount.getRateValue()),
            () -> assertEquals(new BigDecimal("0.25000000"), amount.getRawAmount()),
            () -> assertEquals(new BigDecimal("0.25"), amount.getPostedAmount()));
    }

    /** 1% × 10.00 = 0.10 低于下限 0.50：取下限。 */
    @Test
    void test_minAmountClamp() {
        ChannelCostComponentAmount amount = calculate("10.00", rule("MIN_FEE", "0.01", null, "0.50", null)).getFirst();

        assertAll(() -> assertEquals(new BigDecimal("0.50000000"), amount.getRawAmount()),
            () -> assertEquals(new BigDecimal("0.50"), amount.getPostedAmount()));
    }

    /** 3% × 100.00 = 3.00 高于上限 2.00：取上限。 */
    @Test
    void test_maxAmountClamp() {
        ChannelCostComponentAmount amount = calculate("100.00", rule("MAX_FEE", "0.03", null, null, "2.00")).getFirst();

        assertEquals(new BigDecimal("2.00"), amount.getPostedAmount());
    }

    /** 1% × 10.50 = 0.105：原始金额保留 8 位，HALF_UP 归整为 0.11（HALF_EVEN 会得 0.10）。 */
    @Test
    void test_halfUpRounding() {
        ChannelCostComponentAmount amount = calculate("10.50", rule("ROUNDED", "0.01", null, null, null)).getFirst();

        assertAll(() -> assertEquals(new BigDecimal("0.10500000"), amount.getRawAmount()),
            () -> assertEquals(new BigDecimal("0.11"), amount.getPostedAmount()));
    }

    /** 原始金额超过 8 位时先按 8 位 HALF_UP：0.123456789 × 1 = 0.12345679。 */
    @Test
    void test_rawAmountEightDecimals() {
        ChannelCostComponentAmount amount = calculate("1.00", rule("FINE", "0.123456789", null, null, null)).getFirst();

        assertAll(() -> assertEquals(new BigDecimal("0.12345679"), amount.getRawAmount()),
            () -> assertEquals(new BigDecimal("0.12"), amount.getPostedAmount()));
    }

    /** 零基数：费率部分为 0，只收固定费 0.30。 */
    @Test
    void test_zeroBasisChargesFixedOnly() {
        ChannelCostComponentAmount amount = calculate("0.00", rule("PROCESSING", "0.029", "0.30", null, null)).getFirst();

        assertEquals(new BigDecimal("0.30"), amount.getPostedAmount());
    }

    /** 多组件按输入顺序返回；零精度币种按整数归整。 */
    @Test
    void test_multipleComponentsKeepOrder() {
        List<ChannelCostComponentAmount> amounts = calculator.calculate(List.of(rule("B_FEE", "0.01", null, null, null),
            rule("A_FEE", null, "1.5", null, null)), new BigDecimal("150"), 0);

        assertAll(() -> assertEquals(2, amounts.size()),
            () -> assertEquals("B_FEE", amounts.get(0).getComponent().getComponentCode()),
            () -> assertEquals(new BigDecimal("2"), amounts.get(0).getPostedAmount()),
            () -> assertEquals("A_FEE", amounts.get(1).getComponent().getComponentCode()),
            () -> assertEquals(new BigDecimal("2"), amounts.get(1).getPostedAmount()));
    }

    /** 组件为空、基数为负或超精度、币种精度越界、缺少舍入方式都拒绝计算，不返回零成本。 */
    @Test
    void test_invalidInputRejected() {
        ChannelCostComponentRule rule = rule("PROCESSING", "0.029", "0.30", null, null);

        assertRejected(() -> calculator.calculate(List.of(), new BigDecimal("1.00"), USD), "渠道成本组件为空");
        assertRejected(() -> calculator.calculate(List.of(rule), new BigDecimal("-0.01"), USD), "计费基数不合法");
        assertRejected(() -> calculator.calculate(List.of(rule), new BigDecimal("1.001"), USD), "计费基数不合法");
        assertRejected(() -> calculator.calculate(List.of(rule), null, USD), "计费基数不合法");
        assertRejected(() -> calculator.calculate(List.of(rule), new BigDecimal("1"), 9), "币种精度不合法");
        ChannelCostComponentRule noRounding = new ChannelCostComponentRule(1L, "NO_ROUNDING", PROCESSING_FEE,
            TRANSACTION_AMOUNT, new BigDecimal("0.01"), null, null, null, null);
        assertRejected(() -> calculator.calculate(List.of(noRounding), new BigDecimal("1.00"), USD), "缺少舍入方式");
    }

    /** 以 USD 精度计算一组组件。 */
    private List<ChannelCostComponentAmount> calculate(String basis, ChannelCostComponentRule... rules) {
        return calculator.calculate(List.of(rules), new BigDecimal(basis), USD);
    }

    /** HALF_UP 组件；空字符串参数为空。 */
    private static ChannelCostComponentRule rule(String code, String rate, String fixed, String min, String max) {
        return new ChannelCostComponentRule(1L, code, PROCESSING_FEE, TRANSACTION_AMOUNT, decimal(rate), decimal(fixed),
            decimal(min), decimal(max), AmountRoundingMode.HALF_UP);
    }

    /** 十进制或空。 */
    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    /** 数值相等（忽略标度）。 */
    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "期望 " + expected + "，实际 " + actual);
    }

    /** 业务拒绝且消息含片段。 */
    private static void assertRejected(Runnable call, String fragment) {
        BizCheckFailException error = assertThrows(BizCheckFailException.class, call::run);
        assertTrue(error.getMessage().contains(fragment), error.getMessage());
    }
}
