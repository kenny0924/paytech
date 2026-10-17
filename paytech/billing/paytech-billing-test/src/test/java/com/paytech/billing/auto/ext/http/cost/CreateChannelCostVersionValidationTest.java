package com.paytech.billing.auto.ext.http.cost;

import static com.paytech.billing.test.builder.ChannelCostVersionRequestBuilder.component;
import static com.paytech.billing.test.builder.ChannelCostVersionRequestBuilder.scope;
import static com.paytech.billing.test.builder.ChannelCostVersionRequestBuilder.standardComponent;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.billing.core.enums.ChannelCostComponentType;
import com.paytech.billing.test.base.AbstractChannelCostApiTest;
import com.paytech.billing.test.builder.ChannelCostVersionRequestBuilder;

/**
 * 创建渠道成本版本的内容校验：费率区间、费率与固定费、固定费币种、上下限、币种精度与启用、范围/组成唯一性、空集合、
 * 非法目录代码与方案不存在；被拒时不新增任何版本。
 *
 * @see com.paytech.billing.api.ChannelCostPlanApi#createChannelCostVersion(String, com.paytech.billing.api.request.CreateChannelCostVersionRequestBody)
 */
class CreateChannelCostVersionValidationTest extends AbstractChannelCostApiTest {
    /** 未在 t_currency_spec 配置的币种。 */
    private static final String UNCONFIGURED_CURRENCY = "EUR";

    /** 本类公共配置：标准 DRAFT 方案。 */
    @BeforeEach
    void setUpClassMocks() {
        createStandardPlan();
    }

    /** 每例被拒后方案下没有任何版本。 */
    @AfterEach
    void assertNoVersionCreated() {
        assertEquals(0, testMapper.countChannelCostVersions(PLAN_CODE), "不应新增版本");
    }

    /** 费率大于 1。 */
    @Test
    void test_rateAboveOneRejected() {
        ChannelCostVersionRequestBuilder builder = version();
        builder.component(0).setRateValue("1.01");

        createVersion(builder).assertBusinessRejected("成本组成费率必须在 0 到 1 之间")
            .assertDetailContains(ChannelCostVersionRequestBuilder.PROCESSING);
    }

    /** 费率与固定费都为空。 */
    @Test
    void test_rateAndFixedBothMissingRejected() {
        ChannelCostVersionRequestBuilder builder = version()
            .components(component("EMPTY_FEE", ChannelCostComponentType.PROCESSING_FEE, null, null));

        createVersion(builder).assertBusinessRejected("成本组成费率与固定费至少提供一项").assertDetailContains("EMPTY_FEE");
    }

    /** 固定费缺少币种。 */
    @Test
    void test_fixedAmountWithoutCurrencyRejected() {
        ChannelCostVersionRequestBuilder builder = version();
        builder.component(0).setCurrency(null);

        createVersion(builder).assertBusinessRejected("固定费与固定费币种必须同时提供或同时为空");
    }

    /** 固定费币种与范围币种不一致（本期不换汇）。 */
    @Test
    void test_fixedCurrencyMismatchRejected() {
        ChannelCostVersionRequestBuilder builder = version();
        builder.component(0).setCurrency(UNCONFIGURED_CURRENCY);

        createVersion(builder).assertBusinessRejected("固定费币种必须等于适用范围币种")
            .assertDetailContains(UNCONFIGURED_CURRENCY);
    }

    /** 下限大于上限。 */
    @Test
    void test_minAboveMaxRejected() {
        ChannelCostVersionRequestBuilder builder = version().components(
            component("CAPPED_FEE", ChannelCostComponentType.PROCESSING_FEE, "0.029", null, "5.00", "1.00"));

        createVersion(builder).assertBusinessRejected("成本组成下限不能大于上限").assertDetailContains("CAPPED_FEE");
    }

    /** 固定费小数位超过 USD 精度 2。 */
    @Test
    void test_amountOverCurrencyPrecisionRejected() {
        ChannelCostVersionRequestBuilder builder = version();
        builder.component(0).setFixedAmount("0.301");

        createVersion(builder).assertBusinessRejected("成本组成金额超过币种精度");
    }

    /** 范围币种未在币种规则中配置。 */
    @Test
    void test_currencyNotConfiguredRejected() {
        ChannelCostVersionRequestBuilder builder = version();
        builder.scope(0).setCurrency(UNCONFIGURED_CURRENCY);
        builder.component(0).setCurrency(UNCONFIGURED_CURRENCY);

        createVersion(builder).assertBusinessRejected("适用范围币种未配置或未启用")
            .assertDetailContains(UNCONFIGURED_CURRENCY);
    }

    /** 范围序号重复（维度不同）。 */
    @Test
    void test_duplicateScopeNoRejected() {
        ChannelCostVersionRequestBuilder builder = version();
        builder.addScope(scope(1, standardComponent()));
        builder.scope(1).setActionType("REFUND");

        createVersion(builder).assertBusinessRejected("适用范围序号重复");
    }

    /** 六个匹配维度组合在版本内重复（序号不同）。 */
    @Test
    void test_duplicateScopeDimensionsRejected() {
        ChannelCostVersionRequestBuilder builder = version().addScope(scope(2, standardComponent()));

        createVersion(builder).assertBusinessRejected("适用范围匹配维度重复")
            .assertDetailContains(ChannelCostVersionRequestBuilder.MID_ID);
    }

    /** 范围内组成代码重复。 */
    @Test
    void test_duplicateComponentCodeRejected() {
        ChannelCostVersionRequestBuilder builder = version().components(standardComponent(),
            component(ChannelCostVersionRequestBuilder.PROCESSING, ChannelCostComponentType.CROSS_BORDER_FEE, "0.01",
                null));

        createVersion(builder).assertBusinessRejected("同一适用范围内成本组成代码重复");
    }

    /** 范围集为空由服务端拒绝。 */
    @Test
    void test_emptyScopesRejected() {
        createVersion(version().clearScopes()).assertBusinessRejected("渠道成本适用范围不能为空");
    }

    /** 范围的组成集为空由服务端拒绝。 */
    @Test
    void test_emptyComponentsRejected() {
        createVersion(version().components()).assertBusinessRejected("适用范围成本组成不能为空");
    }

    /** 组件类型代码不在目录中。 */
    @Test
    void test_unknownComponentTypeInvalid() {
        ChannelCostVersionRequestBuilder builder = version();
        builder.component(0).setComponentType("UNKNOWN_FEE");

        createVersion(builder).assertInvalidRequest().assertDetailContains("成本组成类型代码不在目录内");
    }

    /** 编制人缺失属于字段校验。 */
    @Test
    void test_missingMakerIdInvalid() {
        createVersion(version().makerId(null)).assertProblem(422, "VALIDATION_ERROR").assertFieldError("makerId");
    }

    /** 方案不存在。 */
    @Test
    void test_planNotFoundRejected() {
        String unknown = "UNKNOWN_PLAN";

        createVersion(unknown, version()).assertBusinessRejected("渠道成本方案不存在").assertDetailContains(unknown);
    }
}
