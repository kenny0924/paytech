package com.paytech.commercial.test.base.term;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.BandBasis;
import com.paytech.commercial.api.enums.CalculationBase;
import com.paytech.commercial.api.enums.FeeTaxTreatment;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.PricingModel;
import com.paytech.commercial.api.enums.RefundBehavior;
import com.paytech.commercial.api.term.request.CommercialTermBandRequestBody;
import com.paytech.commercial.api.term.request.CommercialTermComponentRequestBody;
import com.paytech.commercial.api.term.request.CommercialTermPriceRequestBody;
import com.paytech.commercial.api.term.request.CommercialTermVersionCommandRequestBody;
import com.paytech.commercial.api.term.request.CreateCommercialTermPlanRequestBody;
import com.paytech.commercial.api.term.request.CreateCommercialTermVersionRequestBody;
import com.paytech.commercial.api.term.request.ReplaceCommercialTermPricesRequestBody;
import com.paytech.commercial.api.term.request.ScheduleCommercialTermVersionRequestBody;
import com.paytech.commercial.api.enums.CommercialTermPlanStatus;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.CommercialTermComponentComponentType;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.commercial.test.base.CommercialMockTestBase;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.AgreementCapabilityBuilder;
import com.paytech.commercial.test.builder.CommercialTermComponentBuilder;
import com.paytech.commercial.test.builder.CommercialTermPlanBuilder;
import com.paytech.commercial.test.builder.CommercialTermPriceBuilder;
import com.paytech.commercial.test.builder.CommercialTermVersionBuilder;
import com.paytech.commercial.test.builder.PricingProfileBuilder;
import com.paytech.commercial.test.builder.PricingProfileCondBuilder;
import com.paytech.commercial.test.dal.CommercialTestMapper;
import com.paytech.commercial.test.mocker.local.agreement.AgreementSubscriptionMocker;
import com.paytech.commercial.test.mocker.local.agreement.CommercialGraphMocker;
import com.paytech.commercial.test.mocker.local.catalog.ProductCatalogMocker;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;
import com.paytech.commercial.test.mocker.local.term.TermPricingMocker;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 商业条款写入、价格命中与费率卡入口测试的公共部分：HTTP 路径、请求模板、标准前置数据与经正式 API 推进版本的步骤
 * <li>标准前置数据：种子产品目录、ACTIVE CLIENT 合同（AE993）、三个 ACTIVE 卡画像（通用、VISA、VISA + CREDIT）与 CARD_VISA 开通项
 * （USD）。不含方案与版本，它们由被测 API 创建。</li>
 */
public abstract class AbstractCommercialTermApiTest extends CommercialMockTestBase {
    /** 条款写入路径前缀。 */
    protected static final String TERMS = "/commercial-terms/v1";

    /** Maker 账号。 */
    protected static final long MAKER = 930000000000951L;

    /** Checker 账号。 */
    protected static final long CHECKER = 930000000000952L;

    /** 合同 OE。 */
    protected static final String ENTITY_NO = AgreementBuilder.ENTITY_NO;

    /** 通用卡画像（无条件）。 */
    protected static final String CARD = PricingProfileBuilder.PRICING_PROFILE_CODE;

    /** VISA 画像（CARD_BRAND=VISA）。 */
    protected static final String CARD_VISA = "WRITE_CARD_VISA";

    /** VISA 信用卡画像（CARD_BRAND=VISA、FUNDING_TYPE=CREDIT）。 */
    protected static final String CARD_VISA_CREDIT = "WRITE_CARD_VISA_CREDIT";

    /** 固定测试时点。 */
    protected static final Instant NOW = CheckoutFixture.NOW;

    private static final String REQUEST_ID = "Commercial.Term_01";

    @Autowired
    protected HttpTestInvoker http;

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected CommercialGraphMocker graph;

    @Autowired
    protected TermPricingMocker pricing;

    @Autowired
    protected ProductCatalogMocker catalog;

    @Autowired
    protected CommercialTestMapper testMapper;

