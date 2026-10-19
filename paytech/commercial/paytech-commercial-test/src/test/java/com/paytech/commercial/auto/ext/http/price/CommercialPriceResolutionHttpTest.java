package com.paytech.commercial.auto.ext.http.price;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.CalculationBase;
import com.paytech.commercial.api.enums.CommercialPriceErrorCode;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.price.CommercialPriceApi;
import com.paytech.commercial.api.price.request.PricingFactsRequestBody;
import com.paytech.commercial.api.price.request.ResolveCommercialPriceRequestBody;
import com.paytech.commercial.api.term.request.CommercialTermPriceRequestBody;
import com.paytech.commercial.api.term.request.CreateCommercialTermPlanRequestBody;
import com.paytech.commercial.api.term.request.CreateCommercialTermVersionRequestBody;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.commercial.domainservice.repository.AgreementOperatingEntityRepository;
import com.paytech.commercial.test.base.term.AbstractCommercialTermApiTest;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.AgreementOperatingEntityBuilder;
import com.paytech.commercial.test.builder.CommercialTermPlanBuilder;
import com.paytech.commercial.test.builder.PricingProfileBuilder;
import com.paytech.commercial.test.builder.PricingProfileCondBuilder;
import com.paytech.foundation.common.error.CommonErrorCode;

import tools.jackson.databind.JsonNode;

/**
 * 价格命中与不可变回读（technique.md §2.4、§2.11 命中矩阵）：币种精确、具体度、优先级、事实缺失、无命中、非法事实、父链冲突、
 * 历史版本回读不受换版影响
 * <li>预置已生效第 1 版的 USD / AED 价格：通用卡 30、VISA 20、借记卡 15、VISA + CREDIT 10（USD），通用卡 30（AED）。</li>
 *
 * @see CommercialPriceApi
 */
class CommercialPriceResolutionHttpTest extends AbstractCommercialTermApiTest {
    private static final String RESOLUTIONS = "/commercial-prices/v1/resolutions";

    /** 借记卡画像（FUNDING_TYPE=DEBIT）。 */
    private static final String CARD_DEBIT = "WRITE_CARD_DEBIT";

    @Autowired
    private AgreementOperatingEntityRepository agreementOperatingEntityRepository;

    private CommercialTermVersion first;

    private CommercialTermPrice usdCard;

    private CommercialTermPrice usdVisa;

    private CommercialTermPrice usdDebit;

    private CommercialTermPrice usdVisaCredit;

    private CommercialTermPrice aedCard;

    /** 每例：标准前置数据、借记卡画像与已生效第 1 版的五条价格。 */
    @BeforeEach
    void setUpClassMocks() {
        mockBaseline();
        pricing.mockProfile(PricingProfileBuilder.template().pricingProfileId(PricingProfileBuilder.PRICING_PROFILE_ID + 3)
            .pricingProfileCode(CARD_DEBIT).status(PricingProfileStatus.ACTIVE),
            PricingProfileCondBuilder.template().dimensionCode(PricingDimension.FUNDING_TYPE).conditionValue("DEBIT"));
        first = mockActiveVersion();
        usdCard = mockPrice(first, 0, "USD", CARD, 30, "0.03");
        usdVisa = mockPrice(first, 1, "USD", CARD_VISA, 20, "0.025");
        usdDebit = mockPrice(first, 2, "USD", CARD_DEBIT, 15, "0.015");
        usdVisaCredit = mockPrice(first, 3, "USD", CARD_VISA_CREDIT, 10, "0.02");
        aedCard = mockPrice(first, 4, "AED", CARD, 30, "0.035");
    }

    /** 币种精确：AED 交易只在 AED 价格中命中通用卡，即使 USD 有更具体的价格。 */
    @Test
    void test_currencyExact() {
        JsonNode price = ok(200, resolve(request("AED", facts("VISA", "CREDIT"))));

        assertAll(() -> assertEquals(Long.toString(aedCard.getTermPriceId()), text(price, "priceId")),
            () -> assertEquals("AED", text(price, "currency")),
            () -> assertEquals("0.035", price.path("components").get(0).path("rate").asString()));
    }

