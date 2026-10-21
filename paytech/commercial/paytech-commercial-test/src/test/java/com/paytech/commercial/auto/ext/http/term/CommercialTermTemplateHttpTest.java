package com.paytech.commercial.auto.ext.http.term;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.CommercialTermOwnerType;
import com.paytech.commercial.api.enums.CommercialTermPlanStatus;
import com.paytech.commercial.api.enums.PriceBenchmarkStatus;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.term.request.CommercialTermComponentRequestBody;
import com.paytech.commercial.api.term.request.CommercialTermPriceRequestBody;
import com.paytech.commercial.api.term.request.CreateCommercialTermTemplateRequestBody;
import com.paytech.commercial.api.term.request.CreateCommercialTermVersionRequestBody;
import com.paytech.commercial.api.term.request.FillCommercialTermVersionFromTemplateRequestBody;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.CommercialTermComponentComponentType;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.commercial.test.base.term.AbstractCommercialTermApiTest;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.AgreementCapabilityBuilder;
import com.paytech.commercial.test.builder.AgreementPartyBuilder;
import com.paytech.commercial.test.builder.CommercialTermComponentBuilder;
import com.paytech.commercial.test.builder.CommercialTermPlanBuilder;
import com.paytech.commercial.test.builder.CommercialTermPriceBuilder;
import com.paytech.commercial.test.builder.CommercialTermVersionBuilder;
import com.paytech.commercial.test.builder.PricingProfileBuilder;
import com.paytech.commercial.test.builder.PricingProfileCondBuilder;
import com.paytech.foundation.common.error.CommonErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 标准价目模板与基于模板生成（开通与定价模型调整 technique.md §4.3–§4.5，AC-04 / AC-05 / AC-07 / AC-10）
 * <li>合同 AE993 开通 CARD：CURRENCY=USD、CARD_BRAND=VISA,MASTERCARD；未开通 APPLE_PAY 与存卡。</li>
 * <li>模板「标准价 · AE993」第 1 版（ACTIVE）：USD 通用卡（MDR 3% + 存卡费）、USD VISA（MDR 2.5%，最低 0.50）、USD AMEX（MDR + 存卡费）、
 * AED 通用卡（MDR + 存卡费）、USD APPLE_PAY 五条价格。</li>
 */
class CommercialTermTemplateHttpTest extends AbstractCommercialTermApiTest {
    private static final String TEMPLATES = TERMS + "/templates";

    private static final String AMEX = "WRITE_CARD_AMEX";

    private static final String APPLE_PAY = "WRITE_APPLE_PAY";

    private static final long TEMPLATE_PLAN_ID = 930000000000561L;

    private static final long TEMPLATE_V1_ID = 930000000000571L;

    private static final long TEMPLATE_PRICE_ID = 930000000000581L;

    /** 每例：目录、ACTIVE 合同（含 CLIENT 签署 Party）、卡与 APPLE_PAY 画像、CARD 开通项（USD + VISA / MASTERCARD）。 */
    @BeforeEach
    void setUpClassMocks() {
        catalog.mockSeedCatalog();
        graph.mockAgreement(AgreementBuilder.template().status(AgreementStatus.ACTIVE)).party(AgreementPartyBuilder.template());
        mockCardProfiles(PricingProfileStatus.ACTIVE);
        pricing.mockProfile(PricingProfileBuilder.template().pricingProfileId(PricingProfileBuilder.PRICING_PROFILE_ID + 3)
            .pricingProfileCode(AMEX).status(PricingProfileStatus.ACTIVE),
            PricingProfileCondBuilder.template().dimensionCode(PricingDimension.CARD_BRAND)
                .conditionValue(CommercialCodes.CardBrand.AMEX));
        pricing.mockProfile(PricingProfileBuilder.template().pricingProfileId(PricingProfileBuilder.PRICING_PROFILE_ID + 4)
            .pricingProfileCode(APPLE_PAY).paymentMethodCode(CommercialCodes.PaymentMethod.APPLE_PAY)
            .status(PricingProfileStatus.ACTIVE));
        subscriptions.mockCapability(AgreementCapabilityBuilder.template().agreementId(AgreementBuilder.AGREEMENT_ID)
                .entityNo(ENTITY_NO)).currencies("USD").limit(PricingDimension.CARD_BRAND, CommercialCodes.CardBrand.VISA)
            .limit(PricingDimension.CARD_BRAND, CommercialCodes.CardBrand.MASTERCARD);
    }

