package com.paytech.commercial.auto.ext.integration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.AgreementCapabilityStatus;
import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.api.enums.FundMode;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.PricingAuthority;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ResponsiblePartyType;
import com.paytech.commercial.domain.CheckoutCandidateContext;
import com.paytech.commercial.domain.CheckoutContextView;
import com.paytech.commercial.domain.CheckoutQuery;
import com.paytech.commercial.domain.CheckoutResolutionContext;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.commercial.test.base.checkout.AbstractCheckoutApiTest;
import com.paytech.commercial.test.builder.CommercialTermComponentBuilder;
import com.paytech.commercial.test.builder.CommercialTermPriceBuilder;
import com.paytech.commercial.test.builder.PricingProfileBuilder;
import com.paytech.commercial.test.builder.PricingProfileCondBuilder;
import com.paytech.commercial.test.dal.CommercialTestMapper;
import com.paytech.commercial.test.mocker.local.agreement.AgreementSubscriptionMocker;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutTermVersionMocker;
import com.paytech.commercial.test.mocker.local.term.TermPricingMocker;

/**
 * 解析矩阵（technique §2.3、§2.5、§2.11）：订阅、开通项、约束、资金模式、版本区间与派生四项。
 * <li>HTTP 解析链在 Risk 正式不可用端口处以 503 结束，成功候选无法经 HTTP 返回；这里与 {@link CheckoutVerificationTest}
 * 相同，从公开端口与本库只读 Service 构造 {@link CheckoutResolutionContext}，以首个候选和 {@link CheckoutCandidateContext#toView()}
 * 观察真实解析结果。</li>
 * <li>夹具：AE991（优先级 1，合同 106 订阅 ACQUIRING / PAYOUT，CARD 限 VISA、USD / EUR 且带保证金，APPLE_PAY 无约束且无价格）与
 * AE992（优先级 2，合同 206 订阅 ACQUIRING，CARD 限 USD 与 AE）；两份 Client 版本各有一条 USD 的 CARD 画像价格。AE991 落空即由 AE992
 * 接替。开通项只在有生效价格时输出（开通与定价模型调整 AC-09）。</li>
 */
class CheckoutResolutionMatrixTest extends AbstractCheckoutApiTest {
    private static final long FIRST_AGREEMENT_ID = CheckoutTermVersionMocker.AGREEMENT_ID;
    private static final long FIRST_CARD_CAPABILITY_ID = 910000000000331L;
    private static final long FIRST_CARD_USD_LIMIT_ID = 910000000000341L;
    private static final long FIRST_CARD_BRAND_LIMIT_ID = 910000000000345L;
    private static final long FIRST_PRICE_ID = 910000000000114L;
    private static final long FIRST_ENTITY_PARTY_ID = 910000000000102L;
    private static final long CLIENT_PARTY_ID = 910000000000002L;
    private static final long SUCCESSOR_VERSION_ID = 910000000000351L;
    private static final String FIRST_ENTITY = "AE991";
    private static final String SECOND_ENTITY = "AE992";

    @Autowired
    CommercialTestMapper data;
    @Autowired
    CheckoutTermVersionMocker versions;
    @Autowired
    AgreementSubscriptionMocker subscriptions;
    @Autowired
    TermPricingMocker pricing;

    /** 每例准备商务事实与默认主档候选。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture.install();
        clientMaster.mockCandidates();
    }

    /** 不给支付方式：首选 AE991，返回商业关系、订阅产品与 MOR 派生，不匹配开通项。 */
    @Test
    void test_normalFlow_withoutPaymentMethod() {
        CheckoutContextView view = first(query()).toView();

        var terms = view.clientTerms();
        var derived = view.derived();
        assertAll(() -> assertEquals(FIRST_ENTITY, view.entityNo()),
            () -> assertEquals(BusinessMode.MOR.getCode(), terms.businessMode()),
            () -> assertEquals(FundMode.SETTLE_THROUGH_PLATFORM.getCode(), terms.fundMode()),
            () -> assertEquals(PricingAuthority.PLATFORM.getCode(), terms.pricingAuthority()),
            () -> assertNull(terms.referralPartnerId()),
            () -> assertEquals(List.of(ProductCode.ACQUIRING.getCode(), ProductCode.PAYOUT.getCode()),
                terms.agreementProducts()),
            () -> assertEquals(List.of(), terms.capabilities()),
            () -> assertEquals(Long.toString(FIRST_ENTITY_PARTY_ID), derived.sellerPartyId()),
            () -> assertEquals(FIRST_ENTITY, derived.acquiringEntityNo()),
            () -> assertEquals(ResponsiblePartyType.OPERATING_ENTITY.getCode(), derived.taxResponsibleParty()),
            () -> assertEquals(ResponsiblePartyType.OPERATING_ENTITY.getCode(), derived.fundsBookOwner()));
    }

