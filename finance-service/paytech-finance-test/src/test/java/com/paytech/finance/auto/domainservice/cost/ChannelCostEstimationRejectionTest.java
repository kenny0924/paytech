package com.paytech.finance.auto.domainservice.cost;

import static com.paytech.finance.test.builder.ChannelCostVersionRequestBuilder.standardComponent;

import org.junit.jupiter.api.Test;

import com.paytech.finance.test.base.AbstractChannelCostEstimationTest;

/**
 * 渠道成本估算 Fail-Closed：无方案或方案未启用、无覆盖发生时间的版本、无命中范围、币种未配置、计费基数为负或超币种精度；
 * 任一不满足即拒绝，不返回零成本。
 *
 * @see com.paytech.finance.accounting.domainservice.service.ChannelCostEstimationService#estimate(com.paytech.finance.accounting.domain.ChannelCostEstimateCommand)
 */
class ChannelCostEstimationRejectionTest extends AbstractChannelCostEstimationTest {
    /** 方案不存在。 */
    private static final String PLAN_MISSING = "渠道成本方案不存在或未启用";

    /** 无覆盖发生时间的版本。 */
    private static final String VERSION_MISSING = "无覆盖发生时间的渠道成本版本";

    /** 无命中范围。 */
    private static final String SCOPE_MISSING = "无匹配的渠道成本适用范围";

    /** 渠道没有任何成本方案。 */
    @Test
    void test_noPlanRejected() {
        publish(minutesAgo(1), standardComponent());

        assertRejected(command("100.00").channelId(799999L), PLAN_MISSING);
    }

    /** 方案仍为 DRAFT（尚无生效版本）。 */
    @Test
    void test_draftPlanRejected() {
        assertRejected(command("100.00"), PLAN_MISSING);
    }

    /** 发生时间早于唯一生效版本的生效时间。 */
    @Test
    void test_noCoveringVersionRejected() {
        publish(minutesAgo(10), standardComponent());

        assertRejected(command("100.00").occurredAt(minutesAgo(20)), VERSION_MISSING);
    }

    /** 动作类别或国家与唯一范围不一致。 */
    @Test
    void test_noScopeMatchRejected() {
        publish(minutesAgo(1), standardComponent());

        assertRejected(command("100.00").actionType("REFUND"), SCOPE_MISSING);
        assertRejected(command("100.00").countryCode("SA"), SCOPE_MISSING);
    }

    /** 币种未在币种规则中配置。 */
    @Test
    void test_currencyNotConfiguredRejected() {
        publish(minutesAgo(1), standardComponent());

        assertRejected(command("100.00").currency("EUR"), "币种未配置或未启用");
    }

    /** 计费基数小数位超过 USD 精度 2 或为负。 */
    @Test
    void test_invalidBasisRejected() {
        publish(minutesAgo(1), standardComponent());

        assertRejected(command("10.001"), "计费基数不合法");
        assertRejected(command("-1.00"), "计费基数不合法");
    }
}