    /** 模板方案：201 / 同内容重放 200 / 同号不同内容 409 / Partner 条款类型 400；模板列表含生效与最新版本，合同方案列表不含模板。 */
    @Test
    void test_templatePlan_createAndList() {
        JsonNode created = ok(201, http.post(TEMPLATES, headers(MAKER), templateBody("TPL-STD", "标准价 · AE993")));
        JsonNode replay = ok(200, http.post(TEMPLATES, headers(MAKER), templateBody("TPL-STD", "标准价 · AE993")));
        CreateCommercialTermTemplateRequestBody partner = templateBody("TPL-PT", "Partner");
        partner.setTermType(CommercialCodes.TermType.PARTNER_SHARE);

        assertAll(() -> assertEquals(CommercialTermOwnerType.PLATFORM_TEMPLATE.getCode(), text(created, "ownerType")),
            () -> assertEquals("标准价 · AE993", text(created, "templateName")),
            () -> assertEquals(ENTITY_NO, text(created, "entityNo")), () -> assertTrue(created.path("agreementId").isNull()),
            () -> assertTrue(created.path("clientId").isNull()), () -> assertEquals(text(created, "planId"), text(replay, "planId")),
            () -> assertProblem(http.post(TEMPLATES, headers(MAKER), templateBody("TPL-STD", "优惠价")), 409,
                CommonErrorCode.CONCURRENT_REQUEST.getCode(), "方案编号已被不同内容的方案使用"),
            () -> assertProblem(http.post(TEMPLATES, headers(MAKER), partner), 400,
                CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), "只支持已登记的 Client 条款类型"));

