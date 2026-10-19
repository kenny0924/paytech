package com.paytech.commercial.auto.ext.http.agreement;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.commercial.api.agreement.AgreementApi;
import com.paytech.commercial.api.agreement.request.AddAgreementCapabilityRequestBody;
import com.paytech.commercial.api.agreement.request.ReplaceAgreementProductsRequestBody;
import com.paytech.commercial.api.agreement.request.UpdateAgreementCapabilityRequestBody;
import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.AgreementCapabilityStatus;
import com.paytech.commercial.api.enums.CapabilityLimitCode;
import com.paytech.commercial.api.enums.ProductBusinessModeScope;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.test.base.write.AbstractCommercialWriteApiTest;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.AgreementCapabilityBodyBuilder;
import com.paytech.commercial.test.builder.AgreementCapabilityBuilder;
import com.paytech.commercial.test.builder.AgreementProductBuilder;
import com.paytech.commercial.test.checker.write.BusinessChangeChecker;
import com.paytech.commercial.test.mocker.local.agreement.AgreementSubscriptionMocker;
import com.paytech.commercial.test.mocker.local.agreement.AgreementWriteMocker;

/**
 * 订阅产品与开通项（technique.md §2.2、database.md §5）：集合替换与等价不变、基座 / 未启用 / 模式不符产品、移除仍有开通项的产品、
 * 开通项的 OE 覆盖、目录资格、条款与约束规范、唯一身份冲突、整体改写与不变重放、归属错误、版本冲突，以及 Child 快照与 Root 关联
 *
 * @see AgreementApi#replaceProducts(String, String, ReplaceAgreementProductsRequestBody)
 * @see AgreementApi#addCapability(String, String, AddAgreementCapabilityRequestBody)
 * @see AgreementApi#updateCapability(String, String, String, UpdateAgreementCapabilityRequestBody)
 */
class AgreementSubscriptionHttpTest extends AbstractCommercialWriteApiTest {
    private static final long AGREEMENT_ID = AgreementBuilder.AGREEMENT_ID;

    private static final String BASE = "/agreements/v1/" + AGREEMENT_ID;

    /** 本类存量订阅行 ID。 */
    private static final long PRODUCT_ROW_ID = 930000000000061L;

    /** 本类存量开通项 ID。 */
    private static final long CAPABILITY_ID = 930000000000071L;

    @Autowired
    private AgreementSubscriptionMocker subscriptions;

    private AgreementWriteMocker.Mocker agreement;

    /** 每例插入完整产品目录与 ACTIVE 的 MOR CLIENT 合同（Root 版本 5）。 */
    @BeforeEach
    void setUpClassMocks() {
        catalog.mockSeedCatalog();
        agreement = agreements.mock(AgreementBuilder.template().status(AgreementStatus.ACTIVE), 5);
    }

    /** 订阅两个产品后收缩为一个：版本 6 → 7，新增行 CREATE、移除行先留 DELETE 快照再删除，Child 与 Root 共享 triggerId。 */
    @Test
    void test_products_normalFlow_childHistory() {
        put(BASE + "/products", products(5, ProductCode.ACQUIRING, ProductCode.PAYOUT)).assertOk().assertVersion(6);
        assertEquals(List.of(ProductCode.ACQUIRING.getCode(), ProductCode.PAYOUT.getCode()),
            testMapper.agreementProductCodes(AGREEMENT_ID));
        BusinessChangeChecker.Record root = history.record(BusinessChangeRecordRecordType.AGREEMENT.getCode(), AGREEMENT_ID)
            .assertVersions("6:UPDATE").assertLastReason(BusinessChangeReason.AGREEMENT_PRODUCTS_REPLACED.getCode());
        String triggerAt6 = root.lastTriggerId();
        long payoutRowId = productRowId(ProductCode.PAYOUT);

        put(BASE + "/products", products(6, ProductCode.ACQUIRING)).assertOk().assertVersion(7);

        BusinessChangeChecker.Record payout = history.record(BusinessChangeRecordRecordType.AGREEMENT_PRODUCT.getCode(),
            payoutRowId);
        assertAll(() -> assertEquals(List.of(ProductCode.ACQUIRING.getCode()), testMapper.agreementProductCodes(AGREEMENT_ID)),
            () -> payout.assertParent(root).assertCurrentVersion(7).assertVersions("6:CREATE", "7:DELETE")
                .assertLastData("productCode", ProductCode.PAYOUT.getCode()).assertIntegrity(),
            () -> history.record(BusinessChangeRecordRecordType.AGREEMENT.getCode(), AGREEMENT_ID)
                .assertVersions("6:UPDATE", "7:UPDATE").assertIntegrity(),
            () -> assertEquals(triggerAt6, history.record(BusinessChangeRecordRecordType.AGREEMENT_PRODUCT.getCode(),
                productRowId(ProductCode.ACQUIRING)).lastTriggerId()));
    }

