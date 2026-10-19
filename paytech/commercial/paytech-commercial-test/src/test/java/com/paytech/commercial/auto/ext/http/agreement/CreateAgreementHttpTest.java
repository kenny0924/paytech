package com.paytech.commercial.auto.ext.http.agreement;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.commercial.api.agreement.AgreementApi;
import com.paytech.commercial.api.agreement.request.CreateAgreementRequestBody;
import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.FundMode;
import com.paytech.commercial.api.enums.PricingAuthority;
import com.paytech.commercial.domain.enums.AgreementAgreementFormat;
import com.paytech.commercial.domain.enums.AgreementDocumentRefDocumentType;
import com.paytech.commercial.domain.enums.AgreementPartyPartyRole;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.service.handler.AgreementStatusChangedHandler;
import com.paytech.commercial.test.base.write.AbstractCommercialWriteApiTest;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.AgreementPartyBuilder;
import com.paytech.commercial.test.builder.CreateAgreementBodyBuilder;
import com.paytech.commercial.test.checker.write.CommercialWriteResponseChecker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

/**
 * 创建合同与合同详情（technique.md §2.2）：首次写入、同 agreementNo 重放、Owner XOR 与合同属性组合、本期资金模式 / 定价权限制、参数校验
 * <li>首次写入断言合同行、参与方、文件、覆盖 OE 与 AGREEMENT Root Version 1 CREATE 快照（独立重算完整性哈希）；创建不登记发布任务。</li>
 *
 * @see AgreementApi#createAgreement(String, CreateAgreementRequestBody)
 * @see AgreementApi#getAgreement(String)
 */
class CreateAgreementHttpTest extends AbstractCommercialWriteApiTest {
    private static final String PATH = "/agreements/v1";

    /** CLIENT 合同首次创建：DRAFT、版本 1、两个待签参与方、一份签署文件、Root CREATE 快照；详情返回同一内容。 */
    @Test
    void test_normalFlow_clientAgreement() {
        CommercialWriteResponseChecker response = post(PATH, CreateAgreementBodyBuilder.client().build()).assertOk()
            .assertText("agreementNo", CreateAgreementBodyBuilder.AGREEMENT_NO)
            .assertText("status", AgreementStatus.DRAFT.getCode()).assertVersion(1);
        long agreementId = response.id("agreementId");

        Map<String, Object> agreement = testMapper.agreement(agreementId);
        assertAll(() -> assertEquals(BusinessMode.MOR.getCode(), agreement.get("business_mode")),
            () -> assertEquals(FundMode.SETTLE_THROUGH_PLATFORM.getCode(), agreement.get("fund_mode")),
            () -> assertEquals(PricingAuthority.PLATFORM.getCode(), agreement.get("pricing_authority")),
            () -> assertEquals(AgreementBuilder.CLIENT_ID, ((Number) agreement.get("client_id")).longValue()),
            () -> assertEquals(AgreementBuilder.ENTITY_NO, agreement.get("entity_no")),
            () -> assertNull(agreement.get("local_partner_id")),
            () -> assertEquals(AgreementAgreementFormat.MERGED_PARTY.getCode(), agreement.get("agreement_format")),
            () -> assertEquals(ACTOR_ID, ((Number) agreement.get("create_by")).longValue()),
            () -> assertEquals(2, testMapper.agreementParties(agreementId).size()),
            () -> assertNull(testMapper.agreementParties(agreementId).getFirst().get("accepted_time")),
            () -> assertEquals(1, testMapper.agreementDocuments(agreementId).size()),
            () -> assertEquals(List.of(), testMapper.agreementEntityNos(agreementId)),
            () -> assertEquals(0, testMapper.countCompensationEvents(AgreementStatusChangedHandler.EVENT_TYPE)));
        history.record(BusinessChangeRecordRecordType.AGREEMENT.getCode(), agreementId).assertParent(null)
            .assertCurrentVersion(1).assertVersions("1:CREATE")
            .assertLastReason(BusinessChangeReason.AGREEMENT_CREATED.getCode())
            .assertLastData("status", AgreementStatus.DRAFT.getCode()).assertLastData("businessMode", BusinessMode.MOR.getCode())
            .assertLastActor(ACTOR_ID).assertIntegrity();

        CommercialWriteResponseChecker detail = get(PATH + "/" + agreementId).assertOk()
            .assertText("status", AgreementStatus.DRAFT.getCode()).assertText("entityNo", AgreementBuilder.ENTITY_NO)
            .assertVersion(1);
        assertAll(() -> assertEquals(2, detail.node("parties").size()),
            () -> assertEquals(AgreementDocumentRefDocumentType.SIGNING_DOCUMENT.getCode(),
                detail.node("documents").get(0).path("documentType").asString()),
            () -> assertEquals(List.of(), detail.texts("productCodes")),
            () -> assertEquals(List.of(), detail.texts("operatingEntityNos")));
    }

