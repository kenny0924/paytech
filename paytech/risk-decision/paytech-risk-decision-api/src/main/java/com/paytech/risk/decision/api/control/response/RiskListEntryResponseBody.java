package com.paytech.risk.decision.api.control.response;

/** 风险名单成员；主体键只返回脱敏形式。 */
public class RiskListEntryResponseBody {

    /** 成员 ID，正整数十进制字符串。 */
    private String entryId;

    /** 名单 ID，正整数十进制字符串。 */
    private String listId;

    /** 主体类型 Code。 */
    private String subjectType;

    /** 脱敏后的规范化主体键。 */
    private String maskedSubjectKey;

    /** 来源 Code，ACTION 或 MANUAL。 */
    private String sourceType;

    /** 状态 Code，ACTIVE、EXPIRED、RELEASED 或 SUPERSEDED。 */
    private String status;

    /** 生效起点，UTC ISO-8601，含。 */
    private String effectiveFrom;

    /** 生效终点，UTC ISO-8601，不含；长期有效时为空。 */
    private String effectiveTo;

    /** 加入原因。 */
    private String reason;

    /** 人工添加人账号 ID；动作来源为空。 */
    private String createdBy;

    /** 人工失效人账号 ID；未失效为空。 */
    private String deactivatedBy;

    /** 失效原因；未失效为空。 */
    private String deactivatedReason;

    /** 创建时间，UTC ISO-8601。 */
    private String createTime;

    /** 读取成员 ID。 */
    public String getEntryId() { return entryId; }

    /** 设置成员 ID。 */
    public void setEntryId(String entryId) { this.entryId = entryId; }

    /** 读取名单 ID。 */
    public String getListId() { return listId; }

    /** 设置名单 ID。 */
    public void setListId(String listId) { this.listId = listId; }

    /** 读取主体类型 Code。 */
    public String getSubjectType() { return subjectType; }

    /** 设置主体类型 Code。 */
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }

    /** 读取脱敏后的规范化主体键。 */
    public String getMaskedSubjectKey() { return maskedSubjectKey; }

    /** 设置脱敏后的规范化主体键。 */
    public void setMaskedSubjectKey(String maskedSubjectKey) { this.maskedSubjectKey = maskedSubjectKey; }

    /** 读取来源 Code。 */
    public String getSourceType() { return sourceType; }

    /** 设置来源 Code。 */
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    /** 读取状态 Code。 */
    public String getStatus() { return status; }

    /** 设置状态 Code。 */
    public void setStatus(String status) { this.status = status; }

    /** 读取生效起点。 */
    public String getEffectiveFrom() { return effectiveFrom; }

    /** 设置生效起点。 */
    public void setEffectiveFrom(String effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    /** 读取生效终点。 */
    public String getEffectiveTo() { return effectiveTo; }

    /** 设置生效终点。 */
    public void setEffectiveTo(String effectiveTo) { this.effectiveTo = effectiveTo; }

    /** 读取加入原因。 */
    public String getReason() { return reason; }

    /** 设置加入原因。 */
    public void setReason(String reason) { this.reason = reason; }

    /** 读取人工添加人。 */
    public String getCreatedBy() { return createdBy; }

    /** 设置人工添加人。 */
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    /** 读取人工失效人。 */
    public String getDeactivatedBy() { return deactivatedBy; }

    /** 设置人工失效人。 */
    public void setDeactivatedBy(String deactivatedBy) { this.deactivatedBy = deactivatedBy; }

    /** 读取失效原因。 */
    public String getDeactivatedReason() { return deactivatedReason; }

    /** 设置失效原因。 */
    public void setDeactivatedReason(String deactivatedReason) { this.deactivatedReason = deactivatedReason; }

    /** 读取创建时间。 */
    public String getCreateTime() { return createTime; }

    /** 设置创建时间。 */
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
