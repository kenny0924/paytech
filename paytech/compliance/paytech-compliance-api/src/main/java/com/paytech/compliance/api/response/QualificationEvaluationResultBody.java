package com.paytech.compliance.api.response;

/** 一个主体在判定时间的资格结论。 */
public class QualificationEvaluationResultBody {

    /** 角色类型 */
    private String roleType;

    /** 角色标识 */
    private String roleId;

    /**
     * 是否合格
     * <li>仅 status 为 ELIGIBLE 时为 true。</li>
     */
    private Boolean eligible;

    /**
     * 实时状态
     * <li>ELIGIBLE、NOT_REGISTERED、REJECTED、REVOKED、EXPIRED、NOT_YET_VALID、SETTLEMENT_BLOCKED（仅 CLIENT）。</li>
     */
    private String status;

    /**
     * 据以判定的资格类型
     * <li>NOT_REGISTERED 时为空。</li>
     */
    private String qualificationType;

    /**
     * 据以判定的资格 ID
     * <li>十进制字符串；NOT_REGISTERED 时为空。</li>
     */
    private String qualificationId;

    /**
     * 有效开始时间
     * <li>UTC 六位微秒；NOT_REGISTERED 时为空。</li>
     */
    private String validFrom;

    /**
     * 有效结束时间
     * <li>UTC 六位微秒；没有预定结束时间或 NOT_REGISTERED 时为空。</li>
     */
    private String validTo;

    /** 读取角色类型。 */
    public String getRoleType() {
        return roleType;
    }

    /** 设置角色类型。 */
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

    /** 读取是否合格。 */
    public Boolean getEligible() {
        return eligible;
    }

    /** 设置是否合格。 */
    public void setEligible(Boolean eligible) {
        this.eligible = eligible;
    }

    /** 读取实时状态。 */
    public String getStatus() {
        return status;
    }

    /** 设置实时状态。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 读取据以判定的资格类型。 */
    public String getQualificationType() {
        return qualificationType;
    }

    /** 设置据以判定的资格类型。 */
    public void setQualificationType(String qualificationType) {
        this.qualificationType = qualificationType;
    }

    /** 读取据以判定的资格 ID。 */
    public String getQualificationId() {
        return qualificationId;
    }

    /** 设置据以判定的资格 ID。 */
    public void setQualificationId(String qualificationId) {
        this.qualificationId = qualificationId;
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
}
