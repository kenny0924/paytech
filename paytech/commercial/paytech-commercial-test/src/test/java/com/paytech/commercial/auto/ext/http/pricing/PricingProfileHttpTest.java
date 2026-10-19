package com.paytech.commercial.auto.ext.http.pricing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.PricingProfileKind;
import com.paytech.commercial.api.pricing.PricingProfileApi;
import com.paytech.commercial.api.pricing.request.CreatePricingProfileRequestBody;
import com.paytech.commercial.api.pricing.request.PricingProfileTransitionRequestBody;
import com.paytech.commercial.api.pricing.request.UpdatePricingProfileRequestBody;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.commercial.test.base.write.AbstractCommercialWriteApiTest;
import com.paytech.commercial.test.builder.PricingProfileBodyBuilder;
import com.paytech.commercial.test.builder.PricingProfileBuilder;
import com.paytech.commercial.test.builder.PricingProfileCondBuilder;
import com.paytech.commercial.test.checker.write.CommercialWriteResponseChecker;

/**
 * 定价画像维护（technique.md §2.2、database.md §5.6）：创建与同码重放、种类 / Payment Method / 条件维度相容、DRAFT 改写与不变重放、
 * ACTIVE 冻结、激活与退役的状态条件推进与重放、版本冲突、按种类与状态的列表
 *
 * @see PricingProfileApi#createProfile(String, CreatePricingProfileRequestBody)
 * @see PricingProfileApi#updateProfile(String, String, UpdatePricingProfileRequestBody)
 * @see PricingProfileApi#activate(String, String, PricingProfileTransitionRequestBody)
 * @see PricingProfileApi#retire(String, String, PricingProfileTransitionRequestBody)
 * @see PricingProfileApi#listProfiles(String, String)
 */
class PricingProfileHttpTest extends AbstractCommercialWriteApiTest {
    private static final String PATH = "/pricing-profiles/v1";

    private static final long PROFILE_ID = PricingProfileBuilder.PRICING_PROFILE_ID;

    private static final String RECORD_TYPE = BusinessChangeRecordRecordType.PAYMENT_METHOD_PROFILE.getCode();

    /** 创建 DRAFT 画像：版本 1、条件按维度排序、Root CREATE 快照含条件。 */
    @Test
    void test_create_normalFlow() {
        CommercialWriteResponseChecker response = post(PATH, PricingProfileBodyBuilder.template().buildCreate()).assertOk()
            .assertText("status", PricingProfileStatus.DRAFT.getCode()).assertText("pricingProfileCode",
                PricingProfileBodyBuilder.CODE).assertVersion(1);
        long profileId = response.id("pricingProfileId");

        assertAll(() -> assertEquals(PricingDimension.CARD_BRAND.getCode(),
                response.node("conditions").get(0).path("dimensionCode").asString()),
            () -> assertEquals(List.of("CARD_BRAND:VISA", "CURRENCY:USD"), testMapper.pricingProfileConditions(profileId)),
            () -> assertEquals(CommercialCodes.PaymentMethod.CARD, testMapper.pricingProfile(profileId).get("payment_method_code")));
        history.record(RECORD_TYPE, profileId).assertParent(null).assertVersions("1:CREATE")
            .assertLastReason(BusinessChangeReason.PRICING_PROFILE_CREATED.getCode())
            .assertLastData("profileKind", PricingProfileKind.PAYMENT_METHOD.getCode()).assertLastActor(ACTOR_ID)
            .assertIntegrity();
    }

    /** 同码同内容重放返回原画像；同码改内容 409。 */
    @Test
    void test_create_replay() {
        long first = post(PATH, PricingProfileBodyBuilder.template().buildCreate()).assertOk().id("pricingProfileId");

        long second = post(PATH, PricingProfileBodyBuilder.template().buildCreate()).assertOk().assertVersion(1)
            .id("pricingProfileId");
        post(PATH, PricingProfileBodyBuilder.template().conditions(PricingProfileBodyBuilder.condition(
            PricingDimension.CARD_BRAND, CommercialCodes.CardBrand.AMEX)).buildCreate()).assertConflict();

        assertAll(() -> assertEquals(first, second),
            () -> assertEquals(1, testMapper.countPricingProfilesByCode(PricingProfileBodyBuilder.CODE)));
    }

