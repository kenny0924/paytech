package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 一个受审主体的结构化资料；个人填写 individual 字段，组织填写 organization 字段。 */
public class ClientReviewSubjectBody {

    /** CCT Party ID。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String partyId;

    /** CCT 单份资料 ID；不是资料版本。 */
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

    /** 名，个人主体必填。 */
    @Size(max = 64)
    private String givenName;

    /** 中间名，可空。 */
    @Size(max = 64)
    private String middleName;

    /** 姓，个人主体必填。 */
    @Size(max = 64)
    private String familyName;

    /** 出生日期 YYYY-MM-DD，个人主体必填。 */
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private String birthDate;

    /** 国籍，个人主体必填。 */
    @Pattern(regexp = "^[A-Z]{2}$")
    private String nationalityCountry;

    /** 居住国，个人主体必填。 */
    @Pattern(regexp = "^[A-Z]{2}$")
    private String residenceCountry;

    /** 组织法定名称，组织主体必填。 */
    @Size(max = 64)
    private String legalName;

    /** 组织简称，可空。 */
    @Size(max = 64)
    private String tradeName;

    /** 注册国，组织主体必填。 */
    @Pattern(regexp = "^[A-Z]{2}$")
    private String incorporationCountry;

    /** 组织类型代码，组织主体必填。 */
    @Size(max = 64)
    private String organizationType;

    /** 成立日期 YYYY-MM-DD，组织主体必填。 */
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private String incorporationDate;

    /** 地址第一行。 */
    @NotBlank
    @Size(max = 255)
    private String addressLine1;

    /** 地址第二行，可空。 */
    @Size(max = 255)
    private String addressLine2;

    /** 城市。 */
    @NotBlank
    @Size(max = 128)
    private String city;

    /** 州或省，可空。 */
    @Size(max = 128)
    private String stateOrProvince;

    /** 邮政编码，可空。 */
    @Size(max = 32)
    private String postalCode;

    /** 地址所在国家。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z]{2}$")
    private String addressCountry;

    /** 证件类型代码。 */
    @NotBlank
    @Size(max = 64)
    private String identifierType;

    /**
     * 签发国
     * <li>证件类型不要求签发国（如 LEI）时为空。</li>
     */
    @Pattern(regexp = "^[A-Z]{2}$")
    private String issuingCountry;

    /** 客户填报的原始证件号；不得进入普通日志。 */
    @NotBlank
    @Size(max = 128)
    private String identifierValue;

    /** CCT 已绑定的永久身份键 ID。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String identityKeyId;

    /** 证件有效期开始 YYYY-MM-DD，可空。 */
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private String validFrom;

    /** 证件有效期结束 YYYY-MM-DD（不含当日），可空。 */
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private String validTo;

    /** 读取CCT Party ID。 */
    public String getPartyId() {
        return partyId;
    }

    /** 设置CCT Party ID。 */
    public void setPartyId(String partyId) {
        this.partyId = partyId;
    }

    /** 读取CCT 单份资料 ID；不是资料版本。 */
    public String getProfileId() {
        return profileId;
    }

    /** 设置CCT 单份资料 ID；不是资料版本。 */
    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    /** 读取主体角色。 */
    public String getSubjectRole() {
        return subjectRole;
    }

    /** 设置主体角色。 */
    public void setSubjectRole(String subjectRole) {
        this.subjectRole = subjectRole;
    }

    /** 读取资格类型。 */
    public String getQualificationType() {
        return qualificationType;
    }

    /** 设置资格类型。 */
    public void setQualificationType(String qualificationType) {
        this.qualificationType = qualificationType;
    }

    /** 读取名，个人主体必填。 */
    public String getGivenName() {
        return givenName;
    }

    /** 设置名，个人主体必填。 */
    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    /** 读取中间名，可空。 */
    public String getMiddleName() {
        return middleName;
    }

    /** 设置中间名，可空。 */
    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    /** 读取姓，个人主体必填。 */
    public String getFamilyName() {
        return familyName;
    }

    /** 设置姓，个人主体必填。 */
    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    /** 读取出生日期 YYYY-MM-DD，个人主体必填。 */
    public String getBirthDate() {
        return birthDate;
    }

    /** 设置出生日期 YYYY-MM-DD，个人主体必填。 */
    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    /** 读取国籍，个人主体必填。 */
    public String getNationalityCountry() {
        return nationalityCountry;
    }

    /** 设置国籍，个人主体必填。 */
    public void setNationalityCountry(String nationalityCountry) {
        this.nationalityCountry = nationalityCountry;
    }

