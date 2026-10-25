package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 运营手工登记一条已批准的角色资格
 * <li>允许的组合：CLIENT + INDIVIDUAL_KYC/ORGANIZATION_KYB、OPERATING_ENTITY + ENTITY_UNDERWRITING、LOCAL_PARTNER + PARTNER_DUE_DILIGENCE。</li>
 */
public class QualificationManualDecisionRequestBody {

    /** 角色类型：CLIENT、OPERATING_ENTITY、LOCAL_PARTNER */
    @NotBlank
    @Pattern(regexp = "^(CLIENT|OPERATING_ENTITY|LOCAL_PARTNER)$")
    private String roleType;

    /**
     * 角色标识
     * <li>OPERATING_ENTITY 为 entity_no；CLIENT、LOCAL_PARTNER 为正十进制 ID。compliance 不核对主体是否存在。</li>
     */
    @NotBlank
    @Size(max = 64)
    private String roleId;

    /**
     * 资格类型
     * <li>必须是该角色允许的类型，否则 422 QUALIFICATION_INVALID。</li>
     */
    @NotBlank
    @Pattern(regexp = "^(INDIVIDUAL_KYC|ORGANIZATION_KYB|PARTNER_DUE_DILIGENCE|ENTITY_UNDERWRITING)$")
    private String qualificationType;

    /**
     * 有效开始时间
     * <li>UTC ISO-8601，例如 2026-10-24T00:00:00.000000Z。</li>
     */
    @NotBlank
    private String validFrom;

    /**
     * 有效结束时间
     * <li>UTC ISO-8601；为空表示没有预定结束时间；必须晚于有效开始时间。</li>
     */
    private String validTo;

    /** 登记原因代码 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String reasonCode;

    /**
     * 登记依据说明
     * <li>1—1000 字符；只供内部审计，不展示给客户。</li>
     */
    @NotBlank
    @Size(max = 1000)
    private String basisNote;

    /**
     * 页面读取的资格版本
     * <li>十进制字符串；该角色与资格类型已有资格时必填且必须等于当前版本，首次登记必须为空；否则 409 QUALIFICATION_CHANGED。</li>
     */
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String expectedVersion;

    /** 读取角色类型：CLIENT、OPERATING_ENTITY、LOCAL_PARTNER。 */
    public String getRoleType() {
        return roleType;
    }

    /** 设置角色类型：CLIENT、OPERATING_ENTITY、LOCAL_PARTNER。 */
    public void setRoleType(String roleType) {
        this.roleType = roleType;
    }

    /** 读取角色标识。 */
    public String getRoleId() {
        return roleId;
    }

    /** 设置角色标识。 */
    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    /** 读取资格类型。 */
    public String getQualificationType() {
        return qualificationType;
    }

    /** 设置资格类型。 */
    public void setQualificationType(String qualificationType) {
        this.qualificationType = qualificationType;
    }

    /** 读取有效开始时间。 */
    public String getValidFrom() {
        return validFrom;
    }

    /** 设置有效开始时间。 */
    public void setValidFrom(String validFrom) {
        this.validFrom = validFrom;
    }

    /** 读取有效结束时间。 */
    public String getValidTo() {
        return validTo;
    }

    /** 设置有效结束时间。 */
    public void setValidTo(String validTo) {
        this.validTo = validTo;
    }

    /** 读取登记原因代码。 */
    public String getReasonCode() {
        return reasonCode;
    }

    /** 设置登记原因代码。 */
    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    /** 读取登记依据说明。 */
    public String getBasisNote() {
        return basisNote;
    }

    /** 设置登记依据说明。 */
    public void setBasisNote(String basisNote) {
        this.basisNote = basisNote;
    }

    /** 读取页面读取的资格版本。 */
    public String getExpectedVersion() {
        return expectedVersion;
    }

    /** 设置页面读取的资格版本。 */
    public void setExpectedVersion(String expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