    /** 给出 CARD：命中 CARD（按支付方式匹配），返回开通项、支付方式、种类、完整接受条件、3DS 要求与保证金条款。 */
    @Test
    void test_normalFlow_withPaymentMethod() {
        data.overwriteCapabilityRequire3ds(FIRST_CARD_CAPABILITY_ID, true);

        var capabilities = first(query(CommercialCodes.PaymentMethod.CARD)).toView().clientTerms().capabilities();
        var capability = capabilities.getFirst();

        assertAll(() -> assertEquals(1, capabilities.size()),
            () -> assertEquals(Long.toString(FIRST_CARD_CAPABILITY_ID), capability.agreementCapabilityId()),
            () -> assertEquals(CommercialCodes.Capability.CARD, capability.capabilityCode()),
            () -> assertEquals(AgreementCapabilityStatus.ENABLED.getCode(), capability.status()),
            () -> assertEquals(CommercialCodes.PaymentMethod.CARD, capability.paymentMethodCode()),
            () -> assertEquals(CapabilityKind.PAYMENT_METHOD.getCode(), capability.capabilityKind()),
            () -> assertEquals(List.of(PricingDimension.CARD_BRAND.getCode(), PricingDimension.CURRENCY.getCode()),
                capability.conditions().stream().map(CheckoutContextView.CapabilityCondition::dimensionCode).toList()),
            () -> assertEquals(List.of(CommercialCodes.CardBrand.VISA), capability.conditions().get(0).values()),
            () -> assertEquals(List.of("EUR", "USD"), capability.conditions().get(1).values()),
            () -> assertEquals(true, capability.require3ds()),
            () -> assertEquals("0.1", capability.rollingReserveRate()),
            () -> assertEquals(90, capability.rollingReserveDays()));
    }

    /** 新开通但版本中没有生效价格的开通项不输出：APPLE_PAY 无价格时落空，补一条 APPLE_PAY 画像价格后命中 AE991。 */
    @Test
    void test_capabilityWithoutPriceIsNotReturned() {
        assertFalse(context(query(CommercialCodes.PaymentMethod.APPLE_PAY)).candidates().iterator().hasNext());

        pricing.mockProfile(PricingProfileBuilder.template().pricingProfileId(910000000000008L)
            .pricingProfileCode("CCTQUERY_APPLE").paymentMethodCode(CommercialCodes.PaymentMethod.APPLE_PAY)
            .status(PricingProfileStatus.ACTIVE));
        pricing.mockPrice(CommercialTermPriceBuilder.template().termPriceId(910000000000115L)
                .termVersionId(CheckoutTermVersionMocker.TERM_VERSION_ID).entityNo(FIRST_ENTITY)
                .pricingProfileCode("CCTQUERY_APPLE").priorityNo(2),
            CommercialTermComponentBuilder.template().termComponentId(910000000000116L));

        var capabilities = first(query(CommercialCodes.PaymentMethod.APPLE_PAY)).toView().clientTerms().capabilities();
        assertAll(() -> assertEquals(List.of(CommercialCodes.Capability.APPLE_PAY),
                capabilities.stream().map(CheckoutContextView.Capability::capabilityCode).toList()),
            () -> assertEquals(List.of(), capabilities.getFirst().conditions()),
            () -> assertEquals(false, capabilities.getFirst().require3ds()));
    }

    /** 价格画像条件与接受条件不相容（价格只限 VISA，开通项只收 AMEX）时 AE991 的 CARD 视为无生效价格，落到 AE992。 */
    @Test
    void test_priceConditionOutOfScopeFallsThrough() {
        pricing.mockProfile(PricingProfileBuilder.template().pricingProfileId(910000000000009L)
            .pricingProfileCode("CCTQUERY_VISA").paymentMethodCode(CommercialCodes.PaymentMethod.CARD)
            .status(PricingProfileStatus.ACTIVE),
            PricingProfileCondBuilder.template().dimensionCode(PricingDimension.CARD_BRAND)
                .conditionValue(CommercialCodes.CardBrand.VISA));
        data.overwritePriceProfile(FIRST_PRICE_ID, "CCTQUERY_VISA");
        assertEquals(FIRST_ENTITY, first(query(CommercialCodes.PaymentMethod.CARD)).entityNo());

        data.overwriteCapabilityLimitValue(FIRST_CARD_BRAND_LIMIT_ID, CommercialCodes.CardBrand.AMEX);

        assertEquals(SECOND_ENTITY, first(query(CommercialCodes.PaymentMethod.CARD)).entityNo());
    }