    /** 具体度：VISA + CREDIT 命中两维度画像，结果携带完整父链、条件、组件与结算字段。 */
    @Test
    void test_specificity() {
        JsonNode price = ok(200, resolve(request("USD", facts("VISA", "CREDIT"))));

        JsonNode component = price.path("components").get(0);
        assertAll(() -> assertEquals(Long.toString(usdVisaCredit.getTermPriceId()), text(price, "priceId")),
            () -> assertEquals(CARD_VISA_CREDIT, text(price, "pricingProfileCode")),
            () -> assertEquals(CommercialCodes.PaymentMethod.CARD, text(price, "paymentMethodCode")),
            () -> assertEquals(Long.toString(CommercialTermPlanBuilder.TERM_PLAN_ID), text(price, "planId")),
            () -> assertEquals(Long.toString(AgreementBuilder.CLIENT_ID), text(price, "clientId")),
            () -> assertTrue(price.path("localPartnerId").isNull()),
            () -> assertEquals("BLENDED", text(price, "pricingModel")),
            () -> assertEquals("EXCLUSIVE", text(price, "feeTaxTreatment")),
            () -> assertEquals("CARD_BRAND", price.path("conditions").get(0).path("dimensionCode").asString()),
            () -> assertEquals("FUNDING_TYPE", price.path("conditions").get(1).path("dimensionCode").asString()),
            () -> assertEquals("RETURN_PRORATA", component.path("refundBehavior").asString()),
            () -> assertTrue(component.path("bandBasis").isNull()), () -> assertTrue(component.path("fixedAmount").isNull()),
            () -> assertEquals(2, price.path("settlement").path("settlementBusinessDays").asInt()),
            () -> assertEquals("UTC", price.path("settlement").path("settlementTimezone").asString()));
    }

    /** 优先级：VISA + DEBIT 同时命中 VISA（20）与借记卡（15）两个单维度画像，取优先级小者；资金类型确认不适用时取 VISA。 */
    @Test
    void test_priority() {
        JsonNode debit = ok(200, resolve(request("USD", facts("VISA", "DEBIT"))));
        JsonNode notApplicable = ok(200, resolve(request("USD", facts("VISA", null))));

        assertAll(() -> assertEquals(Long.toString(usdDebit.getTermPriceId()), text(debit, "priceId")),
            () -> assertEquals(Long.toString(usdVisa.getTermPriceId()), text(notApplicable, "priceId")));
    }

    /** 事实缺失：候选用到 FUNDING_TYPE 而事实未给出，422，不退回通用卡价格。 */
    @Test
    void test_factsIncomplete() {
        Map<String, String> brandOnly = new HashMap<>();
        brandOnly.put(PricingDimension.CARD_BRAND.getCode(), "VISA");

        assertProblem(resolve(request("USD", brandOnly)), 422, CommercialPriceErrorCode.PRICING_FACTS_INCOMPLETE.getCode(),
            "FUNDING_TYPE");
    }

    /** 无命中：支付方式没有任何画像、或币种没有价格，422。 */
    @Test
    void test_priceNotFound() {
        ResolveCommercialPriceRequestBody applePay = request("USD", facts("VISA", "CREDIT"));
        applePay.getPaymentFacts().setPaymentMethodCode(CommercialCodes.PaymentMethod.APPLE_PAY);

        assertAll(
            () -> assertProblem(resolve(applePay), 422, CommercialPriceErrorCode.PRICE_NOT_FOUND.getCode(), "APPLE_PAY"),
            () -> assertProblem(resolve(request("EUR", facts("VISA", "CREDIT"))), 422,
                CommercialPriceErrorCode.PRICE_NOT_FOUND.getCode(), "EUR"));
    }

    /** 非法事实：目录外维度、CURRENCY 与交易币种不一致，422。 */
    @Test
    void test_invalidFact() {
        Map<String, String> unknown = facts("VISA", "CREDIT");
        unknown.put("COLOR", "RED");
        Map<String, String> currency = facts("VISA", "CREDIT");
        currency.put(PricingDimension.CURRENCY.getCode(), "AED");

        assertAll(
            () -> assertProblem(resolve(request("USD", unknown)), 422,
                CommercialPriceErrorCode.INVALID_PRICING_FACT.getCode(), "COLOR"),
            () -> assertProblem(resolve(request("USD", currency)), 422,
                CommercialPriceErrorCode.INVALID_PRICING_FACT.getCode(), "CURRENCY"));
    }

