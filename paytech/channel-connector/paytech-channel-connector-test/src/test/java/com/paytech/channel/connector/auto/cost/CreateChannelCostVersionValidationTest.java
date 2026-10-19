package com.paytech.channel.connector.auto.cost;

import static com.paytech.channel.connector.test.builder.ChannelCostVersionRequestBuilder.component;
import static com.paytech.channel.connector.test.builder.ChannelCostVersionRequestBuilder.scope;
import static com.paytech.channel.connector.test.builder.ChannelCostVersionRequestBuilder.standardComponent;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.channel.connector.api.cost.ChannelCostPlanApi;
import com.paytech.channel.connector.api.cost.request.CreateChannelCostVersionRequestBody;
import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.core.enums.ChannelCostComponentType;
import com.paytech.channel.connector.test.base.AbstractChannelCostApiTest;
import com.paytech.channel.connector.test.builder.ChannelCostVersionRequestBuilder;

/**
 * 创建渠道成本版本的内容校验：范围 MID 存在 / 归属 Channel / OE 一致、费率区间、费率与固定费、固定费币种、上下限、
 * 金额小数位、范围/组成唯一性、空集合、非法目录代码与方案不存在；被拒时不新增任何版本。
 * <li>自 billing 搬入；原 t_currency_spec 币种启用与币种精度用例删除（本库无币种表，精度归整由 billing 计算时完成）。</li>
 *
 * @see ChannelCostPlanApi#createChannelCostVersion(String, CreateChannelCostVersionRequestBody)
 */
class CreateChannelCostVersionValidationTest extends AbstractChannelCostApiTest {
    /** 与范围币种不同的固定费币种。 */
    private static final String OTHER_CURRENCY = "EUR";

    /** 本库不存在的 MID 主键。 */
    private static final String UNKNOWN_MID_ID = "999999999";

    /** 与标准 Fixture 不同的 Operating Entity。 */
    private static final String OTHER_ENTITY_NO = "AE002";

    /** 本类公共配置：标准 Channel / MID 与 DRAFT 方案。 */
    @BeforeEach
    void setUpClassMocks() {
        mockStandardChannel();
        createStandardPlan();
    }

    /** 每例被拒后方案下没有任何版本。 */
    @AfterEach
    void assertNoVersionCreated() {
        assertEquals(0, testMapper.countChannelCostVersions(PLAN_CODE), "不应新增版本");
    }

    /** 范围 MID 在本库不存在。 */
    @Test
    void test_midNotFoundRejected() {
        String unknownMid = UNKNOWN_MID_ID;
        ChannelCostVersionRequestBuilder builder = version();
        builder.scope(0).setMidId(unknownMid);

        createVersion(builder).assertBusinessRejected("适用范围 MID 不存在").assertDetailContains(unknownMid);
    }

    /** 范围 MID 属于其他 Channel。 */
    @Test
    void test_midOfOtherChannelRejected() {
        ChannelMid otherMid = data.cardFixture("GEIDEA", ChannelCostVersionRequestBuilder.ENTITY_NO,
            ChannelCostVersionRequestBuilder.CURRENCY).getMid();
        ChannelCostVersionRequestBuilder builder = version();
        builder.scope(0).setMidId(otherMid.getMidId().toString());

        createVersion(builder).assertBusinessRejected("适用范围 MID 不属于方案 Channel")
            .assertDetailContains(otherMid.getMidId().toString());
    }

    /** 范围 MID 的 OE 与范围 entityNo 不一致。 */
    @Test
    void test_midOfOtherEntityRejected() {
        long channelId = fixture.getChannel().getChannelId();
        var account = data.account(channelId, OTHER_ENTITY_NO, "C".repeat(43));
        ChannelMid otherEntityMid = data.mid(channelId, account.getChannelAccountId(), OTHER_ENTITY_NO);
        ChannelCostVersionRequestBuilder builder = version();
        builder.scope(0).setMidId(otherEntityMid.getMidId().toString());

        createVersion(builder).assertBusinessRejected("适用范围 MID 的 Operating Entity 与范围不一致")
            .assertDetailContains(OTHER_ENTITY_NO);
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
        builder.component(0).setCurrency(OTHER_CURRENCY);

        createVersion(builder).assertBusinessRejected("固定费币种必须等于适用范围币种").assertDetailContains(OTHER_CURRENCY);
    }

    /** 下限大于上限。 */
    @Test
    void test_minAboveMaxRejected() {
        ChannelCostVersionRequestBuilder builder = version().components(
            component("CAPPED_FEE", ChannelCostComponentType.PROCESSING_FEE, "0.029", null, "5.00", "1.00"));

        createVersion(builder).assertBusinessRejected("成本组成下限不能大于上限").assertDetailContains("CAPPED_FEE");
    }

    /** 金额小数位超过 8 位由入口格式拒绝（本库只校验小数位，不按币种精度）。 */
    @Test
    void test_amountOverEightDecimalsInvalid() {
        ChannelCostVersionRequestBuilder builder = version();
        builder.component(0).setFixedAmount("0.300000001");

        createVersion(builder).assertProblem(422, "VALIDATION_ERROR");
    }

    /** 范围序号重复（维度不同）。 */
    @Test
    void test_duplicateScopeNoRejected() {
        ChannelCostVersionRequestBuilder builder = version();
        builder.addScope(scope(1, midId(), standardComponent()));
        builder.scope(1).setActionType("REFUND");

        createVersion(builder).assertBusinessRejected("适用范围序号重复");
    }

    /** 六个匹配维度组合在版本内重复（序号不同）。 */
    @Test
    void test_duplicateScopeDimensionsRejected() {
        ChannelCostVersionRequestBuilder builder = version().addScope(scope(2, midId(), standardComponent()));

        createVersion(builder).assertBusinessRejected("适用范围匹配维度重复").assertDetailContains(String.valueOf(midId()));
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