    /** PARTNER 合同首次创建：无业务模式三属性，覆盖两个 OE。 */
    @Test
    void test_normalFlow_partnerAgreement() {
        long agreementId = post(PATH, CreateAgreementBodyBuilder.partner().build()).assertOk()
            .assertText("status", AgreementStatus.DRAFT.getCode()).id("agreementId");

        Map<String, Object> agreement = testMapper.agreement(agreementId);
        assertAll(() -> assertNull(agreement.get("business_mode")), () -> assertNull(agreement.get("fund_mode")),
            () -> assertNull(agreement.get("client_id")),
            () -> assertEquals(Long.parseLong(CreateAgreementBodyBuilder.LOCAL_PARTNER_ID),
                ((Number) agreement.get("local_partner_id")).longValue()),
            () -> assertEquals(List.of(AgreementBuilder.ENTITY_NO, "AE994"), testMapper.agreementEntityNos(agreementId)));
        get(PATH + "/" + agreementId).assertOk().assertText("localPartnerId", CreateAgreementBodyBuilder.LOCAL_PARTNER_ID);
    }

    /** 同号同内容重放返回原合同，不新增行与快照。 */
    @Test
    void test_replay_sameContent() {
        long first = post(PATH, CreateAgreementBodyBuilder.client().build()).assertOk().id("agreementId");

        long second = post(PATH, CreateAgreementBodyBuilder.client().build()).assertOk().assertVersion(1).id("agreementId");

        assertAll(() -> assertEquals(first, second),
            () -> assertEquals(1, testMapper.countAgreementsByNo(CreateAgreementBodyBuilder.AGREEMENT_NO)),
            () -> assertEquals(2, testMapper.agreementParties(first).size()));
        history.record(BusinessChangeRecordRecordType.AGREEMENT.getCode(), first).assertVersions("1:CREATE");
    }

    /** 同号改内容（名称、参与方）返回 409，原合同不变。 */
    @Test
    void test_replay_changedContent() {
        long agreementId = post(PATH, CreateAgreementBodyBuilder.client().build()).assertOk().id("agreementId");

        post(PATH, CreateAgreementBodyBuilder.client().agreementName("Renamed").build()).assertConflict();
        post(PATH, CreateAgreementBodyBuilder.client().parties(List.of(CreateAgreementBodyBuilder.party(
            Long.toString(AgreementPartyBuilder.PARTY_ID), Long.toString(AgreementPartyBuilder.PARTY_PROFILE_ID),
            AgreementPartyPartyRole.CLIENT))).build()).assertConflict();

        assertAll(() -> assertEquals("Synthetic Client Agreement", testMapper.agreement(agreementId).get("agreement_name")),
            () -> assertEquals(2, testMapper.agreementParties(agreementId).size()));
    }

    /** Owner XOR 与合同类型不符：业务检查失败且不落库。 */
    @Test
    void test_ownershipViolation() {
        post(PATH, CreateAgreementBodyBuilder.client().localPartnerId(CreateAgreementBodyBuilder.LOCAL_PARTNER_ID).build())
            .assertBusinessCheckFailed("CLIENT 合同必须且只能指定");
        post(PATH, CreateAgreementBodyBuilder.client().entityNo(null).build())
            .assertBusinessCheckFailed("CLIENT 合同必须且只能指定");
        post(PATH, CreateAgreementBodyBuilder.partner().entityNo(AgreementBuilder.ENTITY_NO).build())
            .assertBusinessCheckFailed("PARTNER 合同必须且只能指定");
        post(PATH, CreateAgreementBodyBuilder.partner().businessMode(BusinessMode.MOR.getCode()).build())
            .assertBusinessCheckFailed("PARTNER 合同不能指定");
        post(PATH, CreateAgreementBodyBuilder.client().parties(List.of(CreateAgreementBodyBuilder.party(
            CreateAgreementBodyBuilder.PLATFORM_PARTY_ID, CreateAgreementBodyBuilder.PLATFORM_PROFILE_ID,
            AgreementPartyPartyRole.PLATFORM_SIGNATORY))).build()).assertBusinessCheckFailed("必须包含 CLIENT 签约方");

        assertEquals(0, testMapper.countAgreementsByNo(CreateAgreementBodyBuilder.AGREEMENT_NO));
    }

