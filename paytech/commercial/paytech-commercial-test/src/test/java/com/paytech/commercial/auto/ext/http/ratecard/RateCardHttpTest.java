package com.paytech.commercial.auto.ext.http.ratecard;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.CommercialTermPlanStatus;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.ratecard.RateCardApi;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.commercial.test.base.term.AbstractCommercialTermApiTest;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.AgreementCapabilityBuilder;
import com.paytech.commercial.test.builder.AgreementProductBuilder;
import com.paytech.commercial.test.builder.CommercialTermPlanBuilder;
import com.paytech.commercial.test.builder.CommercialTermVersionBuilder;

import tools.jackson.databind.JsonNode;

/**
 * Client 费率卡结构（technique.md §2.7）：ACTIVE 合同的订阅、开通项（约束）、当前与已排期版本，价格按开通项 × 币种分块、块内按具体度排序
 * <li>CARD_VISA 开通 USD + AED、CARD_MC 开通 USD；第 1 版（当前）USD 四个卡画像价格与 AED 通用卡价格，第 2 版（已排期）USD 通用卡价格。</li>
 *
 * @see RateCardApi
 */
class RateCardHttpTest extends AbstractCommercialTermApiTest {
    private CommercialTermVersion current;

    /** 每例：目录、ACTIVE 合同订阅 ACQUIRING、两个开通项、当前与已排期两个版本的价格。 */
    @BeforeEach
    void setUpClassMocks() {
        catalog.mockSeedCatalog();
        graph.mockAgreement(AgreementBuilder.template().status(AgreementStatus.ACTIVE));
        mockCardProfiles(PricingProfileStatus.ACTIVE);
        subscriptions.mockProduct(AgreementProductBuilder.template().agreementId(AgreementBuilder.AGREEMENT_ID));
        subscriptions.mockCapability(AgreementCapabilityBuilder.template().agreementId(AgreementBuilder.AGREEMENT_ID)
            .entityNo(ENTITY_NO)).currencies("USD", "AED");
        subscriptions.mockCapability(AgreementCapabilityBuilder.template()
            .agreementCapabilityId(AgreementCapabilityBuilder.AGREEMENT_CAPABILITY_ID + 1)
            .agreementId(AgreementBuilder.AGREEMENT_ID).entityNo(ENTITY_NO).capabilityCode(CommercialCodes.Capability.CARD_MC))
            .currencies("USD");
        List<CommercialTermVersion> versions = pricing.mockPlan(
            CommercialTermPlanBuilder.template().status(CommercialTermPlanStatus.ACTIVE), 3,
            CommercialTermVersionBuilder.template().status(CommercialTermVersionStatus.ACTIVE).activeSlot(1)
                .effectiveFrom(V1_FROM),
            CommercialTermVersionBuilder.template().termVersionId(CommercialTermVersionBuilder.TERM_VERSION_ID + 1)
                .versionNo(2L).status(CommercialTermVersionStatus.SCHEDULED).effectiveFrom(NOW.plus(30, ChronoUnit.DAYS)));
        current = versions.getFirst();
        mockPrice(current, 0, "USD", CARD, 30, "0.03");
        mockPrice(current, 1, "USD", CARD_VISA, 20, "0.025");
        mockPrice(current, 2, "USD", CARD_VISA_CREDIT, 10, "0.02");
        mockPrice(current, 3, "AED", CARD, 30, "0.035");
        mockPrice(versions.get(1), 4, "USD", CARD, 30, "0.028");
    }

    /** 全币种：合同、订阅、开通项约束、当前 / 已排期版本，各开通项按币种分块、块内具体度降序。 */
    @Test
    void test_structureByCurrency() {
        JsonNode card = ok(200, http.get("/rate-cards/v1?clientId=" + AgreementBuilder.CLIENT_ID + "&asOf=" + NOW, headers()));

        JsonNode agreement = card.path("agreements").get(0);
        JsonNode currentVersion = agreement.path("termVersions").get(0);
        JsonNode visa = currentVersion.path("capabilities").get(0);
        JsonNode mastercard = currentVersion.path("capabilities").get(1);
        assertAll(() -> assertEquals(1, card.path("agreements").size()),
            () -> assertEquals(List.of(ProductCode.ACQUIRING.getCode()), texts(agreement.path("products"))),
            () -> assertEquals("MOR", agreement.path("businessMode").asString()),
            () -> assertEquals(List.of("AED", "USD"),
                texts(agreement.path("capabilities").get(0).path("limits"), "limitValue")),
            () -> assertEquals(List.of("CURRENT", "SCHEDULED"), texts(agreement.path("termVersions"), "timing")),
            () -> assertEquals(CommercialCodes.Capability.CARD_VISA, visa.path("capabilityCode").asString()),
            () -> assertEquals(List.of("AED", "USD"), texts(visa.path("currencies"), "currency")),
            () -> assertEquals(List.of(CARD), texts(visa.path("currencies").get(0).path("profiles"), "pricingProfileCode")),
            () -> assertEquals(List.of(CARD_VISA_CREDIT, CARD_VISA, CARD),
                texts(visa.path("currencies").get(1).path("profiles"), "pricingProfileCode")),
            () -> assertEquals(2, visa.path("currencies").get(1).path("profiles").get(0).path("specificity").asInt()),
            () -> assertEquals(CommercialCodes.FeeEvent.SALE, visa.path("currencies").get(1).path("profiles").get(0)
                .path("feeEvents").get(0).path("feeEventCode").asString()),
            () -> assertEquals(List.of(CARD), texts(mastercard.path("currencies").get(0).path("profiles"),
                "pricingProfileCode")),
            () -> assertEquals("0.028", agreement.path("termVersions").get(1).path("capabilities").get(0).path("currencies")
                .get(1).path("profiles").get(0).path("feeEvents").get(0).path("components").get(0).path("rate").asString()));
    }

    /** 币种筛选：只返回 AED 块；CARD_MC 只开通 USD，因此没有币种块。 */
    @Test
    void test_currencyFilter() {
        JsonNode card = ok(200, http.get("/rate-cards/v1?clientId=" + AgreementBuilder.CLIENT_ID + "&asOf=" + NOW
            + "&currency=AED", headers()));

        JsonNode currentVersion = card.path("agreements").get(0).path("termVersions").get(0);
        assertAll(() -> assertEquals(List.of("AED"), texts(currentVersion.path("capabilities").get(0).path("currencies"),
                "currency")),
            () -> assertEquals(0, currentVersion.path("capabilities").get(1).path("currencies").size()));
    }

    /** 没有 ACTIVE 合同的 Client：空列表。 */
    @Test
    void test_noActiveAgreement() {
        JsonNode card = ok(200, http.get("/rate-cards/v1?clientId=930000000000999", headers()));

        assertEquals(0, card.path("agreements").size());
    }

    /** 数组字段。 */
    static List<String> texts(JsonNode array, String field) {
        return StreamSupport.stream(array.spliterator(), false).map(node -> node.path(field).asString()).toList();
    }

    /** 文本数组。 */
    static List<String> texts(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asString).toList();
    }
}
