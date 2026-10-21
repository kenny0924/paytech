package com.paytech.commercial.auto.ext.http.catalog;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.commercial.api.catalog.ProductCatalogApi;
import com.paytech.commercial.api.catalog.request.CapabilityDimensionEntry;
import com.paytech.commercial.api.catalog.request.CreateProductCapabilityRequestBody;
import com.paytech.commercial.api.catalog.request.ProductCatalogTransitionRequestBody;
import com.paytech.commercial.api.catalog.request.UpdateFeeEventCapabilityRequestBody;
import com.paytech.commercial.api.catalog.request.UpdateProductCapabilityRequestBody;
import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.domain.ProductCatalogContext;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.test.base.write.AbstractCommercialWriteApiTest;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.AgreementCapabilityBodyBuilder;
import com.paytech.commercial.test.builder.AgreementCapabilityBuilder;
import com.paytech.commercial.test.builder.AgreementProductBuilder;
import com.paytech.commercial.test.mocker.local.agreement.AgreementSubscriptionMocker;
import com.paytech.commercial.test.mocker.local.catalog.ProductCatalogFixture;

import tools.jackson.databind.JsonNode;

/**
 * 产品目录运营维护（开通与定价模型调整 technique.md §4.1，AC-01 / AC-02）：新增功能与支付方式开通项、可配维度、改名与改归属、停用 / 启用、
 * 计费事件挂接、维度清单；代码重复 / 格式错误 / 种类与支付方式不符 / 不可用于开通的维度被拒，目录行版本 CAS，目录 BCR Root 记录快照。
 *
 * @see ProductCatalogApi
 */
class ProductCatalogWriteHttpTest extends AbstractCommercialWriteApiTest {
    private static final String CAPABILITIES = "/products/v1/capabilities";

    /** 种子目录 TOKEN_STORED（CLIENT_PRICING）计费事件。 */
    private static final long TOKEN_STORED_EVENT_ID = 9100000000000003014L;

    @Autowired
    private AgreementSubscriptionMocker subscriptions;

    private ProductCatalogFixture seed;

    /** 每例插入种子目录（迁移后的 CARD / APPLE_PAY / GOOGLE_PAY / TOKEN_STORAGE 收单目录）。 */
    @BeforeEach
    void setUpClassMocks() {
        seed = catalog.mockSeedCatalog();
    }

    /** 迁移后收单目录与 CARD 可配维度、TOKEN_STORED 挂接功能开通项（AC-02），维度清单只读接口（S2）。 */
    @Test
    void test_seedCatalogAndDimensions() {
        JsonNode acquiring = get(CAPABILITIES + "?productCode=ACQUIRING").assertOk().node("capabilities");
        JsonNode card = acquiring.valueStream()
            .filter(node -> CommercialCodes.Capability.CARD.equals(node.path("capabilityCode").asString())).findFirst()
            .orElseThrow();
        JsonNode tokenStored = get("/products/v1/fee-events?termType=CLIENT_PRICING").assertOk().node("feeEvents")
            .valueStream().filter(node -> CommercialCodes.FeeEvent.TOKEN_STORED.equals(node.path("feeEventCode").asString()))
            .findFirst().orElseThrow();
        JsonNode dimensions = get("/products/v1/dimensions").assertOk().node("dimensions");
        JsonNode threeDs = dimensions.valueStream()
            .filter(node -> PricingDimension.THREE_DS_RESULT.getCode().equals(node.path("dimensionCode").asString()))
            .findFirst().orElseThrow();

        assertAll(() -> assertEquals(List.of(CommercialCodes.Capability.APPLE_PAY, CommercialCodes.Capability.GOOGLE_PAY,
                CommercialCodes.Capability.CARD, CommercialCodes.Capability.TOKEN_STORAGE),
                acquiring.valueStream().map(node -> node.path("capabilityCode").asString()).toList()),
            () -> assertEquals(List.of("CARD_BRAND", "CARD_LEVEL", "CONSUMER_COUNTRY", "CURRENCY", "FUNDING_TYPE",
                "ISSUER_COUNTRY", "ISSUER_REGION"), card.path("dimensions").valueStream()
                .map(node -> node.path("dimensionCode").asString()).toList()),
            () -> assertTrue(card.path("dimensions").get(3).path("required").asBoolean()),
            () -> assertEquals(CapabilityKind.FEATURE.getCode(), tokenStored.path("capabilityKind").asString()),
            () -> assertEquals(CommercialCodes.Capability.TOKEN_STORAGE, tokenStored.path("capabilityCode").asString()),
            () -> assertEquals(PricingDimension.values().length, dimensions.size()),
            () -> assertEquals("THREE_DS", threeDs.path("factSource").asString()),
            () -> assertEquals("AUTHENTICATION", threeDs.path("evaluationPoint").asString()),
            () -> assertEquals(false, threeDs.path("acceptanceAllowed").asBoolean()),
            () -> assertEquals(true, threeDs.path("pricingAllowed").asBoolean()));
    }