    /** 与当前订阅等价的集合不写入，即使期望版本已过期也返回当前版本。 */
    @Test
    void test_products_equivalentSetNoChange() {
        put(BASE + "/products", products(5, ProductCode.ACQUIRING)).assertOk().assertVersion(6);

        put(BASE + "/products", products(5, ProductCode.ACQUIRING)).assertOk().assertVersion(6);

        history.record(BusinessChangeRecordRecordType.AGREEMENT.getCode(), AGREEMENT_ID).assertVersions("6:UPDATE");
    }

    /** 基座产品、未启用产品、模式不适用产品、重复代码与过期版本拒绝，订阅不变。 */
    @Test
    void test_products_businessRuleViolation() {
        put(BASE + "/products", products(5, ProductCode.PLATFORM)).assertBusinessCheckFailed("基座产品无需订阅");
        testMapper.updateProduct(ProductCode.FX.getCode(), ProductBusinessModeScope.PSP_ONLY.getCode(),
            ProductStatus.ACTIVE.getCode());
        put(BASE + "/products", products(5, ProductCode.FX)).assertBusinessCheckFailed("产品不适用于合同业务模式");
        testMapper.updateProduct(ProductCode.DISPUTE.getCode(), ProductBusinessModeScope.BOTH.getCode(),
            ProductStatus.RETIRED.getCode());
        put(BASE + "/products", products(5, ProductCode.DISPUTE)).assertBusinessCheckFailed("产品不存在或未启用");
        put(BASE + "/products", products(5, ProductCode.ACQUIRING, ProductCode.ACQUIRING)).assertInvalidRequest();
        put(BASE + "/products", products(4, ProductCode.ACQUIRING)).assertConflict();

        assertEquals(List.of(), testMapper.agreementProductCodes(AGREEMENT_ID));
    }

    /** 仍有开通项的产品不能移除订阅。 */
    @Test
    void test_products_removeWithCapabilitiesRejected() {
        subscriptions.mockProduct(AgreementProductBuilder.template().agreementProductId(PRODUCT_ROW_ID)
            .agreementId(AGREEMENT_ID));
        subscriptions.mockCapability(AgreementCapabilityBuilder.template().agreementCapabilityId(CAPABILITY_ID)
            .agreementId(AGREEMENT_ID).entityNo(AgreementBuilder.ENTITY_NO));

        put(BASE + "/products", products(5)).assertBusinessCheckFailed("产品仍有开通项");

        assertEquals(List.of(ProductCode.ACQUIRING.getCode()), testMapper.agreementProductCodes(AGREEMENT_ID));
    }

    /** 新增开通项：ENABLED、约束两行、保证金成组、版本 6，开通项 Child CREATE 快照含约束。 */
    @Test
    void test_capability_add_normalFlow() {
        mockAcquiringSubscription();

        long capabilityId = post(BASE + "/capabilities", AgreementCapabilityBodyBuilder.template().reserve("0.05", 180)
            .buildAdd(5)).assertOk().assertText("status", AgreementCapabilityStatus.ENABLED.getCode()).assertVersion(6)
            .id("agreementCapabilityId");

        Map<String, Object> row = testMapper.agreementCapability(capabilityId);
        assertAll(() -> assertEquals(AgreementBuilder.ENTITY_NO, row.get("entity_no")),
            () -> assertEquals(0, new BigDecimal("0.05").compareTo((BigDecimal) row.get("rolling_reserve_rate"))),
            () -> assertEquals(180, ((Number) row.get("rolling_reserve_days")).intValue()),
            () -> assertEquals(List.of("CURRENCY:AED", "CURRENCY:USD"), testMapper.capabilityLimitValues(capabilityId)));
        BusinessChangeChecker.Record root = history.record(BusinessChangeRecordRecordType.AGREEMENT.getCode(), AGREEMENT_ID);
        history.record(BusinessChangeRecordRecordType.AGREEMENT_CAPABILITY.getCode(), capabilityId).assertParent(root)
            .assertVersions("6:CREATE").assertLastReason(BusinessChangeReason.AGREEMENT_CAPABILITY_ADDED.getCode())
            .assertLastData("rollingReserveRate", "0.05").assertIntegrity();
        get(BASE).assertOk().assertVersion(6);
    }