    /** 父链冲突：合同、条款类型、Owner 不符或版本不是该商业时点的适用版本，409；版本不存在 404。 */
    @Test
    void test_parentChainConflict() {
        ResolveCommercialPriceRequestBody wrongAgreement = request("USD", facts("VISA", "CREDIT"));
        wrongAgreement.setAgreementId("930000000000999");
        ResolveCommercialPriceRequestBody wrongType = request("USD", facts("VISA", "CREDIT"));
        wrongType.setTermType(CommercialCodes.TermType.PAYOUT_FEE);
        ResolveCommercialPriceRequestBody partnerOwner = request("USD", facts("VISA", "CREDIT"));
        partnerOwner.setClientId(null);
        partnerOwner.setLocalPartnerId("930000000000998");
        ResolveCommercialPriceRequestBody beforeEffective = request("USD", facts("VISA", "CREDIT"));
        beforeEffective.setContextAsOfTime("2026-08-31T23:59:59Z");
        ResolveCommercialPriceRequestBody missingVersion = request("USD", facts("VISA", "CREDIT"));
        missingVersion.setTermVersionId("930000000000777");

        String conflict = CommercialPriceErrorCode.PRICE_AMBIGUOUS.getCode();
        assertAll(() -> assertProblem(resolve(wrongAgreement), 409, conflict, "父链冲突"),
            () -> assertProblem(resolve(wrongType), 409, conflict, "父链冲突"),
            () -> assertProblem(resolve(partnerOwner), 409, conflict, "父链冲突"),
            () -> assertProblem(resolve(beforeEffective), 409, conflict, "时点适用 false"),
            () -> assertProblem(resolve(missingVersion), 404, CommonErrorCode.NOT_FOUND.getCode(), "不存在"));
    }

    /** 回读：按 priceId 返回同一对象，不存在 404；版本价格全集按币种、优先级排序，条款类型不符 404。 */
    @Test
    void test_readBack() {
        JsonNode resolved = ok(200, resolve(request("USD", facts("VISA", "CREDIT"))));
        JsonNode byId = ok(200, http.get("/commercial-prices/v1/" + usdVisaCredit.getTermPriceId(), headers()));
        JsonNode list = ok(200, http.get(versionPrices(first.getTermVersionId(), "CLIENT_PRICING", "USD"), headers()));

        assertAll(() -> assertEquals(resolved, byId), () -> assertEquals(4, list.path("prices").size()),
            () -> assertEquals(Long.toString(usdVisaCredit.getTermPriceId()),
                list.path("prices").get(0).path("priceId").asString()),
            () -> assertEquals(Long.toString(usdCard.getTermPriceId()), list.path("prices").get(3).path("priceId").asString()),
            () -> assertEquals(5, ok(200, http.get(versionPrices(first.getTermVersionId(), "CLIENT_PRICING", null),
                headers())).path("prices").size()));
        assertProblem(http.get("/commercial-prices/v1/930000000000777", headers()), 404, CommonErrorCode.NOT_FOUND.getCode(),
            "价格不存在");
        assertProblem(http.get(versionPrices(first.getTermVersionId(), "PAYOUT_FEE", null), headers()), 404,
            CommonErrorCode.NOT_FOUND.getCode(), "条款类型");
    }