    /** 种类与 Payment Method / 维度不相容、值格式不符、条件重复与未知维度均在落库前拒绝。 */
    @Test
    void test_create_businessRuleViolation() {
        post(PATH, PricingProfileBodyBuilder.template().paymentMethodCode(null).buildCreate())
            .assertBusinessCheckFailed("必须指定 paymentMethodCode");
        post(PATH, PricingProfileBodyBuilder.template().kind(PricingProfileKind.PAYOUT_RAIL)
            .conditions(PricingProfileBodyBuilder.condition(PricingDimension.PAYOUT_RAIL, "SWIFT")).buildCreate())
            .assertBusinessCheckFailed("非 PAYMENT_METHOD 画像不能指定 paymentMethodCode");
        post(PATH, PricingProfileBodyBuilder.template().kind(PricingProfileKind.FX_PAIR).paymentMethodCode(null).buildCreate())
            .assertBusinessCheckFailed("条件维度与画像种类不相容");
        post(PATH, PricingProfileBodyBuilder.template().conditions(PricingProfileBodyBuilder.condition(
            PricingDimension.CURRENCY, "US")).buildCreate()).assertBusinessCheckFailed("条件值格式不符合维度要求");
        post(PATH, PricingProfileBodyBuilder.template().conditions(
            PricingProfileBodyBuilder.condition(PricingDimension.CURRENCY, "USD"),
            PricingProfileBodyBuilder.condition(PricingDimension.CURRENCY, "USD")).buildCreate()).assertInvalidRequest();
        CreatePricingProfileRequestBody unknownDimension = PricingProfileBodyBuilder.template().buildCreate();
        unknownDimension.getConditions().getFirst().setDimensionCode("CHANNEL");
        post(PATH, unknownDimension).assertValidationError("conditions[0].dimensionCode");

        assertEquals(0, testMapper.countPricingProfilesByCode(PricingProfileBodyBuilder.CODE));
    }

    /** 非支付方式种类：FX_PAIR 只用币对维度，PERIODIC 无条件。 */
    @Test
    void test_edgeCase_nonPaymentKinds() {
        post(PATH, PricingProfileBodyBuilder.template().code("B_FX_USDAED").kind(PricingProfileKind.FX_PAIR)
            .paymentMethodCode(null).conditions(PricingProfileBodyBuilder.condition(PricingDimension.CURRENCY_PAIR, "USD/AED"))
            .buildCreate()).assertOk().assertText("profileKind", PricingProfileKind.FX_PAIR.getCode());
        post(PATH, PricingProfileBodyBuilder.template().code("B_MONTHLY").kind(PricingProfileKind.PERIODIC)
            .paymentMethodCode(null).conditions().buildCreate()).assertOk().assertText("paymentMethodCode", null);
    }

    /** DRAFT 改写：名称与条件整体替换，版本 2，Root UPDATE 快照为改写后的条件。 */
    @Test
    void test_update_normalFlow() {
        mockDraft(1);

        put(PATH + "/" + PROFILE_ID, PricingProfileBodyBuilder.template().name("Visa AED").conditions(
            PricingProfileBodyBuilder.condition(PricingDimension.CARD_BRAND, CommercialCodes.CardBrand.VISA),
            PricingProfileBodyBuilder.condition(PricingDimension.CURRENCY, "AED")).buildUpdate(1))
            .assertOk().assertText("profileName", "Visa AED").assertVersion(2);

        assertAll(() -> assertEquals("Visa AED", testMapper.pricingProfile(PROFILE_ID).get("profile_name")),
            () -> assertEquals(List.of("CARD_BRAND:VISA", "CURRENCY:AED"), testMapper.pricingProfileConditions(PROFILE_ID)));
        history.record(RECORD_TYPE, PROFILE_ID).assertCurrentVersion(2).assertVersions("2:UPDATE")
            .assertLastReason(BusinessChangeReason.PRICING_PROFILE_UPDATED.getCode()).assertIntegrity();
    }

    /** 内容不变的改写不推进版本；ACTIVE 画像冻结；过期版本 409。 */
    @Test
    void test_update_replayFrozenAndStaleVersion() {
        mockDraft(1);
        put(PATH + "/" + PROFILE_ID, sameAsMock(9)).assertOk().assertVersion(1);
        put(PATH + "/" + PROFILE_ID, PricingProfileBodyBuilder.template().name("Renamed").buildUpdate(2))
            .assertConflict();

        long activeId = PROFILE_ID + 1;
        profiles.mock(PricingProfileBuilder.template().pricingProfileId(activeId).pricingProfileCode("B_ACTIVE")
            .status(PricingProfileStatus.ACTIVE), 3, condition(activeId));
        put(PATH + "/" + activeId, PricingProfileBodyBuilder.template().name("Renamed").buildUpdate(3))
            .assertBusinessCheckFailed("只有 DRAFT 画像可以修改");

        assertEquals("Synthetic Card Profile", testMapper.pricingProfile(PROFILE_ID).get("profile_name"));
    }

