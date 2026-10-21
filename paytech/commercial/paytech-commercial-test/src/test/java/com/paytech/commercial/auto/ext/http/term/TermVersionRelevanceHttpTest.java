package com.paytech.commercial.auto.ext.http.term;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.PriceRelevanceReason;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.commercial.test.base.term.AbstractCommercialTermApiTest;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.AgreementCapabilityBuilder;
import com.paytech.commercial.test.builder.AgreementPartyBuilder;
import com.paytech.commercial.test.builder.PricingProfileBuilder;
import com.paytech.foundation.common.error.CommonErrorCode;

import tools.jackson.databind.JsonNode;

/**
 * Client 合同方案的价格相关性、零开通与按接受条件空间的完整性（开通与定价模型调整 technique.md §4.4、§4.7，AC-06）
 * <li>保存含未开通支付方式 / 功能的价格时返回 relevanceWarnings 且保存成功，排期被拒并在 detail 中给出 OE / 币种 / 画像与原因。</li>
 * <li>OE 没有任何开通项却有价格时排期被拒；CARD 限 VISA / MASTERCARD 时只有 VISA 价格缺 MASTERCARD 被拒，补通用价格后通过。</li>
 * <li>价格行须先对应已开通的支付方式类开通项，只有功能费的行不因功能已开通而相关。</li>
 */
class TermVersionRelevanceHttpTest extends AbstractCommercialTermApiTest {
    private static final String BIZ = CommonErrorCode.BUSINESS_CHECK_FAILED.getCode();

    private static final String APPLE_PAY = "WRITE_APPLE_PAY";

    /** 保存时提示：APPLE_PAY 未开通、币种不在条件内、存卡功能未开通；三条提示均不阻断保存，排期时拒绝。 */
    @Test
    void test_relevanceWarningsOnSaveAndRejectedOnSchedule() {
        mockBaseline();
        pricing.mockProfile(PricingProfileBuilder.template().pricingProfileId(PricingProfileBuilder.PRICING_PROFILE_ID + 4)
            .pricingProfileCode(APPLE_PAY).paymentMethodCode(CommercialCodes.PaymentMethod.APPLE_PAY)
            .status(PricingProfileStatus.ACTIVE));

        JsonNode saved = replacePrices(createVersion(createPlan()), price("USD", CARD, 1, percentage("MDR", "0.025")),
            price("USD", APPLE_PAY, 2, percentage("MDR", "0.02")), price("AED", CARD, 1, percentage("MDR", "0.03")),
            price("USD", CARD_VISA, 3, tokenFee("USD")));

        JsonNode warnings = saved.path("relevanceWarnings");
        assertAll(() -> assertEquals(List.of(PriceRelevanceReason.PAYMENT_METHOD_NOT_ENABLED.getCode(),
                PriceRelevanceReason.CURRENCY_NOT_ENABLED.getCode(), PriceRelevanceReason.FEATURE_NOT_ENABLED.getCode()),
                warnings.valueStream().map(node -> text(node, "reasonCode")).toList()),
            () -> assertEquals(List.of(APPLE_PAY, CARD, CARD_VISA),
                warnings.valueStream().map(node -> text(node, "pricingProfileCode")).toList()),
            () -> assertEquals(ENTITY_NO, text(warnings.get(0), "entityNo")),
            () -> assertEquals("AED", text(warnings.get(1), "currency")),
            () -> assertEquals(CommercialTermVersionStatus.DRAFT.getCode(), text(saved, "status")));
        assertProblem(scheduleResult(approvedAndSigned(saved), NOW), 400, BIZ, "价格与开通项不相关（PAYMENT_METHOD_NOT_ENABLED）: "
            + ENTITY_NO + "/USD/" + APPLE_PAY);
    }

