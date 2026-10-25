package com.paytech.compliance.api.response;

/** 一条角色资格的当前状态、实时状态与当前决定来源；列表项与登记、撤销命令的结果共用。 */
public class QualificationResponseBody {

    /**
     * 角色资格 ID
     * <li>十进制字符串。</li>
     */
    private String qualificationId;

    /** 角色类型 */
    private String roleType;

    /** 角色标识 */
    private String roleId;

    /** 资格类型 */
    private String qualificationType;

    /**
     * 存储的资格状态
     * <li>APPROVED、REJECTED、REVOKED、EXPIRED。</li>
     */
    private String status;

    /**
     * 查询时实时判定的状态
     * <li>取值同资格判定接口的 status。</li>
     */
    private String effectiveStatus;

    /**
     * 有效开始时间
     * <li>UTC 六位微秒。</li>
     */
    private String validFrom;

    /**
     * 有效结束时间
     * <li>UTC 六位微秒；为空表示没有预定结束时间。</li>
     */
    private String validTo;

    /**
     * 当前决定 ID
     * <li>十进制字符串。</li>
     */
    private String currentDecisionId;

    /**
     * 当前决定来源
     * <li>REVIEWER（入驻审核）、MANUAL（手工登记或撤销）等。</li>
     */
    private String decisionSource;

    /**
     * 复审状态
     * <li>NOT_REQUIRED 或 COMPLETED。</li>
     */
    private String reviewStatus;

    /**
     * 资格并发版本
     * <li>十进制字符串；登记与撤销以此为 expectedVersion。</li>
     */
    private String version;

    /**
     * 最后更新时间
     * <li>UTC 六位微秒。</li>
     */
    private String updatedAt;

    /** 读取角色资格 ID。 */
    public String getQualificationId() {
        return qualificationId;
    }

    /** 设置角色资格 ID。 */
    public void setQualificationId(String qualificationId) {
        this.qualificationId = qualificationId;
    }

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

    /** 读取资格类型。 */
    public String getQualificationType() {
        return qualificationType;
    }

    /** 设置资格类型。 */
    public void setQualificationType(String qualificationType) {
        this.qualificationType = qualificationType;
    }

    /** 读取存储的资格状态。 */
    public String getStatus() {
        return status;
    }

    /** 设置存储的资格状态。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 读取查询时实时判定的状态。 */
    public String getEffectiveStatus() {
        return effectiveStatus;
    }

    /** 设置查询时实时判定的状态。 */
    public void setEffectiveStatus(String effectiveStatus) {
        this.effectiveStatus = effectiveStatus;
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

    /** 读取当前决定 ID。 */
    public String getCurrentDecisionId() {
        return currentDecisionId;
    }

    /** 设置当前决定 ID。 */
    public void setCurrentDecisionId(String currentDecisionId) {
        this.currentDecisionId = currentDecisionId;
    }

    /** 读取当前决定来源。 */
    public String getDecisionSource() {
        return decisionSource;
    }

    /** 设置当前决定来源。 */
    public void setDecisionSource(String decisionSource) {
        this.decisionSource = decisionSource;
    }

    /** 读取复审状态。 */
    public String getReviewStatus() {
        return reviewStatus;
    }

    /** 设置复审状态。 */
    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    /** 读取资格并发版本。 */
    public String getVersion() {
        return version;
    }

    /** 设置资格并发版本。 */
    public void setVersion(String version) {
        this.version = version;
    }

    /** 读取最后更新时间。 */
    public String getUpdatedAt() {
        return updatedAt;
    }

    /** 设置最后更新时间。 */
    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