    /** DRAFT 激活后 ACTIVE、版本 2；同版本重放返回当前结果；再退役 RETIRED、版本 3。 */
    @Test
    void test_activateAndRetire_normalFlow() {
        mockDraft(1);

        post(PATH + "/" + PROFILE_ID + "/activate", transition(1)).assertOk()
            .assertText("status", PricingProfileStatus.ACTIVE.getCode()).assertVersion(2);
        post(PATH + "/" + PROFILE_ID + "/activate", transition(1)).assertOk().assertVersion(2);
        post(PATH + "/" + PROFILE_ID + "/retire", transition(2)).assertOk()
            .assertText("status", PricingProfileStatus.RETIRED.getCode()).assertVersion(3);

        history.record(RECORD_TYPE, PROFILE_ID).assertVersions("2:UPDATE", "3:UPDATE")
            .assertLastReason(BusinessChangeReason.PRICING_PROFILE_RETIRED.getCode())
            .assertLastData("status", PricingProfileStatus.RETIRED.getCode()).assertIntegrity();
    }

    /** 退役 DRAFT、激活 ACTIVE、过期版本与不存在的画像拒绝。 */
    @Test
    void test_transition_businessRuleViolation() {
        mockDraft(1);
        post(PATH + "/" + PROFILE_ID + "/retire", transition(1)).assertBusinessCheckFailed("只有 ACTIVE 画像可以退役");
        post(PATH + "/" + PROFILE_ID + "/activate", transition(3)).assertConflict();
        post(PATH + "/930000000000999/activate", transition(1)).assertBusinessCheckFailed("定价画像不存在");
        long activeId = PROFILE_ID + 1;
        profiles.mock(PricingProfileBuilder.template().pricingProfileId(activeId).pricingProfileCode("B_ACTIVE")
            .status(PricingProfileStatus.ACTIVE), 1, condition(activeId));
        post(PATH + "/" + activeId + "/activate", transition(1)).assertBusinessCheckFailed("只有 DRAFT 画像可以激活");

        assertEquals(PricingProfileStatus.DRAFT.getCode(), testMapper.pricingProfile(PROFILE_ID).get("status"));
    }

    /** 列表按种类与状态筛选，返回条件与当前版本；未知种类为参数错误。 */
    @Test
    void test_list() {
        mockDraft(1);
        profiles.mock(PricingProfileBuilder.template().pricingProfileId(PROFILE_ID + 1).pricingProfileCode("B_ACTIVE")
            .status(PricingProfileStatus.ACTIVE), 4, condition(PROFILE_ID + 1));
        profiles.mock(PricingProfileBuilder.template().pricingProfileId(PROFILE_ID + 2).pricingProfileCode("B_FX")
            .profileKind(PricingProfileKind.FX_PAIR).paymentMethodCode(null).status(PricingProfileStatus.ACTIVE), 2);

        CommercialWriteResponseChecker active = get(PATH + "?kind=PAYMENT_METHOD&status=ACTIVE").assertOk();
        CommercialWriteResponseChecker all = get(PATH).assertOk();

        assertAll(() -> assertEquals(1, active.node("profiles").size()),
            () -> assertEquals("B_ACTIVE", active.node("profiles").get(0).path("pricingProfileCode").asString()),
            () -> assertEquals(4, active.node("profiles").get(0).path("changeVersion").asLong()),
            () -> assertEquals(1, active.node("profiles").get(0).path("conditions").size()),
            () -> assertEquals(3, all.node("profiles").size()));
        get(PATH + "?kind=CARD").assertInvalidRequest();
    }

    /** DRAFT 画像（CARD_BRAND=VISA），Root 版本由用例指定。 */
    void mockDraft(long version) {
        profiles.mock(PricingProfileBuilder.template(), version, condition(PROFILE_ID));
    }

    /** 一个 CARD_BRAND=VISA 条件，ID 由画像派生。 */
    static PricingProfileCondBuilder condition(long profileId) {
        return PricingProfileCondBuilder.template().profileConditionId(profileId + 100);
    }

    /** 与 DRAFT 夹具内容一致的改写请求体。 */
    static UpdatePricingProfileRequestBody sameAsMock(long expectedVersion) {
        return PricingProfileBodyBuilder.template().name("Synthetic Card Profile")
            .conditions(PricingProfileBodyBuilder.condition(PricingDimension.CARD_BRAND, CommercialCodes.CardBrand.VISA))
            .buildUpdate(expectedVersion);
    }

    /** 状态推进请求体。 */
    static PricingProfileTransitionRequestBody transition(long expectedVersion) {
        PricingProfileTransitionRequestBody body = new PricingProfileTransitionRequestBody();
        body.setExpectedChangeVersion(expectedVersion);
        return body;
    }
}