    /** 零开通：合同 OE 没有任何开通项却有 Client 价格，排期被拒。 */
    @Test
    void test_zeroCapabilityRejected() {
        catalog.mockSeedCatalog();
        graph.mockAgreement(AgreementBuilder.template().status(AgreementStatus.ACTIVE)).party(AgreementPartyBuilder.template());
        mockCardProfiles(PricingProfileStatus.ACTIVE);

        JsonNode saved = replacePrices(createVersion(createPlan()), price("USD", CARD, 1, percentage("MDR", "0.025")));

        assertAll(() -> assertEquals(PriceRelevanceReason.NO_CAPABILITY_ON_ENTITY.getCode(),
                text(saved.path("relevanceWarnings").get(0), "reasonCode")),
            () -> assertProblem(scheduleResult(approvedAndSigned(saved), NOW), 400, BIZ, "NO_CAPABILITY_ON_ENTITY"));
    }

    /** 完整性按接受条件空间：CARD 限 VISA / MASTERCARD，只有 VISA 价格时 MASTERCARD 组合缺价被拒；补通用卡价格后排期通过。 */
    @Test
    void test_completenessByConditionSpace() {
        catalog.mockSeedCatalog();
        graph.mockAgreement(AgreementBuilder.template().status(AgreementStatus.ACTIVE)).party(AgreementPartyBuilder.template());
        mockCardProfiles(PricingProfileStatus.ACTIVE);
        subscriptions.mockCapability(AgreementCapabilityBuilder.template().agreementId(AgreementBuilder.AGREEMENT_ID)
                .entityNo(ENTITY_NO)).currencies("USD").limit(PricingDimension.CARD_BRAND, CommercialCodes.CardBrand.VISA)
            .limit(PricingDimension.CARD_BRAND, CommercialCodes.CardBrand.MASTERCARD);

        JsonNode visaOnly = approvedAndSigned(replacePrices(createVersion(createPlan()),
            price("USD", CARD_VISA, 1, percentage("MDR", "0.025"))));
        assertProblem(scheduleResult(visaOnly, NOW), 400, BIZ,
            "激活校验 3（覆盖完整）：开通项没有命中的价格: " + ENTITY_NO + "/CARD/USD/CARD_BRAND=MASTERCARD");

        JsonNode second = ok(201, createVersionResult(text(visaOnly, "planId"), text(visaOnly, "changeVersion"), null));
        JsonNode complete = approvedAndSigned(replacePrices(second, price("USD", CARD_VISA, 1, percentage("MDR", "0.025")),
            price("USD", CARD, 2, percentage("MDR", "0.03"))));
        assertEquals(CommercialTermVersionStatus.SCHEDULED.getCode(), text(ok(200, scheduleResult(complete, NOW)), "status"));
    }

    /**
     * 只有功能费的价格行也须先对应已开通的支付方式类开通项（缺陷回归 DEMO2-CL-101）：存卡已开通、CARD 只开 USD 时，AED 通用卡只带存卡费的行
     * 保存提示 CURRENCY_NOT_ENABLED、排期被拒；USD 通用卡的 MDR + 存卡费行相关。
     */
    @Test
    void test_featureOnlyRowNeedsPaymentMethodCapability() {
        mockBaseline();
        mockTokenStorage();

        JsonNode saved = replacePrices(createVersion(createPlan()),
            price("USD", CARD, 1, percentage("MDR", "0.025"), tokenFee("USD")), price("AED", CARD, 1, tokenFee("AED")));

        JsonNode warnings = saved.path("relevanceWarnings");
        assertAll(() -> assertEquals(1, warnings.size()),
            () -> assertEquals(PriceRelevanceReason.CURRENCY_NOT_ENABLED.getCode(), text(warnings.get(0), "reasonCode")),
            () -> assertEquals("AED", text(warnings.get(0), "currency")),
            () -> assertEquals(CARD, text(warnings.get(0), "pricingProfileCode")));
        assertProblem(scheduleResult(approvedAndSigned(saved), NOW), 400, BIZ, "价格与开通项不相关（CURRENCY_NOT_ENABLED）: "
            + ENTITY_NO + "/AED/" + CARD);
    }
}
