package com.paytech.commercial.auto.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.commercial.api.enums.PriceBenchmarkStatus;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponentBand;
import com.paytech.commercial.domain.PriceBenchmark;
import com.paytech.commercial.domain.enums.CommercialTermComponentComponentType;
import com.paytech.commercial.test.builder.CommercialTermComponentBandBuilder;
import com.paytech.commercial.test.builder.CommercialTermComponentBuilder;

/**
 * 破标比较的公开静态合同（开通与定价模型调整 technique.md §4.5）：费率 / 固定额 / 最低收费 / 最高收费与阶梯逐档规则
 * <li>HTTP 用例覆盖费率与最低收费为空；这里固定其余分支，便于定位比较规则本身的回归。</li>
 *
 * @see PriceBenchmark#compare(CommercialTermComponent, List, CommercialTermComponent, List)
 */
class PriceBenchmarkTest {
    /** 固定额低于模板、最高收费低于模板为破标；最高收费为空不算破标；组件类型不同或无模板组件无标准可比。 */
    @Test
    void test_flatComponents() {
        CommercialTermComponent standardFixed = fixed("1.00");
        CommercialTermComponent standardPercent = percentage("0.02", null, "5.00");

        assertAll(() -> assertEquals(PriceBenchmarkStatus.BELOW_STANDARD,
                PriceBenchmark.compare(fixed("0.90"), List.of(), standardFixed, List.of()).getStatus()),
            () -> assertEquals(PriceBenchmarkStatus.AT_OR_ABOVE,
                PriceBenchmark.compare(fixed("1.00"), List.of(), standardFixed, List.of()).getStatus()),
            () -> assertEquals(PriceBenchmarkStatus.BELOW_STANDARD,
                PriceBenchmark.compare(percentage("0.02", null, "4.00"), List.of(), standardPercent, List.of()).getStatus()),
            () -> assertEquals(PriceBenchmarkStatus.AT_OR_ABOVE,
                PriceBenchmark.compare(percentage("0.02", null, null), List.of(), standardPercent, List.of()).getStatus()),
            () -> assertEquals(new BigDecimal("5.00"),
                PriceBenchmark.compare(percentage("0.03", null, null), List.of(), standardPercent, List.of())
                    .getStandardMaxAmount()),
            () -> assertEquals(PriceBenchmarkStatus.NO_BENCHMARK,
                PriceBenchmark.compare(fixed("0.10"), List.of(), standardPercent, List.of()).getStatus()),
            () -> assertEquals(PriceBenchmarkStatus.NO_BENCHMARK,
                PriceBenchmark.compare(fixed("0.10"), List.of(), null, List.of()).getStatus()));
    }

    /** 阶梯：档位结构相同时任一档费率低于模板同档为破标；档数或边界不同无标准可比。 */
    @Test
    void test_tieredComponents() {
        CommercialTermComponent tiered = tiered();
        List<CommercialTermComponentBand> standard = List.of(band(1, "0", "100", "0.03"), band(2, "100", null, "0.02"));

        assertAll(() -> assertEquals(PriceBenchmarkStatus.BELOW_STANDARD, PriceBenchmark.compare(tiered,
                List.of(band(1, "0", "100", "0.03"), band(2, "100", null, "0.015")), tiered(), standard).getStatus()),
            () -> assertEquals(PriceBenchmarkStatus.AT_OR_ABOVE, PriceBenchmark.compare(tiered,
                List.of(band(1, "0", "100", "0.035"), band(2, "100", null, "0.02")), tiered(), standard).getStatus()),
            () -> assertEquals(PriceBenchmarkStatus.NO_BENCHMARK, PriceBenchmark.compare(tiered,
                List.of(band(1, "0", "200", "0.03"), band(2, "200", null, "0.02")), tiered(), standard).getStatus()),
            () -> assertEquals(PriceBenchmarkStatus.NO_BENCHMARK, PriceBenchmark.compare(tiered,
                List.of(band(1, "0", null, "0.03")), tiered(), standard).getStatus()));
    }

    /** 固定金额组件。 */
    static CommercialTermComponent fixed(String amount) {
        return CommercialTermComponentBuilder.template().componentType(CommercialTermComponentComponentType.FIXED).rate(null)
            .fixedAmount(new BigDecimal(amount)).currency("USD").build();
    }

    /** 百分比组件，可带上下限。 */
    static CommercialTermComponent percentage(String rate, String min, String max) {
        return CommercialTermComponentBuilder.template().rate(new BigDecimal(rate))
            .minAmount(min == null ? null : new BigDecimal(min)).maxAmount(max == null ? null : new BigDecimal(max))
            .currency("USD").build();
    }

    /** 阶梯组件头部。 */
    static CommercialTermComponent tiered() {
        return CommercialTermComponentBuilder.template().componentType(CommercialTermComponentComponentType.TIERED).rate(null)
            .currency("USD").build();
    }

    /** 一档费率。 */
    static CommercialTermComponentBand band(int bandNo, String lower, String upper, String rate) {
        return CommercialTermComponentBandBuilder.template().bandNo(bandNo).lowerBound(new BigDecimal(lower))
            .upperBound(upper == null ? null : new BigDecimal(upper)).rate(new BigDecimal(rate)).fixedAmount(null).build();
    }
}