    /** 同 (OE, 可开通项) 已存在为 409；OE 未覆盖、产品未订阅、目录项未启用、合同已结束为业务检查失败。 */
    @Test
    void test_capability_add_businessRuleViolation() {
        post(BASE + "/capabilities", AgreementCapabilityBodyBuilder.template().buildAdd(5))
            .assertBusinessCheckFailed("可开通项所属产品未订阅");
        mockAcquiringSubscription();
        post(BASE + "/capabilities", AgreementCapabilityBodyBuilder.template().entityNo("AE999").buildAdd(5))
            .assertBusinessCheckFailed("合同不覆盖该 OE");
        testMapper.updateProductCapabilityStatus(CommercialCodes.Capability.CARD_MC, ProductStatus.RETIRED.getCode());
        post(BASE + "/capabilities", AgreementCapabilityBodyBuilder.template().capabilityCode(CommercialCodes.Capability.CARD_MC)
            .buildAdd(5)).assertBusinessCheckFailed("可开通项不存在或未启用");
        post(BASE + "/capabilities", AgreementCapabilityBodyBuilder.template().buildAdd(5)).assertOk();
        post(BASE + "/capabilities", AgreementCapabilityBodyBuilder.template().buildAdd(6)).assertConflict();

        long terminatedId = AGREEMENT_ID + 100;
        agreements.mock(AgreementBuilder.template().agreementId(terminatedId).agreementNo("B-TERM-1")
            .status(AgreementStatus.TERMINATED), 3);
        post("/agreements/v1/" + terminatedId + "/capabilities", AgreementCapabilityBodyBuilder.template().buildAdd(3))
            .assertBusinessCheckFailed("合同已结束");

        assertEquals(1, testMapper.countAgreementCapabilities(AGREEMENT_ID));
    }

    /** 条款与约束规范只依赖输入：保证金不成组、比例越界、约束值格式、REQUIRE_3DS 取值、重复约束与倒置区间。 */
    @Test
    void test_capability_add_invalidParameter() {
        mockAcquiringSubscription();
        post(BASE + "/capabilities", AgreementCapabilityBodyBuilder.template().reserve("0.05", null).buildAdd(5))
            .assertBusinessCheckFailed("滚动保证金比例与天数必须同时提供");
        post(BASE + "/capabilities", AgreementCapabilityBodyBuilder.template().reserve("1.5", 30).buildAdd(5))
            .assertValidationError("rollingReserveRate");
        post(BASE + "/capabilities", AgreementCapabilityBodyBuilder.template()
            .limits(AgreementCapabilityBodyBuilder.limit(CapabilityLimitCode.CURRENCY, "usd")).buildAdd(5))
            .assertBusinessCheckFailed("约束值不符合类别格式");
        post(BASE + "/capabilities", AgreementCapabilityBodyBuilder.template()
            .limits(AgreementCapabilityBodyBuilder.limit(CapabilityLimitCode.REQUIRE_3DS, "0")).buildAdd(5))
            .assertBusinessCheckFailed("约束值不符合类别格式");
        post(BASE + "/capabilities", AgreementCapabilityBodyBuilder.template()
            .limits(AgreementCapabilityBodyBuilder.limit(CapabilityLimitCode.CURRENCY, "USD"),
                AgreementCapabilityBodyBuilder.limit(CapabilityLimitCode.CURRENCY, "USD")).buildAdd(5))
            .assertInvalidRequest();
        post(BASE + "/capabilities", AgreementCapabilityBodyBuilder.template().effectiveTo("2026-08-01T00:00:00Z")
            .buildAdd(5)).assertBusinessCheckFailed("开通项生效终点必须晚于起点");

        assertEquals(0, testMapper.countAgreementCapabilities(AGREEMENT_ID));
    }