    /** CLIENT 三属性缺失、MOR 非经平台结算、本期不接受的 FEES_ONLY 与 PARTNER 定价权（有无推荐伙伴）均拒绝。 */
    @Test
    void test_businessRuleViolation_commercialModes() {
        post(PATH, CreateAgreementBodyBuilder.client().fundMode(null).build())
            .assertBusinessCheckFailed("必须指定 businessMode、fundMode 与 pricingAuthority");
        post(PATH, CreateAgreementBodyBuilder.client().fundMode(FundMode.FEES_ONLY.getCode()).build())
            .assertBusinessCheckFailed("MOR 合同只允许 SETTLE_THROUGH_PLATFORM");
        post(PATH, CreateAgreementBodyBuilder.client().businessMode(BusinessMode.PSP.getCode())
            .fundMode(FundMode.FEES_ONLY.getCode()).build()).assertBusinessCheckFailed("本期只接受 SETTLE_THROUGH_PLATFORM");
        post(PATH, CreateAgreementBodyBuilder.client().pricingAuthority(PricingAuthority.PARTNER.getCode()).build())
            .assertBusinessCheckFailed("必须指定 referralPartnerId");
        post(PATH, CreateAgreementBodyBuilder.client().pricingAuthority(PricingAuthority.PARTNER.getCode())
            .referralPartnerId(CreateAgreementBodyBuilder.LOCAL_PARTNER_ID).build())
            .assertBusinessCheckFailed("本期只接受 PLATFORM 定价权");

        assertEquals(0, testMapper.countAgreementsByNo(CreateAgreementBodyBuilder.AGREEMENT_NO));
    }

    /** PSP 合同与推荐伙伴（PLATFORM 定价权）是本期允许的组合。 */
    @Test
    void test_edgeCase_pspWithReferralPartner() {
        long agreementId = post(PATH, CreateAgreementBodyBuilder.client().businessMode(BusinessMode.PSP.getCode())
            .referralPartnerId(CreateAgreementBodyBuilder.LOCAL_PARTNER_ID).build()).assertOk().id("agreementId");

        assertAll(() -> assertEquals(BusinessMode.PSP.getCode(), testMapper.agreement(agreementId).get("business_mode")),
            () -> assertEquals(Long.parseLong(CreateAgreementBodyBuilder.LOCAL_PARTNER_ID),
                ((Number) testMapper.agreement(agreementId).get("referral_partner_id")).longValue()));
    }

    /** 字段格式（422 字段错误）、重复角色（400）、多份签署文件与倒置区间（业务检查）、缺少操作者头在落库前拒绝。 */
    @Test
    void test_invalidParameter() {
        post(PATH, CreateAgreementBodyBuilder.client().entityNo("ae1").build()).assertValidationError("entityNo");
        post(PATH, CreateAgreementBodyBuilder.client().parties(List.of()).build()).assertValidationError("parties");
        post(PATH, CreateAgreementBodyBuilder.client().effective("2026-09-01", null).build())
            .assertValidationError("effectiveFrom");
        post(PATH, CreateAgreementBodyBuilder.client().parties(List.of(
            CreateAgreementBodyBuilder.party("930000000000903", "930000000000904", AgreementPartyPartyRole.CLIENT),
            CreateAgreementBodyBuilder.party("930000000000903", "930000000000904", AgreementPartyPartyRole.CLIENT))).build())
            .assertInvalidRequest();
        post(PATH, CreateAgreementBodyBuilder.client().documents(List.of(
            CreateAgreementBodyBuilder.document(AgreementDocumentRefDocumentType.SIGNING_DOCUMENT, "930000000000905",
                CreateAgreementBodyBuilder.SIGNING_HASH),
            CreateAgreementBodyBuilder.document(AgreementDocumentRefDocumentType.SIGNING_DOCUMENT, "930000000000906",
                CreateAgreementBodyBuilder.SIGNING_HASH))).build())
            .assertBusinessCheckFailed("只能有一份 SIGNING_DOCUMENT");
        post(PATH, CreateAgreementBodyBuilder.client().effective("2026-09-01T00:00:00Z", "2026-08-01T00:00:00Z").build())
            .assertBusinessCheckFailed("生效终点必须晚于起点");
        HttpHeaders anonymous = TestRequestHeaders.internal(REQUEST_ID);
        exchange(HttpMethod.POST, PATH, anonymous, CreateAgreementBodyBuilder.client().build()).assertInvalidRequest();

        assertEquals(0, testMapper.countAgreementsByNo(CreateAgreementBodyBuilder.AGREEMENT_NO));
    }

    /** 详情：合同不存在为业务检查失败，ID 格式错误为参数错误。 */
    @Test
    void test_noDataFound() {
        get(PATH + "/930000000000999").assertBusinessCheckFailed("合同不存在");
        get(PATH + "/01").assertInvalidRequest();
    }
}