    @Autowired
    protected AgreementSubscriptionMocker subscriptions;

    /**
     * 标准前置数据：目录、ACTIVE 合同、三个 ACTIVE 卡画像与 CARD_VISA（USD）开通项。
     *
     * @return 已插入的合同
     */
    protected Agreement mockBaseline() {
        catalog.mockSeedCatalog();
        Agreement agreement = graph.mockAgreement(AgreementBuilder.template().status(AgreementStatus.ACTIVE)).agreement();
        mockCardProfiles(PricingProfileStatus.ACTIVE);
        subscriptions.mockCapability(AgreementCapabilityBuilder.template().agreementId(AgreementBuilder.AGREEMENT_ID)
            .entityNo(ENTITY_NO)).currencies("USD");
        return agreement;
    }

    /** 三个卡画像：通用、VISA、VISA + CREDIT。 */
    protected void mockCardProfiles(PricingProfileStatus status) {
        pricing.mockProfile(PricingProfileBuilder.template().status(status));
        pricing.mockProfile(PricingProfileBuilder.template().pricingProfileId(PricingProfileBuilder.PRICING_PROFILE_ID + 1)
            .pricingProfileCode(CARD_VISA).status(status), visa());
        pricing.mockProfile(PricingProfileBuilder.template().pricingProfileId(PricingProfileBuilder.PRICING_PROFILE_ID + 2)
            .pricingProfileCode(CARD_VISA_CREDIT).status(status), visa(),
            PricingProfileCondBuilder.template().dimensionCode(PricingDimension.FUNDING_TYPE).conditionValue("CREDIT"));
    }

    /** CARD_BRAND=VISA 条件。 */
    protected static PricingProfileCondBuilder visa() {
        return PricingProfileCondBuilder.template().dimensionCode(PricingDimension.CARD_BRAND)
            .conditionValue(CommercialCodes.CardBrand.VISA);
    }

    /** 预置生效版本的起点。 */
    protected static final Instant V1_FROM = Instant.parse("2026-09-01T00:00:00Z");

    /**
     * 预置一个已生效的 CLIENT_PRICING 方案（ACTIVE，BCR 根修订 2）与第 1 版（ACTIVE，自 V1_FROM 起），不含价格。
     *
     * @return 第 1 版
     */
    protected CommercialTermVersion mockActiveVersion() {
        return pricing.mockPlan(CommercialTermPlanBuilder.template().status(CommercialTermPlanStatus.ACTIVE), 2,
            CommercialTermVersionBuilder.template().status(CommercialTermVersionStatus.ACTIVE).activeSlot(1)
                .effectiveFrom(V1_FROM)).getFirst();
    }

    /**
     * 在版本下预置一条 AE993 价格与一个 SALE 百分比组件；ID 为默认 ID + index。
     *
     * @param version 版本
     * @param index 序号（同一用例内唯一）
     * @param currency 币种
     * @param profileCode 画像
     * @param priority 优先级
     * @param rate 费率
     * @return 已插入的价格
     */
    protected CommercialTermPrice mockPrice(CommercialTermVersion version, int index, String currency, String profileCode,
        int priority, String rate) {
        return pricing.mockPrice(CommercialTermPriceBuilder.template().termPriceId(CommercialTermPriceBuilder.TERM_PRICE_ID
                + index).termVersionId(version.getTermVersionId()).currency(currency).pricingProfileCode(profileCode)
                .priorityNo(priority),
            CommercialTermComponentBuilder.template().termComponentId(CommercialTermComponentBuilder.TERM_COMPONENT_ID
                + index).rate(new java.math.BigDecimal(rate)));
    }

    // ==================== 请求模板 ====================

    /** 默认 Client 条款方案请求。 */
    protected CreateCommercialTermPlanRequestBody planRequest() {
        CreateCommercialTermPlanRequestBody body = new CreateCommercialTermPlanRequestBody();
        body.setPlanCode("PLAN-C-1");
        body.setTermType(CommercialCodes.TermType.CLIENT_PRICING);
        body.setAgreementId(Long.toString(AgreementBuilder.AGREEMENT_ID));
        body.setClientId(Long.toString(AgreementBuilder.CLIENT_ID));
        body.setOperatorAccountId(Long.toString(MAKER));
        return body;
    }

