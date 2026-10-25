package com.paytech.commercial.auto.ext.http.term;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.FeeTaxTreatment;
import com.paytech.commercial.api.enums.PricingModel;
import com.paytech.commercial.api.term.CommercialTermApi;
import com.paytech.commercial.api.term.request.CommercialTermPriceRequestBody;
import com.paytech.commercial.api.term.request.CreateCommercialTermPlanRequestBody;
import com.paytech.commercial.api.term.request.CreateCommercialTermVersionRequestBody;
import com.paytech.commercial.api.term.request.ReplaceCommercialTermPricesRequestBody;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementPartyPartyRole;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.commercial.test.base.term.AbstractCommercialTermApiTest;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.AgreementPartyBuilder;
import com.paytech.commercial.test.mocker.local.agreement.AgreementWriteMocker;
import com.paytech.foundation.common.error.CommonErrorCode;

import tools.jackson.databind.JsonNode;

/**
 * 保存平台服务费价目时的法律主体近似校验（technique.md §3.3.3，AC-10）：Partner 合同中 OPERATING_ENTITY 角色的 Party 必须等于
 * LOCAL_PARTNER 角色的 Party；PARTNER_SHARE 不校验。
 *
 * @see CommercialTermApi#replacePrices(String, String, ReplaceCommercialTermPricesRequestBody)
 */
class PartnerServiceChargePriceHttpTest extends AbstractCommercialTermApiTest {
    private static final long PARTNER_AGREEMENT_ID = AgreementBuilder.AGREEMENT_ID + 500;
    private static final long LOCAL_PARTNER_ID = 930000000000981L;
    private static final long PARTNER_PARTY_ID = 930000000000983L;
    private static final long OTHER_ENTITY_PARTY_ID = 930000000000984L;

    @Autowired
    AgreementWriteMocker agreements;

    /** 每例：目录与 ACTIVE 卡画像。 */
    @BeforeEach
    void setUpClassMocks() {
        catalog.mockSeedCatalog();
        mockCardProfiles(PricingProfileStatus.ACTIVE);
    }

    /** OE Party 与 Local Partner Party 不同：平台服务费价目 400，价格集合不写入；分成价目不受影响。 */
    @Test
    void test_businessRuleViolation_entityPartyDiffersFromPartner() {
        mockPartnerAgreement(OTHER_ENTITY_PARTY_ID);

        JsonNode charge = createVersion(CommercialCodes.TermType.PLATFORM_SERVICE_CHARGE, "PLAN-PSC-1");
        assertProblem(replacePricesResult(charge, partnerPrice()), 400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(),
            "法律主体不一致");
        assertEquals(List.of(0, 0, 0), testMapper.countPriceSet(Long.parseLong(text(charge, "versionId"))));

        JsonNode share = createVersion(CommercialCodes.TermType.PARTNER_SHARE, "PLAN-PS-1");
        replacePrices(share, partnerPrice());
    }

    /** OE Party 等于 Local Partner Party：平台服务费价目正常保存。 */
    @Test
    void test_normalFlow_entityPartyEqualsPartner() {
        mockPartnerAgreement(PARTNER_PARTY_ID);

        JsonNode charge = createVersion(CommercialCodes.TermType.PLATFORM_SERVICE_CHARGE, "PLAN-PSC-2");

        assertEquals(List.of(1, 1, 0), testMapper.countPriceSet(
            Long.parseLong(text(replacePrices(charge, partnerPrice()), "versionId"))));
    }

    /** ACTIVE 的 PARTNER 合同覆盖 AE993，Local Partner 行用 PARTNER_PARTY_ID，OE 行用给定 Party。 */
    private void mockPartnerAgreement(long entityPartyId) {
        agreements.mock(AgreementBuilder.template().agreementId(PARTNER_AGREEMENT_ID).agreementNo("PSC-PARTNER-1")
                .agreementType(AgreementAgreementType.PARTNER).businessMode(null).fundMode(null).pricingAuthority(null)
                .clientId(null).entityNo(null).localPartnerId(LOCAL_PARTNER_ID).status(AgreementStatus.ACTIVE), 1)
            .operatingEntity(930000000000985L, ENTITY_NO)
            .party(AgreementPartyBuilder.template().agreementPartyId(930000000000986L).partyId(PARTNER_PARTY_ID)
                .partyRole(AgreementPartyPartyRole.LOCAL_PARTNER))
            .party(AgreementPartyBuilder.template().agreementPartyId(930000000000987L).partyId(entityPartyId)
                .partyRole(AgreementPartyPartyRole.OPERATING_ENTITY));
    }

    /** 经正式 API 为 Local Partner 创建方案与引用 Partner 合同的 DRAFT 版本。 */
    private JsonNode createVersion(String termType, String planCode) {
        CreateCommercialTermPlanRequestBody plan = new CreateCommercialTermPlanRequestBody();
        plan.setPlanCode(planCode);
        plan.setTermType(termType);
        plan.setAgreementId(Long.toString(PARTNER_AGREEMENT_ID));
        plan.setLocalPartnerId(Long.toString(LOCAL_PARTNER_ID));
        JsonNode created = ok(201, http.post(TERMS + "/plans", headers(MAKER), plan));
        CreateCommercialTermVersionRequestBody version = new CreateCommercialTermVersionRequestBody();
        version.setExpectedChangeVersion(text(created, "changeVersion"));
        version.setAgreementId(Long.toString(PARTNER_AGREEMENT_ID));
        version.setPricingModel(PricingModel.BLENDED.getCode());
        version.setFeeTaxTreatment(FeeTaxTreatment.EXCLUSIVE.getCode());
        return ok(201, http.post(TERMS + "/plans/" + text(created, "planId") + "/versions", headers(MAKER), version));
    }

    /** AE993 USD 卡价格，Partner 条款不带结算字段，费率 0 占位。 */
    private static CommercialTermPriceRequestBody partnerPrice() {
        CommercialTermPriceRequestBody price = price("USD", CARD, 1, percentage("SERVICE_FEE", "0"));
        price.setSettlementBusinessDays(null);
        price.setBusinessCalendarCode(null);
        price.setSettlementTimezone(null);
        price.setCutoffSecondOfDay(null);
        return price;
    }
}