    /** 新增功能开通项 RECURRING 与支付方式开通项 STC_PAY：201、版本 1、可配维度读回；首次写入创建目录 Root，第二次推进 Root。 */
    @Test
    void test_create_normalFlow() {
        JsonNode recurring = post(CAPABILITIES, createBody("RECURRING", CapabilityKind.FEATURE, null)).assertCreated()
            .node("dimensions");
        long stcPayId = post(CAPABILITIES, createBody("STC_PAY", CapabilityKind.PAYMENT_METHOD, "STC_PAY",
                dimension(PricingDimension.CURRENCY, true), dimension(PricingDimension.CONSUMER_COUNTRY, false)))
            .assertCreated().assertText("status", ProductStatus.ACTIVE.getCode())
            .assertText("paymentMethodCode", "STC_PAY").id("productCapabilityId");

        JsonNode stcPay = row("STC_PAY");
        assertAll(() -> assertEquals(0, recurring.size()),
            () -> assertEquals(Long.toString(stcPayId), stcPay.path("productCapabilityId").asString()),
            () -> assertEquals(1, stcPay.path("changeVersion").asLong()),
            () -> assertTrue(stcPay.path("dimensionCode").isNull()),
            () -> assertEquals(List.of("CONSUMER_COUNTRY", "CURRENCY"), stcPay.path("dimensions").valueStream()
                .map(node -> node.path("dimensionCode").asString()).toList()),
            () -> assertEquals(true, stcPay.path("dimensions").get(1).path("required").asBoolean()));
        history.record(BusinessChangeRecordRecordType.PRODUCT_CATALOG.getCode(), ProductCatalogContext.ROOT_ID)
            .assertVersions("1:CREATE", "2:UPDATE").assertLastReason(BusinessChangeReason.CATALOG_CAPABILITY_CREATED.getCode())
            .assertIntegrity();
    }

    /** 代码重复 409；代码格式错误 422；FEATURE 带支付方式、PAYMENT_METHOD 缺支付方式与维度重复 400；不可用于开通的维度、产品未启用为业务失败。 */
    @Test
    void test_create_invalidParameter() {
        post(CAPABILITIES, createBody(CommercialCodes.Capability.CARD, CapabilityKind.PAYMENT_METHOD, "CARD")).assertConflict();
        post(CAPABILITIES, createBody("stc-pay", CapabilityKind.PAYMENT_METHOD, "STC_PAY")).assertValidationError("capabilityCode");
        post(CAPABILITIES, createBody("RECURRING", CapabilityKind.FEATURE, "CARD")).assertInvalidRequest();
        post(CAPABILITIES, createBody("STC_PAY", CapabilityKind.PAYMENT_METHOD, null)).assertInvalidRequest();
        post(CAPABILITIES, createBody("STC_PAY", CapabilityKind.PAYMENT_METHOD, "STC_PAY",
            dimension(PricingDimension.CURRENCY, true), dimension(PricingDimension.CURRENCY, false))).assertInvalidRequest();
        post(CAPABILITIES, createBody("STC_PAY", CapabilityKind.PAYMENT_METHOD, "STC_PAY",
            dimension(PricingDimension.THREE_DS_RESULT, false))).assertBusinessCheckFailed("维度不可用于开通接受条件");
        testMapper.updateProduct(ProductCode.ACQUIRING.getCode(), "BOTH", ProductStatus.RETIRED.getCode());
        post(CAPABILITIES, createBody("STC_PAY", CapabilityKind.PAYMENT_METHOD, "STC_PAY"))
            .assertBusinessCheckFailed("归属产品不存在或未启用");

        assertEquals(seed.capabilities().size(), get(CAPABILITIES).assertOk().node("capabilities").size());
    }

