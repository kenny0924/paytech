package com.paytech.billing.auto.domainservice.cost;

import static com.paytech.billing.test.builder.ChannelCostVersionRequestBuilder.component;
import static com.paytech.billing.test.builder.ChannelCostVersionRequestBuilder.standardComponent;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import com.paytech.billing.core.enums.AmountRoundingMode;
import com.paytech.billing.core.enums.ChannelCostCalculationBase;
import com.paytech.billing.core.enums.ChannelCostComponentType;
import com.paytech.billing.domain.view.ChannelCostEstimate;
import com.paytech.billing.test.base.AbstractChannelCostEstimationTest;
import com.paytech.billing.test.builder.ChannelCostVersionRequestBuilder;

/**
 * 渠道成本估算成功路径：费率 + 固定费、下限与上限约束、多组成合计、HALF_UP 舍入、零基数只收固定费、
 * 按发生时间命中已停用旧版本，以及未切换的 APPROVED 版本不参与。
 *
 * @see com.paytech.billing.domainservice.service.ChannelCostEstimationService#estimate(com.paytech.billing.domain.ChannelCostEstimateCommand)
 */
class ChannelCostEstimationTest extends AbstractChannelCostEstimationTest {
    /** 跨境费组成代码。 */
    private static final String CROSS_BORDER = "CROSS_BORDER";

    /** 2.9% + 0.30：100.00 × 0.029 + 0.30 = 3.20；证据逐值来自命中版本、范围与组成。 */
    @Test
    void test_rateAndFixed() {
        String hash = publish(minutesAgo(1), standardComponent());

        ChannelCostEstimate estimate = estimate(command("100.00"));

        assertHit(estimate, 1, hash, "3.20");
        assertEquals(1, estimate.getComponents().size());
        ChannelCostEstimate.Component component = estimate.getComponents().get(0);
        assertComponent(component, ChannelCostVersionRequestBuilder.PROCESSING, "100.00", "3.20", "3.20");
        assertAll("组成证据",
            () -> assertEquals(ChannelCostComponentType.PROCESSING_FEE, component.getComponentType()),
            () -> assertEquals(ChannelCostCalculationBase.TRANSACTION_AMOUNT, component.getCalculationBase()),
            () -> assertAmount(ChannelCostVersionRequestBuilder.PROCESSING_RATE, component.getRateValue()),
            () -> assertAmount(ChannelCostVersionRequestBuilder.PROCESSING_FIXED, component.getFixedAmount()),
            () -> assertEquals(ChannelCostVersionRequestBuilder.CURRENCY, component.getFixedCurrency()),
            () -> assertNull(component.getMinAmount()), () -> assertNull(component.getMaxAmount()),
            () -> assertEquals(AmountRoundingMode.HALF_UP, component.getRoundingMode()));
    }

    /** 1% × 10.00 = 0.10 低于下限 0.50：取下限。 */
    @Test
    void test_minAmountClamp() {
        String hash = publish(minutesAgo(1),
            component("MIN_FEE", ChannelCostComponentType.PROCESSING_FEE, "0.01", null, "0.50", null));

        ChannelCostEstimate estimate = estimate(command("10.00"));

        assertHit(estimate, 1, hash, "0.50");
        assertComponent(estimate.getComponents().get(0), "MIN_FEE", "10.00", "0.50", "0.50");
    }

    /** 3% × 100.00 = 3.00 高于上限 2.00：取上限。 */
    @Test
    void test_maxAmountClamp() {
        String hash = publish(minutesAgo(1),
            component("MAX_FEE", ChannelCostComponentType.PROCESSING_FEE, "0.03", null, null, "2.00"));

        ChannelCostEstimate estimate = estimate(command("100.00"));

        assertHit(estimate, 1, hash, "2.00");
        assertComponent(estimate.getComponents().get(0), "MAX_FEE", "100.00", "2.00", "2.00");
    }

    /** 处理费 3.20 + 跨境费 1.00 = 4.20；组成按代码升序返回。 */
    @Test
    void test_multiComponentTotal() {
        String hash = publish(minutesAgo(1), standardComponent(),
            component(CROSS_BORDER, ChannelCostComponentType.CROSS_BORDER_FEE, "0.01", null));

        ChannelCostEstimate estimate = estimate(command("100.00"));

        assertHit(estimate, 1, hash, "4.20");
        assertEquals(2, estimate.getComponents().size());
        assertComponent(estimate.getComponents().get(0), CROSS_BORDER, "100.00", "1.00", "1.00");
        assertComponent(estimate.getComponents().get(1), ChannelCostVersionRequestBuilder.PROCESSING, "100.00",
            "3.20", "3.20");
    }

    /** 1% × 10.50 = 0.105：原始金额保留 8 位，HALF_UP 归整为 0.11（HALF_EVEN 会得 0.10）。 */
    @Test
    void test_halfUpRounding() {
        String hash = publish(minutesAgo(1),
            component("ROUNDED_FEE", ChannelCostComponentType.PROCESSING_FEE, "0.01", null));

        ChannelCostEstimate estimate = estimate(command("10.50"));

        assertHit(estimate, 1, hash, "0.11");
        assertComponent(estimate.getComponents().get(0), "ROUNDED_FEE", "10.50", "0.105", "0.11");
    }

    /** 零基数：费率部分为 0，只收固定费 0.30。 */
    @Test
    void test_zeroBasisChargesFixedOnly() {
        String hash = publish(minutesAgo(1), standardComponent());

        ChannelCostEstimate estimate = estimate(command("0.00"));

        assertHit(estimate, 1, hash, "0.30");
        assertComponent(estimate.getComponents().get(0), ChannelCostVersionRequestBuilder.PROCESSING, "0.00",
            "0.30", "0.30");
    }

    /** 第 1 版（2%）被第 2 版（3%）取代后为 INACTIVE：旧发生时间仍命中第 1 版，当前发生时间命中第 2 版。 */
    @Test
    void test_historicalInactiveVersionByOccurredAt() {
        String firstHash = publish(minutesAgo(10),
            component("RATE_FEE", ChannelCostComponentType.PROCESSING_FEE, "0.02", null));
        String secondHash = publish(minutesAgo(5),
            component("RATE_FEE", ChannelCostComponentType.PROCESSING_FEE, "0.03", null));

        ChannelCostEstimate historical = estimate(command("100.00").occurredAt(minutesAgo(7)));
        ChannelCostEstimate current = estimate(command("100.00"));

        assertHit(historical, 1, firstHash, "2.00");
        assertHit(current, 2, secondHash, "3.00");
    }

    /** 未到生效时间的第 2 版仅 APPROVED：即使发生时间晚于其生效时间，仍命中当前 ACTIVE 第 1 版。 */
    @Test
    void test_approvedVersionNotUsed() {
        String activeHash = publish(minutesAgo(1),
            component("RATE_FEE", ChannelCostComponentType.PROCESSING_FEE, "0.02", null));
        publish(tomorrow(), component("RATE_FEE", ChannelCostComponentType.PROCESSING_FEE, "0.03", null));

        ChannelCostEstimate estimate = estimate(command("100.00").occurredAt(tomorrow().plus(1, ChronoUnit.HOURS)));

        assertHit(estimate, 1, activeHash, "2.00");
    }
}