    /** 一条 AE993 价格，Client 结算四字段取本期默认值。 */
    protected static CommercialTermPriceRequestBody price(String currency, String profileCode, int priority,
        CommercialTermComponentRequestBody... components) {
        CommercialTermPriceRequestBody body = new CommercialTermPriceRequestBody();
        body.setEntityNo(ENTITY_NO);
        body.setCurrency(currency);
        body.setPricingProfileCode(profileCode);
        body.setPriorityNo(priority);
        body.setSettlementBusinessDays(2);
        body.setBusinessCalendarCode(CommercialCodes.AE_BUSINESS_DAYS);
        body.setSettlementTimezone("UTC");
        body.setCutoffSecondOfDay(86399);
        body.setComponents(List.of(components));
        return body;
    }

    /** SALE 百分比组件。 */
    protected static CommercialTermComponentRequestBody percentage(String code, String rate) {
        CommercialTermComponentRequestBody body = component(code, CommercialTermComponentComponentType.PERCENTAGE);
        body.setRate(rate);
        return body;
    }

    /** SALE 固定金额组件。 */
    protected static CommercialTermComponentRequestBody fixed(String code, String amount, String currency) {
        CommercialTermComponentRequestBody body = component(code, CommercialTermComponentComponentType.FIXED);
        body.setFixedAmount(amount);
        body.setCurrency(currency);
        return body;
    }

    /** SALE 阶梯组件（按单笔金额）。 */
    protected static CommercialTermComponentRequestBody tiered(String code, String currency, BandBasis basis, String mode,
        CommercialTermBandRequestBody... bands) {
        CommercialTermComponentRequestBody body = component(code, CommercialTermComponentComponentType.TIERED);
        body.setCurrency(currency);
        body.setBandBasis(basis.getCode());
        body.setBandMode(mode);
        body.setBands(new ArrayList<>(Arrays.asList(bands)));
        return body;
    }

    /** 一档费率。 */
    protected static CommercialTermBandRequestBody band(String lower, String upper, String rate) {
        CommercialTermBandRequestBody body = new CommercialTermBandRequestBody();
        body.setLowerBound(lower);
        body.setUpperBound(upper);
        body.setRate(rate);
        return body;
    }

    /** 组件公共字段：SALE、GROSS_AMOUNT、RETURN_PRORATA。 */
    static CommercialTermComponentRequestBody component(String code, CommercialTermComponentComponentType type) {
        CommercialTermComponentRequestBody body = new CommercialTermComponentRequestBody();
        body.setComponentCode(code);
        body.setComponentType(type.getCode());
        body.setTriggerCode(CommercialCodes.FeeEvent.SALE);
        body.setCalculationBase(CalculationBase.GROSS_AMOUNT.getCode());
        body.setRefundBehavior(RefundBehavior.RETURN_PRORATA.getCode());
        return body;
    }

    // ==================== 经正式 API 推进 ====================

    /** 创建默认方案，201。 */
    protected JsonNode createPlan() {
        return ok(201, http.post(TERMS + "/plans", headers(), planRequest()));
    }

    /** 创建 BLENDED / EXCLUSIVE 的 DRAFT 版本，201。 */
    protected JsonNode createVersion(JsonNode plan) {
        return ok(201, createVersionResult(text(plan, "planId"), text(plan, "changeVersion"), null));
    }

    /** 创建版本的原始结果，可指定复制来源。 */
    protected EntityExchangeResult<byte[]> createVersionResult(String planId, String changeVersion, String copyFrom) {
        CreateCommercialTermVersionRequestBody body = new CreateCommercialTermVersionRequestBody();
        body.setExpectedChangeVersion(changeVersion);
        body.setOperatorAccountId(Long.toString(MAKER));
        body.setCopyFromVersionId(copyFrom);
        body.setPricingModel(PricingModel.BLENDED.getCode());
        body.setFeeTaxTreatment(FeeTaxTreatment.EXCLUSIVE.getCode());
        return http.post(TERMS + "/plans/" + planId + "/versions", headers(), body);
    }