    /** PSP：卖方为 Client Party，税务责任在 Client，资金账簿仍归收单 OE。 */
    @Test
    void test_pspDerivesClientAsSeller() {
        data.overwriteAgreementModes(FIRST_AGREEMENT_ID, BusinessMode.PSP, FundMode.SETTLE_THROUGH_PLATFORM);

        CheckoutContextView view = first(query()).toView();

        assertAll(() -> assertEquals(BusinessMode.PSP.getCode(), view.clientTerms().businessMode()),
            () -> assertEquals(Long.toString(CLIENT_PARTY_ID), view.derived().sellerPartyId()),
            () -> assertEquals(FIRST_ENTITY, view.derived().acquiringEntityNo()),
            () -> assertEquals(ResponsiblePartyType.CLIENT.getCode(), view.derived().taxResponsibleParty()),
            () -> assertEquals(ResponsiblePartyType.OPERATING_ENTITY.getCode(), view.derived().fundsBookOwner()));
    }

    /** 未订阅 ACQUIRING：AE991 落空，由 AE992 接替。 */
    @Test
    void test_notSubscribedFallsThrough() {
        data.deleteAgreementProduct(FIRST_AGREEMENT_ID, ProductCode.ACQUIRING);

        assertEquals(SECOND_ENTITY, first(query()).entityNo());
    }

    /** 未开通：两份合同都没有 GOOGLE_PAY，没有候选；APPLE_PAY 只在 AE991 开通且没有价格。 */
    @Test
    void test_notEnabledFallsThrough() {
        assertFalse(context(query(CommercialCodes.PaymentMethod.GOOGLE_PAY)).candidates().iterator().hasNext());
        data.overwriteCapabilityStatus(910000000000332L, AgreementCapabilityStatus.DISABLED);
        assertFalse(context(query(CommercialCodes.PaymentMethod.APPLE_PAY)).candidates().iterator().hasNext());
    }

    /** 币种不在条件内：AE991 的 CARD 只剩 EUR，USD 请求落到 AE992。 */
    @Test
    void test_currencyOutsideLimitsFallsThrough() {
        data.deleteCapabilityLimit(FIRST_CARD_USD_LIMIT_ID);

        assertEquals(SECOND_ENTITY, first(query(CommercialCodes.PaymentMethod.CARD)).entityNo());
    }

    /** SUSPENDED 的开通项不参与匹配；不给支付方式时不受影响。 */
    @Test
    void test_suspendedCapabilityFallsThrough() {
        data.overwriteCapabilityStatus(FIRST_CARD_CAPABILITY_ID, AgreementCapabilityStatus.SUSPENDED);

        assertAll(() -> assertEquals(SECOND_ENTITY, first(query(CommercialCodes.PaymentMethod.CARD)).entityNo()),
            () -> assertEquals(FIRST_ENTITY, first(query()).entityNo()));
    }

    /** 开通项区间在商业时点之前结束：落空。 */
    @Test
    void test_capabilityWindowOutsideFallsThrough() {
        data.overwriteCapabilityEffectiveTo(FIRST_CARD_CAPABILITY_ID, Instant.parse("2026-09-10T00:00:00Z"));

        assertEquals(SECOND_ENTITY, first(query(CommercialCodes.PaymentMethod.CARD)).entityNo());
    }

    /** 条款版本区间不含商业时点：没有生效版本，落空。 */
    @Test
    void test_versionWindowOutsideFallsThrough() {
        data.overwriteVersionEffectiveTo(CheckoutTermVersionMocker.TERM_VERSION_ID, Instant.parse("2026-09-10T00:00:00Z"));

        assertEquals(SECOND_ENTITY, first(query()).entityNo());
    }