    /** 读取居住国，个人主体必填。 */
    public String getResidenceCountry() {
        return residenceCountry;
    }

    /** 设置居住国，个人主体必填。 */
    public void setResidenceCountry(String residenceCountry) {
        this.residenceCountry = residenceCountry;
    }

    /** 读取组织法定名称，组织主体必填。 */
    public String getLegalName() {
        return legalName;
    }

    /** 设置组织法定名称，组织主体必填。 */
    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    /** 读取组织简称，可空。 */
    public String getTradeName() {
        return tradeName;
    }

    /** 设置组织简称，可空。 */
    public void setTradeName(String tradeName) {
        this.tradeName = tradeName;
    }

    /** 读取注册国，组织主体必填。 */
    public String getIncorporationCountry() {
        return incorporationCountry;
    }

    /** 设置注册国，组织主体必填。 */
    public void setIncorporationCountry(String incorporationCountry) {
        this.incorporationCountry = incorporationCountry;
    }

    /** 读取组织类型代码，组织主体必填。 */
    public String getOrganizationType() {
        return organizationType;
    }

    /** 设置组织类型代码，组织主体必填。 */
    public void setOrganizationType(String organizationType) {
        this.organizationType = organizationType;
    }

    /** 读取成立日期 YYYY-MM-DD，组织主体必填。 */
    public String getIncorporationDate() {
        return incorporationDate;
    }

    /** 设置成立日期 YYYY-MM-DD，组织主体必填。 */
    public void setIncorporationDate(String incorporationDate) {
        this.incorporationDate = incorporationDate;
    }

    /** 读取地址第一行。 */
    public String getAddressLine1() {
        return addressLine1;
    }

    /** 设置地址第一行。 */
    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    /** 读取地址第二行，可空。 */
    public String getAddressLine2() {
        return addressLine2;
    }

    /** 设置地址第二行，可空。 */
    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    /** 读取城市。 */
    public String getCity() {
        return city;
    }

    /** 设置城市。 */
    public void setCity(String city) {
        this.city = city;
    }

    /** 读取州或省，可空。 */
    public String getStateOrProvince() {
        return stateOrProvince;
    }

    /** 设置州或省，可空。 */
    public void setStateOrProvince(String stateOrProvince) {
        this.stateOrProvince = stateOrProvince;
    }

    /** 读取邮政编码，可空。 */
    public String getPostalCode() {
        return postalCode;
    }

    /** 设置邮政编码，可空。 */
    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    /** 读取地址所在国家。 */
    public String getAddressCountry() {
        return addressCountry;
    }

    /** 设置地址所在国家。 */
    public void setAddressCountry(String addressCountry) {
        this.addressCountry = addressCountry;
    }

    /** 读取证件类型代码。 */
    public String getIdentifierType() {
        return identifierType;
    }

    /** 设置证件类型代码。 */
    public void setIdentifierType(String identifierType) {
        this.identifierType = identifierType;
    }

    /** 读取签发国。 */
    public String getIssuingCountry() {
        return issuingCountry;
    }

    /** 设置签发国。 */
    public void setIssuingCountry(String issuingCountry) {
        this.issuingCountry = issuingCountry;
    }

    /** 读取客户填报的原始证件号；不得进入普通日志。 */
    public String getIdentifierValue() {
        return identifierValue;
    }

    /** 设置客户填报的原始证件号；不得进入普通日志。 */
    public void setIdentifierValue(String identifierValue) {
        this.identifierValue = identifierValue;
    }

    /** 读取CCT 已绑定的永久身份键 ID。 */
    public String getIdentityKeyId() {
        return identityKeyId;
    }

    /** 设置CCT 已绑定的永久身份键 ID。 */
    public void setIdentityKeyId(String identityKeyId) {
        this.identityKeyId = identityKeyId;
    }

    /** 读取证件有效期开始 YYYY-MM-DD，可空。 */
    public String getValidFrom() {
        return validFrom;
    }

    /** 设置证件有效期开始 YYYY-MM-DD，可空。 */
    public void setValidFrom(String validFrom) {
        this.validFrom = validFrom;
    }

    /** 读取证件有效期结束 YYYY-MM-DD（不含当日），可空。 */
    public String getValidTo() {
        return validTo;
    }

    /** 设置证件有效期结束 YYYY-MM-DD（不含当日），可空。 */
    public void setValidTo(String validTo) {
        this.validTo = validTo;
    }
}