    /** 改名、改归属产品与整体替换可配维度：版本 1 → 2；过期版本 409，不存在 404。 */
    @Test
    void test_update_normalFlowAndConflicts() {
        long id = id(CommercialCodes.Capability.TOKEN_STORAGE);
        UpdateProductCapabilityRequestBody body = updateBody(1, "存卡（Token）", ProductCode.ACQUIRING.getCode(),
            dimension(PricingDimension.CURRENCY, false));

        put(CAPABILITIES + "/" + id, body).assertOk().assertText("capabilityName", "存卡（Token）");

        JsonNode updated = row(CommercialCodes.Capability.TOKEN_STORAGE);
        assertAll(() -> assertEquals(2, updated.path("changeVersion").asLong()),
            () -> assertEquals(List.of("CURRENCY"), updated.path("dimensions").valueStream()
                .map(node -> node.path("dimensionCode").asString()).toList()));
        put(CAPABILITIES + "/" + id, body).assertConflict();
        put(CAPABILITIES + "/930000000000999", updateBody(1, "X", "ACQUIRING")).assertNotFound();
        history.record(BusinessChangeRecordRecordType.PRODUCT_CATALOG.getCode(), ProductCatalogContext.ROOT_ID)
            .assertLastReason(BusinessChangeReason.CATALOG_CAPABILITY_UPDATED.getCode()).assertIntegrity();
    }

    /** 停用后新合同不能开通、已开通的仍可改写；同一推进重放返回当前结果；启用后可再开通。 */
    @Test
    void test_retireAndActivate() {
        agreements.mock(AgreementBuilder.template().status(AgreementStatus.ACTIVE), 5)
            .auditedChild(BusinessChangeRecordRecordType.AGREEMENT_CAPABILITY, AgreementCapabilityBuilder.AGREEMENT_CAPABILITY_ID);
        subscriptions.mockProduct(AgreementProductBuilder.template().agreementId(AgreementBuilder.AGREEMENT_ID));
        subscriptions.mockCapability(AgreementCapabilityBuilder.template().agreementId(AgreementBuilder.AGREEMENT_ID)
            .entityNo(AgreementBuilder.ENTITY_NO)).currencies("USD", "AED");
        long cardId = id(CommercialCodes.Capability.CARD);
        long applePayId = id(CommercialCodes.Capability.APPLE_PAY);
        String agreementPath = "/agreements/v1/" + AgreementBuilder.AGREEMENT_ID + "/capabilities";

        post(CAPABILITIES + "/" + cardId + "/retire", transition(1)).assertOk()
            .assertText("status", ProductStatus.RETIRED.getCode());
        post(CAPABILITIES + "/" + cardId + "/retire", transition(1)).assertOk().assertText("status",
            ProductStatus.RETIRED.getCode());
        post(CAPABILITIES + "/" + applePayId + "/retire", transition(1)).assertOk();

        post(agreementPath, AgreementCapabilityBodyBuilder.template().capabilityCode(CommercialCodes.Capability.APPLE_PAY)
            .buildAdd(5)).assertBusinessCheckFailed("可开通项不存在或未启用");
        put(agreementPath + "/" + AgreementCapabilityBuilder.AGREEMENT_CAPABILITY_ID, AgreementCapabilityBodyBuilder
            .template().conditions(AgreementCapabilityBodyBuilder.condition(PricingDimension.CURRENCY, "USD"))
            .buildUpdate(5)).assertOk().assertVersion(6);
        post(CAPABILITIES + "/" + applePayId + "/activate", transition(1)).assertConflict();
        post(CAPABILITIES + "/" + applePayId + "/activate", transition(2)).assertOk()
            .assertText("status", ProductStatus.ACTIVE.getCode());
        post(agreementPath, AgreementCapabilityBodyBuilder.template().capabilityCode(CommercialCodes.Capability.APPLE_PAY)
            .buildAdd(6)).assertOk();

        assertEquals(3, row(CommercialCodes.Capability.APPLE_PAY).path("changeVersion").asLong());
    }