    /** 历史版本回读不受换版影响：第 2 版经 API 生效后，原时点仍按第 1 版命中旧价格；新时点只能用第 2 版。 */
    @Test
    void test_historicalVersionUnaffected() {
        JsonNode second = ok(201, createVersionResult(Long.toString(CommercialTermPlanBuilder.TERM_PLAN_ID), "2", null));
        JsonNode priced = replacePrices(second, price("USD", CARD, 1, percentage("MDR", "0.01")));
        command(ok(200, scheduleResult(approved(priced), NOW)), "activate", CHECKER);

        ResolveCommercialPriceRequestBody historical = request("USD", facts("VISA", "CREDIT"));
        historical.setContextAsOfTime("2026-09-05T00:00:00Z");
        ResolveCommercialPriceRequestBody staleAtNow = request("USD", facts("VISA", "CREDIT"));
        ResolveCommercialPriceRequestBody current = request("USD", facts("VISA", "CREDIT"));
        current.setTermVersionId(text(second, "versionId"));

        JsonNode old = ok(200, resolve(historical));
        JsonNode renewed = ok(200, resolve(current));
        assertAll(() -> assertEquals(Long.toString(usdVisaCredit.getTermPriceId()), text(old, "priceId")),
            () -> assertEquals("0.02", old.path("components").get(0).path("rate").asString()),
            () -> assertEquals("0.01", renewed.path("components").get(0).path("rate").asString()),
            () -> assertEquals(old, ok(200, http.get("/commercial-prices/v1/" + usdVisaCredit.getTermPriceId(), headers()))));
        assertProblem(resolve(staleAtNow), 409, CommercialPriceErrorCode.PRICE_AMBIGUOUS.getCode(), "时点适用 false");
    }

    /**
     * 已排期且生效起点已到、但尚未被作业切成 ACTIVE：排期已把第 1 版窗口终点写成第 2 版起点，NOW 时点唯一适用第 2 版（SCHEDULED），
     * 第 1 版在 NOW 不再适用，此前时点仍适用。
     */
    @Test
    void test_scheduledEffectiveBeforeFlip() {
        JsonNode second = ok(201, createVersionResult(Long.toString(CommercialTermPlanBuilder.TERM_PLAN_ID), "2", null));
        JsonNode scheduled = ok(200, scheduleResult(approved(replacePrices(second,
            price("USD", CARD, 1, percentage("MDR", "0.01")))), NOW));
        ResolveCommercialPriceRequestBody atNow = request("USD", facts("VISA", "CREDIT"));
        atNow.setTermVersionId(text(scheduled, "versionId"));
        ResolveCommercialPriceRequestBody historical = request("USD", facts("VISA", "CREDIT"));
        historical.setContextAsOfTime("2026-09-05T00:00:00Z");

        var previous = testMapper.termVersion(first.getTermVersionId());
        assertAll(() -> assertEquals("SCHEDULED", text(scheduled, "status")),
            () -> assertEquals("ACTIVE", previous.get("status")),
            () -> assertEquals("0.01", ok(200, resolve(atNow)).path("components").get(0).path("rate").asString()),
            () -> assertEquals(Long.toString(usdVisaCredit.getTermPriceId()), text(ok(200, resolve(historical)), "priceId")),
            () -> assertProblem(resolve(request("USD", facts("VISA", "CREDIT"))), 409,
                CommercialPriceErrorCode.PRICE_AMBIGUOUS.getCode(), "时点适用 false"));
    }

