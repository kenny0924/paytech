package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 一个受审主体的结构化资料；个人填写 individual 字段，组织填写 organization 字段
 * <li>v2 不含证件原值与地址；姓名、生日、国籍只用于合同完整性，compliance 筛查时另经 client-master 读取，不落库。</li>
 */
@Getter
@Setter
public class ClientReviewSubjectBody {

    /** Party ID。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String partyId;

    /** 单份资料 ID；不是资料版本。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String profileId;

    /**
     * 主体角色
     * <li>PRIMARY 为申请主主体，RELATED_PERSON 为组织关联人员。</li>
     */
    @NotBlank
    @Pattern(regexp = "^(PRIMARY|RELATED_PERSON)$")
    private String subjectRole;

    /**
     * 资格类型
     * <li>INDIVIDUAL_KYC、ORGANIZATION_KYB 或 RELATED_PERSON_KYC。</li>
     */
    @NotBlank
    @Pattern(regexp = "^(INDIVIDUAL_KYC|ORGANIZATION_KYB|RELATED_PERSON_KYC)$")
    private String qualificationType;

    /** 名（拉丁字母），个人主体填写。 */
    @Size(max = 128)
    private String givenName;

    /** 中间名，可空。 */
    @Size(max = 128)
    private String middleName;

    /** 姓（拉丁字母），个人主体填写。 */
    @Size(max = 128)
    private String familyName;

    /** 出生日期 YYYY-MM-DD，个人主体填写。 */
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private String birthDate;

    /** 国籍 ISO 3166-1 alpha-2，个人主体填写。 */
    @Pattern(regexp = "^[A-Z]{2}$")
    private String nationalityCountry;

    /** 居住国 ISO 3166-1 alpha-2，个人主体填写。 */
    @Pattern(regexp = "^[A-Z]{2}$")
    private String residenceCountry;

    /** 组织法定名称，组织主体填写。 */
    @Size(max = 128)
    private String legalName;

    /** 组织简称，可空。 */
    @Size(max = 128)
    private String tradeName;

    /** 注册国，组织主体填写。 */
    @Pattern(regexp = "^[A-Z]{2}$")
    private String incorporationCountry;

    /** 组织类型代码，组织主体填写。 */
    @Size(max = 64)
    private String organizationType;

    /** 成立日期 YYYY-MM-DD，组织主体填写。 */
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private String incorporationDate;

    /** 主证件类型代码（辖区标识类型目录）。 */
    @NotBlank
    @Size(max = 64)
    private String identifierType;

    /**
     * 签发国
     * <li>证件类型不要求签发国（如 LEI）时为空。</li>
     */
    @Pattern(regexp = "^[A-Z]{2}$")
    private String issuingCountry;

    /**
     * 证件号的 Vault Token
     * <li>LEGAL_IDENTIFIER_V1 STABLE Token；个人证件必填，机构公开登记号为空。</li>
     * <li>compliance 不 Reveal、不落库原值。</li>
     */
    @Size(max = 128)
    private String identifierVaultToken;

    /** 证件号脱敏值（仅末 4 位可见），可空。 */
    @Size(max = 64)
    private String identifierMasked;

    /** client-master 已绑定的永久身份键 ID。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String identityKeyId;

    /** 证件有效期开始 YYYY-MM-DD，可空。 */
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private String validFrom;

    /** 证件有效期结束 YYYY-MM-DD（不含当日），可空。 */
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private String validTo;
}