        mockActiveTemplate();
        createPlan();
        JsonNode templates = ok(200, http.get(TEMPLATES + "?entityNo=" + ENTITY_NO, headers())).path("templates");
        JsonNode plans = ok(200, http.get(TERMS + "/plans", headers())).path("items");
        assertAll(() -> assertEquals(List.of("TPL-MOCK", "TPL-STD"),
                templates.valueStream().map(node -> text(node, "planCode")).toList()),
            () -> assertEquals("1", text(templates.get(0), "activeVersionNo")),
            () -> assertEquals(CommercialTermVersionStatus.ACTIVE.getCode(), text(templates.get(0), "latestVersionStatus")),
            () -> assertTrue(templates.get(1).path("latestVersionId").isNull()),
            () -> assertEquals(List.of("PLAN-C-1"), plans.valueStream().map(node -> text(node, "planCode")).toList()),
            () -> assertEquals(CommercialTermOwnerType.CLIENT.getCode(), text(plans.get(0), "ownerType")));
    }

    /** 模板版本经正式接口推进：无合同、无签署、只做结构校验即可排期激活（AC-04）；价格命中接口不命中模板价格。 */
    @Test
    void test_templateVersion_lifecycleWithoutSignature() {
        JsonNode plan = ok(201, http.post(TEMPLATES, headers(MAKER), templateBody("TPL-STD", "标准价 · AE993")));
        JsonNode version = ok(201, createVersionResult(text(plan, "planId"), text(plan, "changeVersion"), null));
        JsonNode priced = replacePrices(version, price("USD", CARD, 1, percentage("MDR", "0.03")),
            price("USD", APPLE_PAY, 2, percentage("MDR", "0.02")));
        JsonNode approvedVersion = approved(priced);
        assertProblem(acceptanceResult(approvedVersion, AgreementPartyBuilder.PARTY_ID, "SIGN-TPL"), 400,
            CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), "标准价目模板版本无需客户签署");
        JsonNode active = command(ok(200, scheduleResult(approvedVersion, NOW)), "activate", CHECKER);

        long priceId = Long.parseLong(text(ok(200, http.get("/commercial-term-versions/v1/" + text(active, "versionId")
            + "/prices?entityNo=" + ENTITY_NO + "&termType=" + CommercialCodes.TermType.CLIENT_PRICING, headers()))
            .path("prices").get(0), "priceId"));
        assertAll(() -> assertTrue(version.path("agreementId").isNull()),
            () -> assertEquals(CommercialTermOwnerType.PLATFORM_TEMPLATE.getCode(), text(active, "ownerType")),
            () -> assertEquals(0, priced.path("relevanceWarnings").size()),
            () -> assertEquals(CommercialTermVersionStatus.ACTIVE.getCode(), text(active, "status")),
            () -> assertEquals(CommercialTermPlanStatus.ACTIVE.getCode(), testMapper.termPlanStatus(Long.parseLong(
                text(plan, "planId")))),
            () -> assertProblem(http.get("/commercial-prices/v1/" + priceId, headers()), 404,
                CommonErrorCode.NOT_FOUND.getCode(), "标准价目模板不参与价格命中"));
    }

    /** 模板版本读回价格带结算四字段（模板按 Client 条款结构，没有 clientId 也一样）；按读回内容原样整体保存 200，内容不变（缺陷回归 D2）。 */
    @Test
    void test_templateVersion_readBackRoundTrip() {
        JsonNode plan = ok(201, http.post(TEMPLATES, headers(MAKER), templateBody("TPL-STD", "标准价 · AE993")));
        JsonNode version = replacePrices(ok(201, createVersionResult(text(plan, "planId"), text(plan, "changeVersion"), null)),
            price("USD", CARD, 1, percentage("MDR", "0.03"), tokenFee("USD")), price("USD", APPLE_PAY, 2, percentage("MDR", "0.02")));
        JsonNode readBack = versionPrices(version);

        JsonNode settlement = readBack.get(0).path("settlement");
        assertAll(() -> assertEquals(2, settlement.path("settlementBusinessDays").asInt()),
            () -> assertEquals(CommercialCodes.AE_BUSINESS_DAYS, text(settlement, "businessCalendarCode")),
            () -> assertEquals("UTC", text(settlement, "settlementTimezone")),
            () -> assertEquals(86399, settlement.path("cutoffSecondOfDay").asInt()),
            () -> assertTrue(readBack.get(1).path("settlement").isObject()));

        JsonNode saved = replacePrices(version, readBack.valueStream().map(CommercialTermTemplateHttpTest::requestOf)
            .toArray(CommercialTermPriceRequestBody[]::new));
        assertEquals(readBack.valueStream().map(CommercialTermTemplateHttpTest::content).toList(),
            versionPrices(saved).valueStream().map(CommercialTermTemplateHttpTest::content).toList());
    }

    /** 基于模板生成：只取已开通项 × 开通币种 × 相容条件，未开通功能的组件不生成；每行带来源模板价格，版本带来源模板版本（AC-05）。 */
    @Test
    void test_generateFromTemplate() {
        mockActiveTemplate();

        JsonNode version = createFromTemplate(createPlan(), TEMPLATE_V1_ID);
        JsonNode prices = versionPrices(version);

        assertAll(() -> assertEquals(Long.toString(TEMPLATE_V1_ID), text(version, "sourceTemplateVersionId")),
            () -> assertEquals(List.of(CARD, CARD_VISA), prices.valueStream().map(node -> text(node, "pricingProfileCode"))
                .toList()),
            () -> assertEquals(List.of("USD", "USD"), prices.valueStream().map(node -> text(node, "currency")).toList()),
            () -> assertEquals(Long.toString(TEMPLATE_PRICE_ID), text(prices.get(0), "sourceTemplatePriceId")),
            () -> assertEquals(Long.toString(TEMPLATE_PRICE_ID + 1), text(prices.get(1), "sourceTemplatePriceId")),
            () -> assertEquals(List.of("MDR"), prices.get(0).path("components").valueStream()
                .map(node -> text(node, "componentCode")).toList()),
            () -> assertEquals(2, prices.get(0).path("settlement").path("settlementBusinessDays").asInt()),
            () -> assertEquals("BLENDED", text(version, "pricingModel")), () -> assertEquals(0, version.path("belowStandardCount")
                .asInt()), () -> assertEquals(0, version.path("relevanceWarnings").size()));
    }

    /**
     * 开通存卡后基于模板生成与补齐（缺陷回归 DEMO2-CL-101）：价格行须先对应已开通的 CARD（币种在条件内、画像条件相容），AED 通用卡与 AMEX
     * 两行虽有相关的存卡费组件也整行不生成；USD 通用卡保留 MDR + 存卡费；补齐同样只追加 USD 通用卡。
     */
    @Test
    void test_generateAndFill_rowNeedsPaymentMethodCapability() {
        mockTokenStorage();
        mockActiveTemplate();

        JsonNode version = createFromTemplate(createPlan(), TEMPLATE_V1_ID);
        JsonNode generated = versionPrices(version);
        assertAll(() -> assertEquals(List.of(CARD, CARD_VISA), generated.valueStream()
                .map(node -> text(node, "pricingProfileCode")).toList()),
            () -> assertEquals(List.of("USD", "USD"), generated.valueStream().map(node -> text(node, "currency")).toList()),
            () -> assertEquals(List.of("MDR", "TOKEN_FEE"), generated.get(0).path("components").valueStream()
                .map(node -> text(node, "componentCode")).toList()),
            () -> assertEquals(0, version.path("relevanceWarnings").size()));

        JsonNode trimmed = replacePrices(version, price("USD", CARD_VISA, 1, percentage("MDR", "0.025")));
        JsonNode filled = ok(200, http.post(TERMS + "/versions/" + text(trimmed, "versionId") + "/fill-from-template",
            headers(MAKER), fillBody(trimmed, TEMPLATE_V1_ID)));
        JsonNode prices = versionPrices(filled);
        assertAll(() -> assertEquals(List.of(CARD_VISA, CARD), prices.valueStream().map(node -> text(node, "pricingProfileCode"))
                .toList()),
            () -> assertEquals(List.of("USD", "USD"), prices.valueStream().map(node -> text(node, "currency")).toList()),
            () -> assertEquals(List.of("MDR", "TOKEN_FEE"), prices.get(1).path("components").valueStream()
                .map(node -> text(node, "componentCode")).toList()),
            () -> assertEquals(Long.toString(TEMPLATE_PRICE_ID), text(prices.get(1), "sourceTemplatePriceId")),
            () -> assertEquals(0, filled.path("relevanceWarnings").size()));
    }

    /** 模板版本须为同条款类型的已生效模板；复制来源与模板来源互斥。 */
    @Test
    void test_generateFromTemplate_invalidSource() {
        mockActiveTemplate();
        JsonNode plan = createPlan();
        CreateCommercialTermVersionRequestBody both = templateVersionBody(plan, TEMPLATE_V1_ID);
        both.setCopyFromVersionId(Long.toString(TEMPLATE_V1_ID));
        testMapper.overwriteVersionStatus(TEMPLATE_V1_ID, CommercialTermVersionStatus.APPROVED.getCode());

        assertAll(() -> assertProblem(http.post(TERMS + "/plans/" + text(plan, "planId") + "/versions", headers(MAKER), both),
                400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), "只能给出一个"),
            () -> assertProblem(http.post(TERMS + "/plans/" + text(plan, "planId") + "/versions", headers(MAKER),
                templateVersionBody(plan, TEMPLATE_V1_ID)), 400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(),
                "标准价目模板版本尚未生效"));
    }

    /** 破标：费率低于模板、模板有最低收费而合同为空为 BELOW_STANDARD，不低于为 AT_OR_ABOVE，无来源为 NO_BENCHMARK；版本与列表显示破标数（AC-07）。 */
    @Test
    void test_benchmark() {
        mockActiveTemplate();
        JsonNode version = createFromTemplate(createPlan(), TEMPLATE_V1_ID);
        JsonNode generated = versionPrices(version);
        CommercialTermPriceRequestBody card = price("USD", CARD, 1, percentage("MDR", "0.03"));
        card.setSourceTemplatePriceId(text(generated.get(0), "sourceTemplatePriceId"));
        CommercialTermPriceRequestBody visa = price("USD", CARD_VISA, 2, percentage("MDR", "0.02"));
        visa.setSourceTemplatePriceId(text(generated.get(1), "sourceTemplatePriceId"));
        CommercialTermPriceRequestBody manual = price("USD", CARD_VISA_CREDIT, 3, percentage("MDR", "0.01"));

        JsonNode saved = replacePrices(version, card, visa, manual);

        JsonNode prices = versionPrices(saved);
        JsonNode row = ok(200, http.get(TERMS + "/versions?planId=" + text(saved, "planId"), headers())).path("items").get(0);
        assertAll(() -> assertEquals(PriceBenchmarkStatus.AT_OR_ABOVE.getCode(), benchmark(prices.get(0)).path("status").asString()),
            () -> assertEquals(PriceBenchmarkStatus.BELOW_STANDARD.getCode(), benchmark(prices.get(1)).path("status").asString()),
            () -> assertEquals("0.025", benchmark(prices.get(1)).path("standardRate").asString()),
            () -> assertEquals("0.50", benchmark(prices.get(1)).path("standardMinAmount").asString()),
            () -> assertEquals(PriceBenchmarkStatus.NO_BENCHMARK.getCode(), benchmark(prices.get(2)).path("status").asString()),
            () -> assertEquals(1, saved.path("belowStandardCount").asInt()),
            () -> assertEquals(1, ok(200, http.get(TERMS + "/versions/" + text(saved, "versionId"), headers()))
                .path("belowStandardCount").asInt()),
            () -> assertEquals(1, row.path("belowStandardCount").asInt()),
            () -> assertEquals(Long.toString(TEMPLATE_V1_ID), text(row, "sourceTemplateVersionId")),
            () -> assertEquals(false, row.path("hasAcceptance").asBoolean()),
            () -> assertEquals(false, row.path("signatureExempt").asBoolean()));
    }

    /** 从模板补齐：只追加缺失的价格行，同币种优先级冲突时顺延；来源字段保留；非 DRAFT 拒绝。 */
    @Test
    void test_fillFromTemplate() {
        mockActiveTemplate();
        JsonNode version = createFromTemplate(createPlan(), TEMPLATE_V1_ID);
        CommercialTermPriceRequestBody card = price("USD", CARD, 2, percentage("MDR", "0.028"));
        JsonNode trimmed = replacePrices(version, card);

        JsonNode filled = ok(200, http.post(TERMS + "/versions/" + text(trimmed, "versionId") + "/fill-from-template",
            headers(MAKER), fillBody(trimmed, TEMPLATE_V1_ID)));

        JsonNode prices = versionPrices(filled);
        assertAll(() -> assertEquals(List.of(CARD, CARD_VISA), prices.valueStream().map(node -> text(node, "pricingProfileCode"))
                .toList()),
            () -> assertEquals("0.028", prices.get(0).path("components").get(0).path("rate").asString()),
            () -> assertTrue(prices.get(0).path("sourceTemplatePriceId").isNull()),
            () -> assertEquals(3, prices.get(1).path("priorityNo").asInt()),
            () -> assertEquals(Long.toString(TEMPLATE_PRICE_ID + 1), text(prices.get(1), "sourceTemplatePriceId")),
            () -> assertEquals(Long.toString(TEMPLATE_V1_ID), text(filled, "sourceTemplateVersionId")));

        JsonNode approvedVersion = approved(filled);
        assertProblem(http.post(TERMS + "/versions/" + text(approvedVersion, "versionId") + "/fill-from-template",
            headers(MAKER), fillBody(approvedVersion, TEMPLATE_V1_ID)), 400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(),
            "只有 DRAFT 版本可以从标准价目模板补齐");
    }

    /** 模板出新版本生效后，引用旧版本的合同版本显示「标准价已更新」，合同价格不变（AC-10）。 */
    @Test
    void test_templateUpdatedHint() {
        mockActiveTemplate();
        JsonNode contract = createFromTemplate(createPlan(), TEMPLATE_V1_ID);
        JsonNode before = versionPrices(contract);
        JsonNode templateRoot = ok(200, http.get(TERMS + "/versions/" + TEMPLATE_V1_ID, headers()));
        JsonNode v2 = ok(201, createVersionResult(Long.toString(TEMPLATE_PLAN_ID), text(templateRoot, "changeVersion"),
            Long.toString(TEMPLATE_V1_ID)));
        command(ok(200, scheduleResult(approved(v2), NOW)), "activate", CHECKER);

        JsonNode after = ok(200, http.get(TERMS + "/versions/" + text(contract, "versionId"), headers()));
        assertAll(() -> assertEquals("2", after.path("templateUpdate").path("latestVersionNo").asString()),
            () -> assertEquals(text(v2, "versionId"), after.path("templateUpdate").path("latestTemplateVersionId").asString()),
            () -> assertEquals(before, versionPrices(after)),
            () -> assertTrue(ok(200, http.get(TERMS + "/versions/" + text(v2, "versionId"), headers())).path("templateUpdate")
                .isNull()));
    }

    /** 预置已生效的模板方案（BCR 根修订 2）与第 1 版（ACTIVE，自 V1_FROM 起）及五条价格。 */
    void mockActiveTemplate() {
        List<CommercialTermVersion> versions = pricing.mockPlan(CommercialTermPlanBuilder.template().termPlanId(TEMPLATE_PLAN_ID)
                .planCode("TPL-MOCK").ownerType(CommercialTermOwnerType.PLATFORM_TEMPLATE).templateName("标准价 · AE993")
                .agreementId(null).clientId(null).status(CommercialTermPlanStatus.ACTIVE), 2,
            CommercialTermVersionBuilder.template().termVersionId(TEMPLATE_V1_ID).agreementId(null)
                .status(CommercialTermVersionStatus.ACTIVE).activeSlot(1).effectiveFrom(V1_FROM));
        CommercialTermVersion v1 = versions.getFirst();
        templatePrice(v1, 0, "USD", CARD, 1, mdr(0, "0.03"), tokenFeeComponent(0, "USD"));
        templatePrice(v1, 1, "USD", CARD_VISA, 2, mdr(1, "0.025").minAmount(new BigDecimal("0.50")).currency("USD"));
        templatePrice(v1, 2, "USD", AMEX, 3, mdr(2, "0.035"), tokenFeeComponent(1, "USD"));
        templatePrice(v1, 3, "AED", CARD, 1, mdr(3, "0.03"), tokenFeeComponent(2, "AED"));
        templatePrice(v1, 4, "USD", APPLE_PAY, 4, mdr(4, "0.02"));
    }

    /** 模板版本下一条价格；价格 ID 为 TEMPLATE_PRICE_ID + index。 */
    CommercialTermPrice templatePrice(CommercialTermVersion version, int index, String currency, String profileCode,
        int priority, CommercialTermComponentBuilder... components) {
        return pricing.mockPrice(CommercialTermPriceBuilder.template().termPriceId(TEMPLATE_PRICE_ID + index)
            .termVersionId(version.getTermVersionId()).currency(currency).pricingProfileCode(profileCode).priorityNo(priority),
            components);
    }

    /** 第 2 号组件：存卡费（TOKEN_STORED 触发），组件 ID 为 TERM_COMPONENT_ID + 500 + index。 */
    static CommercialTermComponentBuilder tokenFeeComponent(int index, String currency) {
        return CommercialTermComponentBuilder.template().termComponentId(CommercialTermComponentBuilder.TERM_COMPONENT_ID + 500
                + index).componentNo(2).componentCode("TOKEN_FEE").componentType(CommercialTermComponentComponentType.FIXED)
            .triggerCode(CommercialCodes.FeeEvent.TOKEN_STORED).rate(null).fixedAmount(new BigDecimal("0.10")).currency(currency);
    }

    /** SALE 百分比组件 MDR。 */
    static CommercialTermComponentBuilder mdr(int index, String rate) {
        return CommercialTermComponentBuilder.template().termComponentId(CommercialTermComponentBuilder.TERM_COMPONENT_ID
            + 400 + index).componentCode("MDR").rate(new BigDecimal(rate));
    }

    /** 基于模板版本创建合同 DRAFT 版本，201。 */
    JsonNode createFromTemplate(JsonNode plan, long templateVersionId) {
        return ok(201, http.post(TERMS + "/plans/" + text(plan, "planId") + "/versions", headers(MAKER),
            templateVersionBody(plan, templateVersionId)));
    }

    /** 基于模板的版本请求体：定价模型与含税口径沿用模板。 */
    static CreateCommercialTermVersionRequestBody templateVersionBody(JsonNode plan, long templateVersionId) {
        CreateCommercialTermVersionRequestBody body = new CreateCommercialTermVersionRequestBody();
        body.setExpectedChangeVersion(text(plan, "changeVersion"));
        body.setSourceTemplateVersionId(Long.toString(templateVersionId));
        return body;
    }

    /** 补齐请求体。 */
    static FillCommercialTermVersionFromTemplateRequestBody fillBody(JsonNode version, long templateVersionId) {
        FillCommercialTermVersionFromTemplateRequestBody body = new FillCommercialTermVersionFromTemplateRequestBody();
        body.setExpectedChangeVersion(text(version, "changeVersion"));
        body.setTemplateVersionId(Long.toString(templateVersionId));
        return body;
    }

    /** 模板方案请求体。 */
    static CreateCommercialTermTemplateRequestBody templateBody(String planCode, String name) {
        CreateCommercialTermTemplateRequestBody body = new CreateCommercialTermTemplateRequestBody();
        body.setPlanCode(planCode);
        body.setTemplateName(name);
        body.setTermType(CommercialCodes.TermType.CLIENT_PRICING);
        body.setEntityNo(ENTITY_NO);
        return body;
    }

    /** 版本在 AE993 的价格全集（按币种、优先级）。 */
    JsonNode versionPrices(JsonNode version) {
        EntityExchangeResult<byte[]> result = http.get("/commercial-term-versions/v1/" + text(version, "versionId")
            + "/prices?entityNo=" + ENTITY_NO + "&termType=" + CommercialCodes.TermType.CLIENT_PRICING, headers());
        return ok(200, result).path("prices");
    }

    /** 读回的价格原样转为保存请求（Web 编辑器的回写方式）：结算四字段取读回的 settlement，组件取读回值。 */
    static CommercialTermPriceRequestBody requestOf(JsonNode price) {
        CommercialTermPriceRequestBody body = new CommercialTermPriceRequestBody();
        body.setEntityNo(text(price, "entityNo"));
        body.setCurrency(text(price, "currency"));
        body.setPricingProfileCode(text(price, "pricingProfileCode"));
        body.setPriorityNo(price.path("priorityNo").asInt());
        JsonNode settlement = price.path("settlement");
        body.setSettlementBusinessDays(settlement.path("settlementBusinessDays").isNumber()
            ? settlement.path("settlementBusinessDays").asInt() : null);
        body.setBusinessCalendarCode(nullable(settlement, "businessCalendarCode"));
        body.setSettlementTimezone(nullable(settlement, "settlementTimezone"));
        body.setCutoffSecondOfDay(settlement.path("cutoffSecondOfDay").isNumber() ? settlement.path("cutoffSecondOfDay").asInt()
            : null);
        body.setSourceTemplatePriceId(nullable(price, "sourceTemplatePriceId"));
        body.setComponents(price.path("components").valueStream().map(component -> {
            CommercialTermComponentRequestBody item = new CommercialTermComponentRequestBody();
            item.setComponentCode(nullable(component, "componentCode"));
            item.setComponentType(nullable(component, "componentType"));
            item.setTriggerCode(nullable(component, "triggerCode"));
            item.setCalculationBase(nullable(component, "calculationBase"));
            item.setRefundBehavior(nullable(component, "refundBehavior"));
            item.setRate(nullable(component, "rate"));
            item.setFixedAmount(nullable(component, "fixedAmount"));
            item.setCurrency(nullable(component, "currency"));
            item.setMinAmount(nullable(component, "minAmount"));
            item.setMaxAmount(nullable(component, "maxAmount"));
            item.setBandBasis(nullable(component, "bandBasis"));
            item.setBandMode(nullable(component, "bandMode"));
            return item;
        }).toList());
        return body;
    }

    /** 比较用的价格内容：去掉每次保存重新分配的 ID。 */
    static String content(JsonNode price) {
        ObjectNode copy = (ObjectNode) price.deepCopy();
        copy.remove("priceId");
        copy.path("components").valueStream().forEach(component ->
            ((ObjectNode) component).remove("componentId"));
        return copy.toString();
    }

    /** 字段为 null 或缺失时为 null，否则为文本。 */
    static String nullable(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asString();
    }

    /** 价格首个组件的破标比较。 */
    static JsonNode benchmark(JsonNode price) {
        return price.path("components").get(0).path("benchmark");
    }
}