    /** 改写开通项：SUSPENDED、终点与单个约束，版本 6，开通项 Child UPDATE 快照为改写后的完整内容。 */
    @Test
    void test_capability_update_normalFlow() {
        mockExistingCapability();

        put(BASE + "/capabilities/" + CAPABILITY_ID, AgreementCapabilityBodyBuilder.template()
            .status(AgreementCapabilityStatus.SUSPENDED).effectiveTo("2027-01-01T00:00:00Z")
            .limits(AgreementCapabilityBodyBuilder.limit(CapabilityLimitCode.CURRENCY, "USD"),
                AgreementCapabilityBodyBuilder.limit(CapabilityLimitCode.REQUIRE_3DS, "1"))
            .buildUpdate(5)).assertOk().assertText("status", AgreementCapabilityStatus.SUSPENDED.getCode()).assertVersion(6);

        Map<String, Object> row = testMapper.agreementCapability(CAPABILITY_ID);
        assertAll(() -> assertEquals(AgreementCapabilityStatus.SUSPENDED.getCode(), row.get("status")),
            () -> assertEquals(List.of("CURRENCY:USD", "REQUIRE_3DS:1"), testMapper.capabilityLimitValues(CAPABILITY_ID)),
            () -> assertNull(row.get("rolling_reserve_rate")));
        history.record(BusinessChangeRecordRecordType.AGREEMENT_CAPABILITY.getCode(), CAPABILITY_ID)
            .assertCurrentVersion(6).assertVersions("6:UPDATE")
            .assertLastReason(BusinessChangeReason.AGREEMENT_CAPABILITY_UPDATED.getCode())
            .assertLastData("status", AgreementCapabilityStatus.SUSPENDED.getCode())
            .assertLastData("effectiveTo", "2027-01-01T00:00:00.000000Z").assertIntegrity();
    }

    /** 内容不变的改写不推进版本；他合同的开通项为归属错误；过期版本 409。 */
    @Test
    void test_capability_update_replayOwnershipAndStaleVersion() {
        mockExistingCapability();
        long otherId = AGREEMENT_ID + 100;
        agreements.mock(AgreementBuilder.template().agreementId(otherId).agreementNo("B-OTHER-1")
            .status(AgreementStatus.ACTIVE), 2);

        put(BASE + "/capabilities/" + CAPABILITY_ID, unchangedBody(1)).assertOk().assertVersion(5);
        put("/agreements/v1/" + otherId + "/capabilities/" + CAPABILITY_ID, AgreementCapabilityBodyBuilder.template()
            .status(AgreementCapabilityStatus.DISABLED).buildUpdate(2)).assertBusinessCheckFailed("不属于该合同");
        put(BASE + "/capabilities/" + CAPABILITY_ID, AgreementCapabilityBodyBuilder.template()
            .status(AgreementCapabilityStatus.DISABLED).buildUpdate(4)).assertConflict();

        assertEquals(AgreementCapabilityStatus.ENABLED.getCode(), testMapper.agreementCapability(CAPABILITY_ID).get("status"));
        history.record(BusinessChangeRecordRecordType.AGREEMENT.getCode(), AGREEMENT_ID).assertCurrentVersion(5);
    }

    /** 已订阅 ACQUIRING 的存量订阅行（带受审计 Child 身份）。 */
    void mockAcquiringSubscription() {
        subscriptions.mockProduct(AgreementProductBuilder.template().agreementProductId(PRODUCT_ROW_ID)
            .agreementId(AGREEMENT_ID));
        agreement.auditedChild(BusinessChangeRecordRecordType.AGREEMENT_PRODUCT, PRODUCT_ROW_ID);
    }

    /** 存量开通项：AE993 上 ENABLED 的 CARD_VISA，约束 USD 与 AED（带受审计 Child 身份）。 */
    void mockExistingCapability() {
        mockAcquiringSubscription();
        subscriptions.mockCapability(AgreementCapabilityBuilder.template().agreementCapabilityId(CAPABILITY_ID)
            .agreementId(AGREEMENT_ID).entityNo(AgreementBuilder.ENTITY_NO)).currencies("USD", "AED");
        agreement.auditedChild(BusinessChangeRecordRecordType.AGREEMENT_CAPABILITY, CAPABILITY_ID);
    }

    /** 与存量开通项内容一致的改写请求体。 */
    static UpdateAgreementCapabilityRequestBody unchangedBody(long expectedVersion) {
        return AgreementCapabilityBodyBuilder.template().buildUpdate(expectedVersion);
    }

    /** 订阅集合请求体。 */
    static ReplaceAgreementProductsRequestBody products(long expectedVersion, ProductCode... codes) {
        ReplaceAgreementProductsRequestBody body = new ReplaceAgreementProductsRequestBody();
        body.setExpectedChangeVersion(expectedVersion);
        body.setProductCodes(Arrays.stream(codes).map(ProductCode::getCode).toList());
        return body;
    }

    /** 本合同某产品的订阅行 ID。 */
    long productRowId(ProductCode code) {
        return ((Number) testMapper.agreementProducts(AGREEMENT_ID).stream()
            .filter(row -> code.getCode().equals(row.get("product_code"))).findFirst().orElseThrow()
            .get("agreement_product_id")).longValue();
    }
}