    /** SCHEDULED 已到起点而激活作业尚未切换：按区间选中排期版本，不看 ACTIVE 标签。 */
    @Test
    void test_scheduledVersionAlreadyEffectiveIsSelected() {
        Instant switchTime = Instant.parse("2026-09-10T00:00:00Z");
        data.overwriteVersionEffectiveTo(CheckoutTermVersionMocker.TERM_VERSION_ID, switchTime);
        versions.mockSuccessorVersion(SUCCESSOR_VERSION_ID, CommercialTermVersionStatus.SCHEDULED, switchTime);

        CheckoutContextView view = first(query()).toView();

        assertAll(() -> assertEquals(FIRST_ENTITY, view.entityNo()),
            () -> assertEquals(Long.toString(SUCCESSOR_VERSION_ID), view.clientTerms().versionId()));
    }

    /** 原 ACTIVE 终点仍为空（激活作业尚未运行）时，起点已到的 SCHEDULED 后继以较大起点胜出，不报冲突。 */
    @Test
    void test_scheduledVersionWinsOverOpenActive() {
        versions.mockSuccessorVersion(SUCCESSOR_VERSION_ID, CommercialTermVersionStatus.SCHEDULED,
            Instant.parse("2026-09-10T00:00:00Z"));

        assertEquals(Long.toString(SUCCESSOR_VERSION_ID), first(query()).toView().clientTerms().versionId());
    }

    /** 尚未到起点的 SCHEDULED 不参与，原 ACTIVE 版本继续生效。 */
    @Test
    void test_futureScheduledVersionIsIgnored() {
        versions.mockSuccessorVersion(SUCCESSOR_VERSION_ID, CommercialTermVersionStatus.SCHEDULED,
            Instant.parse("2026-10-01T00:00:00Z"));

        assertEquals(Long.toString(CheckoutTermVersionMocker.TERM_VERSION_ID),
            first(query()).toView().clientTerms().versionId());
    }

    /** 区间包含商业时点的 DRAFT 不参与解析，不与原 ACTIVE 版本构成冲突。 */
    @Test
    void test_draftVersionInWindowIsIgnored() {
        versions.mockSuccessorVersion(SUCCESSOR_VERSION_ID, CommercialTermVersionStatus.DRAFT,
            Instant.parse("2026-09-05T00:00:00Z"));

        assertEquals(Long.toString(CheckoutTermVersionMocker.TERM_VERSION_ID),
            first(query()).toView().clientTerms().versionId());
    }

    /** FEES_ONLY 本期不受理：AE991 落空。 */
    @Test
    void test_feesOnlyFundModeFallsThrough() {
        data.overwriteAgreementModes(FIRST_AGREEMENT_ID, BusinessMode.PSP, FundMode.FEES_ONLY);

        assertEquals(SECOND_ENTITY, first(query()).entityNo());
    }

    /** 一个 CARD 开通项可接受多个卡组织：条件按维度与取值排序整体返回，由收单在录卡时使用。 */
    @Test
    void test_multipleCardBrandsInOneCapability() {
        data.overwriteCapabilityLimitValue(FIRST_CARD_BRAND_LIMIT_ID, CommercialCodes.CardBrand.MASTERCARD);
        data.insertCapabilityLimit(910000000000346L, FIRST_CARD_CAPABILITY_ID, PricingDimension.CARD_BRAND.getCode(),
            CommercialCodes.CardBrand.VISA);

        CheckoutContextView view = first(query(CommercialCodes.PaymentMethod.CARD)).toView();

        var capabilities = view.clientTerms().capabilities();
        assertAll(() -> assertEquals(FIRST_ENTITY, view.entityNo()),
            () -> assertEquals(List.of(CommercialCodes.Capability.CARD),
                capabilities.stream().map(CheckoutContextView.Capability::capabilityCode).toList()),
            () -> assertEquals(List.of(CommercialCodes.CardBrand.MASTERCARD, CommercialCodes.CardBrand.VISA),
                capabilities.getFirst().conditions().getFirst().values()));
    }

    /** CARD 停用后 AE991 没有卡开通项，落到 AE992。 */
    @Test
    void test_disabledCardFallsThrough() {
        data.overwriteCapabilityStatus(FIRST_CARD_CAPABILITY_ID, AgreementCapabilityStatus.DISABLED);

        assertEquals(SECOND_ENTITY, first(query(CommercialCodes.PaymentMethod.CARD)).entityNo());
    }

    /** 首个完整候选。 */
    private CheckoutCandidateContext first(CheckoutQuery query) {
        return context(query).candidates().iterator().next();
    }
}