    /** 整体替换价格集合的原始结果。 */
    protected EntityExchangeResult<byte[]> replacePricesResult(JsonNode version, CommercialTermPriceRequestBody... prices) {
        ReplaceCommercialTermPricesRequestBody body = new ReplaceCommercialTermPricesRequestBody();
        body.setExpectedChangeVersion(text(version, "changeVersion"));
        body.setOperatorAccountId(Long.toString(MAKER));
        body.setPrices(List.of(prices));
        return http.exchange(HttpMethod.PUT, TERMS + "/versions/" + text(version, "versionId") + "/prices", headers(), body);
    }

    /** 整体替换价格集合，200。 */
    protected JsonNode replacePrices(JsonNode version, CommercialTermPriceRequestBody... prices) {
        return ok(200, replacePricesResult(version, prices));
    }

    /** 版本命令（submit / approve / reject / activate）的原始结果。 */
    protected EntityExchangeResult<byte[]> commandResult(JsonNode version, String action, long operator) {
        CommercialTermVersionCommandRequestBody body = new CommercialTermVersionCommandRequestBody();
        body.setExpectedChangeVersion(text(version, "changeVersion"));
        body.setOperatorAccountId(Long.toString(operator));
        return http.post(TERMS + "/versions/" + text(version, "versionId") + "/" + action, headers(), body);
    }

    /** 版本命令，200。 */
    protected JsonNode command(JsonNode version, String action, long operator) {
        return ok(200, commandResult(version, action, operator));
    }

    /** 排期的原始结果。 */
    protected EntityExchangeResult<byte[]> scheduleResult(JsonNode version, Instant effectiveFrom) {
        ScheduleCommercialTermVersionRequestBody body = new ScheduleCommercialTermVersionRequestBody();
        body.setExpectedChangeVersion(text(version, "changeVersion"));
        body.setOperatorAccountId(Long.toString(CHECKER));
        body.setEffectiveFrom(effectiveFrom.toString());
        return http.post(TERMS + "/versions/" + text(version, "versionId") + "/schedule", headers(), body);
    }

    /** 提交并批准：DRAFT → APPROVED。 */
    protected JsonNode approved(JsonNode version) {
        return command(command(version, "submit", MAKER), "approve", CHECKER);
    }

    /** 方案、版本、价格、提交、批准、排期（NOW）、激活，返回 ACTIVE 版本。 */
    protected JsonNode activeVersion(CommercialTermPriceRequestBody... prices) {
        JsonNode version = replacePrices(createVersion(createPlan()), prices);
        JsonNode scheduled = ok(200, scheduleResult(approved(version), NOW));
        return command(scheduled, "activate", CHECKER);
    }

    // ==================== 断言 ====================

    /** 断言状态并解析 JSON。 */
    protected JsonNode ok(int status, EntityExchangeResult<byte[]> result) {
        String body = result.getResponseBody() == null ? "" : new String(result.getResponseBody());
        assertEquals(status, result.getStatus().value(), body);
        return json.readTree(body);
    }

    /** 断言 Problem 的状态、code 与 detail 片段。 */
    protected void assertProblem(EntityExchangeResult<byte[]> result, int status, String code, String detail) {
        JsonNode problem = ok(status, result);
        assertEquals(code, problem.path("code").asString(), problem.toString());
        assertTrue(problem.path("detail").asString().contains(detail), problem.toString());
    }

    /** 文本字段。 */
    protected static String text(JsonNode node, String field) {
        return node.path(field).asString();
    }

    /** 私网 JSON 调用头。 */
    protected static HttpHeaders headers() {
        return TestRequestHeaders.internal(REQUEST_ID);
    }
}