    /**
     * Partner 条款：PARTNER 合同经覆盖表覆盖 AE993，方案只冻结 Local Partner、版本冻结合同；价格不带结算字段，命中结果 settlement 为 null，
     * Owner 为 Local Partner。
     */
    @Test
    void test_partnerShareTerm() {
        long partnerAgreementId = AgreementBuilder.AGREEMENT_ID + 50;
        long localPartnerId = 930000000000950L;
        graph.mockAgreement(AgreementBuilder.template().agreementId(partnerAgreementId).agreementNo("WRITE-PARTNER-1")
            .agreementType(AgreementAgreementType.PARTNER).businessMode(null).fundMode(null).pricingAuthority(null)
            .clientId(null).localPartnerId(localPartnerId).entityNo(null).status(AgreementStatus.ACTIVE));
        agreementOperatingEntityRepository.insertAll(List.of(AgreementOperatingEntityBuilder.template()
            .agreementId(partnerAgreementId).build()));
        CreateCommercialTermPlanRequestBody planBody = planRequest();
        planBody.setPlanCode("PLAN-P-1");
        planBody.setTermType(CommercialCodes.TermType.PARTNER_SHARE);
        planBody.setAgreementId(Long.toString(partnerAgreementId));
        planBody.setClientId(null);
        planBody.setLocalPartnerId(Long.toString(localPartnerId));
        JsonNode plan = ok(201, http.post(TERMS + "/plans", headers(MAKER), planBody));
        CreateCommercialTermVersionRequestBody versionBody = new CreateCommercialTermVersionRequestBody();
        versionBody.setExpectedChangeVersion(text(plan, "changeVersion"));
        versionBody.setAgreementId(Long.toString(partnerAgreementId));
        versionBody.setPricingModel("BLENDED");
        versionBody.setFeeTaxTreatment("EXCLUSIVE");
        JsonNode version = ok(201, http.post(TERMS + "/plans/" + text(plan, "planId") + "/versions", headers(MAKER), versionBody));
        CommercialTermPriceRequestBody share = price("USD", CARD, 1, percentage("SHARE", "0.2"));
        share.getComponents().getFirst().setCalculationBase(CalculationBase.ACTUAL_CLIENT_FEE.getCode());
        share.setSettlementBusinessDays(null);
        share.setBusinessCalendarCode(null);
        share.setSettlementTimezone(null);
        share.setCutoffSecondOfDay(null);
        JsonNode active = command(ok(200, scheduleResult(approved(replacePrices(version, share)), NOW)), "activate", CHECKER);
        ResolveCommercialPriceRequestBody request = request("USD", facts("VISA", "CREDIT"));
        request.setTermVersionId(text(active, "versionId"));
        request.setAgreementId(Long.toString(partnerAgreementId));
        request.setTermType(CommercialCodes.TermType.PARTNER_SHARE);
        request.setClientId(null);
        request.setLocalPartnerId(Long.toString(localPartnerId));

        JsonNode price = ok(200, resolve(request));

        assertAll(() -> assertTrue(price.path("settlement").isNull()), () -> assertTrue(price.path("clientId").isNull()),
            () -> assertEquals(Long.toString(localPartnerId), text(price, "localPartnerId")),
            () -> assertEquals("ACTUAL_CLIENT_FEE", price.path("components").get(0).path("calculationBase").asString()));
    }

    /** 发送命中请求。 */
    EntityExchangeResult<byte[]> resolve(ResolveCommercialPriceRequestBody body) {
        return http.post(RESOLUTIONS, headers(), body);
    }

    /** 默认请求：第 1 版、AE993、CLIENT_PRICING、商业时点 NOW、CARD。 */
    ResolveCommercialPriceRequestBody request(String currency, Map<String, String> dimensions) {
        PricingFactsRequestBody facts = new PricingFactsRequestBody();
        facts.setPaymentMethodCode(CommercialCodes.PaymentMethod.CARD);
        facts.setDimensions(dimensions);
        ResolveCommercialPriceRequestBody body = new ResolveCommercialPriceRequestBody();
        body.setTermVersionId(Long.toString(first.getTermVersionId()));
        body.setEntityNo(ENTITY_NO);
        body.setAgreementId(Long.toString(AgreementBuilder.AGREEMENT_ID));
        body.setContextAsOfTime(NOW.toString());
        body.setTermType(CommercialCodes.TermType.CLIENT_PRICING);
        body.setCurrency(currency);
        body.setClientId(Long.toString(AgreementBuilder.CLIENT_ID));
        body.setPaymentFactId("930000000000881");
        body.setPaymentFacts(facts);
        return body;
    }

    /** 卡品牌与资金类型事实；资金类型为 null 表示已确认不适用。 */
    static Map<String, String> facts(String brand, String funding) {
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put(PricingDimension.CARD_BRAND.getCode(), brand);
        facts.put(PricingDimension.FUNDING_TYPE.getCode(), funding);
        return facts;
    }

    /** 版本价格全集路径。 */
    static String versionPrices(long versionId, String termType, String currency) {
        return "/commercial-term-versions/v1/" + versionId + "/prices?entityNo=" + ENTITY_NO + "&termType=" + termType
            + (currency == null ? "" : "&currency=" + currency);
    }
}
