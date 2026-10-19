package com.paytech.commercial.test.builder;

import java.util.ArrayList;
import java.util.List;

import com.paytech.commercial.api.agreement.request.AgreementDocumentEntry;
import com.paytech.commercial.api.agreement.request.AgreementPartyEntry;
import com.paytech.commercial.api.agreement.request.CreateAgreementRequestBody;
import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.FundMode;
import com.paytech.commercial.api.enums.PricingAuthority;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementDocumentRefDocumentType;
import com.paytech.commercial.domain.enums.AgreementPartyPartyRole;

/**
 * POST /agreements/v1 请求体 Builder：client() / partner() 给出合法默认值，用例只覆盖与场景相关的字段
 * <li>不访问数据库；可显式设置 null 或非法组合构造负例，build() 不修复输入。</li>
 */
public final class CreateAgreementBodyBuilder {
    /** 默认合同编号 */
    public static final String AGREEMENT_NO = "B-AGR-0001";

    /** 默认 Local Partner */
    public static final String LOCAL_PARTNER_ID = "930000000000921";

    /** 平台签约方 Party */
    public static final String PLATFORM_PARTY_ID = "930000000000913";

    /** 平台签约方资料版本 */
    public static final String PLATFORM_PROFILE_ID = "930000000000914";

    /** 签署文件 */
    public static final String SIGNING_FILE_ID = "930000000000905";

    /** 签署文件 SHA-256 */
    public static final String SIGNING_HASH = "a".repeat(64);

    private final CreateAgreementRequestBody body = new CreateAgreementRequestBody();

    private CreateAgreementBodyBuilder() {
    }

    /**
     * CLIENT 合同：MOR、经平台结算、平台定价，绑定 AE993；Client 与平台两个签约方，一份签署文件
     *
     * @return 新 Builder
     */
    public static CreateAgreementBodyBuilder client() {
        CreateAgreementBodyBuilder builder = new CreateAgreementBodyBuilder();
        builder.body.setAgreementNo(AGREEMENT_NO);
        builder.body.setAgreementName("Synthetic Client Agreement");
        builder.body.setAgreementType(AgreementAgreementType.CLIENT.getCode());
        builder.body.setBusinessMode(BusinessMode.MOR.getCode());
        builder.body.setFundMode(FundMode.SETTLE_THROUGH_PLATFORM.getCode());
        builder.body.setPricingAuthority(PricingAuthority.PLATFORM.getCode());
        builder.body.setClientId(Long.toString(AgreementBuilder.CLIENT_ID));
        builder.body.setEntityNo(AgreementBuilder.ENTITY_NO);
        builder.body.setEffectiveFrom("2026-09-01T00:00:00Z");
        builder.body.setParties(new ArrayList<>(List.of(
            party(Long.toString(AgreementPartyBuilder.PARTY_ID), Long.toString(AgreementPartyBuilder.PARTY_PROFILE_ID),
                AgreementPartyPartyRole.CLIENT),
            party(PLATFORM_PARTY_ID, PLATFORM_PROFILE_ID, AgreementPartyPartyRole.PLATFORM_SIGNATORY))));
        builder.body.setDocuments(new ArrayList<>(List.of(document(AgreementDocumentRefDocumentType.SIGNING_DOCUMENT,
            SIGNING_FILE_ID, SIGNING_HASH))));
        return builder;
    }

    /**
     * PARTNER 合同：Local Partner 与平台两个签约方，覆盖 AE993 与 AE994，一份签署文件
     *
     * @return 新 Builder
     */
    public static CreateAgreementBodyBuilder partner() {
        CreateAgreementBodyBuilder builder = client();
        builder.body.setAgreementNo("B-AGR-P-0001");
        builder.body.setAgreementName("Synthetic Partner Agreement");
        builder.body.setAgreementType(AgreementAgreementType.PARTNER.getCode());
        builder.body.setBusinessMode(null);
        builder.body.setFundMode(null);
        builder.body.setPricingAuthority(null);
        builder.body.setClientId(null);
        builder.body.setEntityNo(null);
        builder.body.setLocalPartnerId(LOCAL_PARTNER_ID);
        builder.body.setOperatingEntityNos(new ArrayList<>(List.of(AgreementBuilder.ENTITY_NO, "AE994")));
        builder.body.getParties().set(0, party(Long.toString(AgreementPartyBuilder.PARTY_ID),
            Long.toString(AgreementPartyBuilder.PARTY_PROFILE_ID), AgreementPartyPartyRole.LOCAL_PARTNER));
        return builder;
    }

    /** 一个参与方角色。 */
    public static AgreementPartyEntry party(String partyId, String profileId, AgreementPartyPartyRole role) {
        AgreementPartyEntry entry = new AgreementPartyEntry();
        entry.setPartyId(partyId);
        entry.setPartyProfileId(profileId);
        entry.setPartyRole(role.getCode());
        return entry;
    }

    /** 一份合同文件。 */
    public static AgreementDocumentEntry document(AgreementDocumentRefDocumentType type, String fileId, String hash) {
        AgreementDocumentEntry entry = new AgreementDocumentEntry();
        entry.setDocumentType(type.getCode());
        entry.setFileId(fileId);
        entry.setContentHash(hash);
        return entry;
    }

    /** 设置合同编号。 */
    public CreateAgreementBodyBuilder agreementNo(String value) {
        body.setAgreementNo(value);
        return this;
    }

    /** 设置合同名称。 */
    public CreateAgreementBodyBuilder agreementName(String value) {
        body.setAgreementName(value);
        return this;
    }

    /** 设置业务模式 Code。 */
    public CreateAgreementBodyBuilder businessMode(String value) {
        body.setBusinessMode(value);
        return this;
    }

    /** 设置资金模式 Code。 */
    public CreateAgreementBodyBuilder fundMode(String value) {
        body.setFundMode(value);
        return this;
    }

    /** 设置定价权 Code。 */
    public CreateAgreementBodyBuilder pricingAuthority(String value) {
        body.setPricingAuthority(value);
        return this;
    }

    /** 设置推荐伙伴。 */
    public CreateAgreementBodyBuilder referralPartnerId(String value) {
        body.setReferralPartnerId(value);
        return this;
    }

    /** 设置 Local Partner。 */
    public CreateAgreementBodyBuilder localPartnerId(String value) {
        body.setLocalPartnerId(value);
        return this;
    }

    /** 设置 CLIENT 合同 OE。 */
    public CreateAgreementBodyBuilder entityNo(String value) {
        body.setEntityNo(value);
        return this;
    }

    /** 设置 PARTNER 覆盖 OE。 */
    public CreateAgreementBodyBuilder operatingEntityNos(List<String> value) {
        body.setOperatingEntityNos(value);
        return this;
    }

    /** 设置生效区间。 */
    public CreateAgreementBodyBuilder effective(String from, String to) {
        body.setEffectiveFrom(from);
        body.setEffectiveTo(to);
        return this;
    }

    /** 设置参与方角色。 */
    public CreateAgreementBodyBuilder parties(List<AgreementPartyEntry> value) {
        body.setParties(value);
        return this;
    }

    /** 设置合同文件。 */
    public CreateAgreementBodyBuilder documents(List<AgreementDocumentEntry> value) {
        body.setDocuments(value);
        return this;
    }

    /** 返回请求体。 */
    public CreateAgreementRequestBody build() {
        return body;
    }
}