    /** 计费事件挂接：FEATURE 须指向同产品功能开通项，其他种类不得带代码；改为无需开通后版本加一，过期版本 409。 */
    @Test
    void test_feeEventCapability() {
        String path = "/products/v1/fee-events/" + TOKEN_STORED_EVENT_ID + "/capability";

        put(path, feeEventBody(1, CapabilityKind.FEATURE.getCode(), CommercialCodes.Capability.CARD))
            .assertBusinessCheckFailed("不是 FEATURE");
        put(path, feeEventBody(1, CapabilityKind.PAYMENT_METHOD.getCode(), CommercialCodes.Capability.CARD))
            .assertInvalidRequest();
        put(path, feeEventBody(1, null, null)).assertOk().assertText("capabilityKind", null);
        put(path, feeEventBody(1, CapabilityKind.FEATURE.getCode(), CommercialCodes.Capability.TOKEN_STORAGE)).assertConflict();
        put(path, feeEventBody(2, CapabilityKind.FEATURE.getCode(), CommercialCodes.Capability.TOKEN_STORAGE)).assertOk()
            .assertText("capabilityCode", CommercialCodes.Capability.TOKEN_STORAGE);
        put("/products/v1/fee-events/930000000000999/capability", feeEventBody(1, null, null)).assertNotFound();

        history.record(BusinessChangeRecordRecordType.PRODUCT_CATALOG.getCode(), ProductCatalogContext.ROOT_ID)
            .assertVersions("1:CREATE", "2:UPDATE").assertLastReason(BusinessChangeReason.CATALOG_FEE_EVENT_UPDATED.getCode())
            .assertIntegrity();
    }

    /** 新增请求体。 */
    static CreateProductCapabilityRequestBody createBody(String code, CapabilityKind kind, String paymentMethodCode,
        CapabilityDimensionEntry... dimensions) {
        CreateProductCapabilityRequestBody body = new CreateProductCapabilityRequestBody();
        body.setProductCode(ProductCode.ACQUIRING.getCode());
        body.setCapabilityCode(code);
        body.setCapabilityName(code + " 名称");
        body.setCapabilityKind(kind.getCode());
        body.setPaymentMethodCode(paymentMethodCode);
        body.setDimensions(List.of(dimensions));
        return body;
    }

    /** 改写请求体。 */
    static UpdateProductCapabilityRequestBody updateBody(long expectedVersion, String name, String productCode,
        CapabilityDimensionEntry... dimensions) {
        UpdateProductCapabilityRequestBody body = new UpdateProductCapabilityRequestBody();
        body.setExpectedChangeVersion(expectedVersion);
        body.setCapabilityName(name);
        body.setProductCode(productCode);
        body.setDimensions(List.of(dimensions));
        return body;
    }

    /** 一个可配维度。 */
    static CapabilityDimensionEntry dimension(PricingDimension dimension, boolean required) {
        CapabilityDimensionEntry entry = new CapabilityDimensionEntry();
        entry.setDimensionCode(dimension.getCode());
        entry.setRequired(required);
        return entry;
    }

    /** 停用 / 启用请求体。 */
    static ProductCatalogTransitionRequestBody transition(long expectedVersion) {
        ProductCatalogTransitionRequestBody body = new ProductCatalogTransitionRequestBody();
        body.setExpectedChangeVersion(expectedVersion);
        return body;
    }

    /** 计费事件挂接请求体。 */
    static UpdateFeeEventCapabilityRequestBody feeEventBody(long expectedVersion, String kind, String code) {
        UpdateFeeEventCapabilityRequestBody body = new UpdateFeeEventCapabilityRequestBody();
        body.setExpectedChangeVersion(expectedVersion);
        body.setCapabilityKind(kind);
        body.setCapabilityCode(code);
        return body;
    }

    /** 目录列表中某可开通项一行。 */
    JsonNode row(String capabilityCode) {
        return get(CAPABILITIES).assertOk().node("capabilities").valueStream()
            .filter(node -> capabilityCode.equals(node.path("capabilityCode").asString())).findFirst().orElseThrow();
    }

    /** 某可开通项的 ID。 */
    long id(String capabilityCode) {
        return Long.parseLong(row(capabilityCode).path("productCapabilityId").asString());
    }
}
