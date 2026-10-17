package com.paytech.compliance.api.response;

import java.util.List;

/** 一条结算资格控制及其范围（列与 t_settlement_control、t_settlement_control_scope 一致）。 */
public class SettlementControlResponseBody {

    /** 控制 ID（control_id），十进制字符串。 */
    private String controlId;

    /** 控制编号（control_no）。 */
    private String controlNo;

    /** 角色类型（role_type）：CLIENT、LOCAL_PARTNER。 */
    private String roleType;

    /** 角色 ID（role_id），十进制字符串。 */
    private String roleId;

    /** 控制类型（control_type）：SETTLEMENT_BLOCK、MIN_SECURITY_DEPOSIT。 */
    private String controlType;

    /** 控制原因代码（reason_code），取自结算资格控制原因目录。 */
    private String reasonCode;

    /** 生效时间（effective_from），UTC 六位微秒。 */
    private String effectiveFrom;

    /** 失效时间（effective_to），UTC 六位微秒；为空表示长期。 */
    private String effectiveTo;

    /** 来源 Action Effect ID（source_action_effect_id），十进制字符串。 */
    private String sourceActionEffectId;

    /** 控制状态（status）：ACTIVE、RELEASED、EXPIRED。 */
    private String status;

    /** 最低保证金金额（required_amount），十进制字符串，最多 8 位小数；仅 MIN_SECURITY_DEPOSIT。 */
    private String requiredAmount;

    /** 最低保证金币种（required_currency），ISO 4217 alpha-3。 */
    private String requiredCurrency;

    /** 释放条件代码（release_condition_code）；可空。 */
    private String releaseConditionCode;

    /** 控制实例键（control_instance_key），同一角色同一实例键同时只允许一条 ACTIVE。 */
    private String controlInstanceKey;

    /** 控制内容哈希（content_hash），SHA-256 小写十六进制。 */
    private String contentHash;

    /** 活动控制占位（active_slot）：ACTIVE 时为 true，否则为空。 */
    private Boolean activeSlot;

    /** 控制并发版本（version）。 */
    private Integer version;

    /** 创建时间（create_time），UTC 六位微秒。 */
    private String createTime;

    /** 最后更新时间（update_time），UTC 六位微秒。 */
    private String updateTime;

    /** 适用范围。 */
    private List<SettlementControlScopeResponseBody> scopes;

    /** 读取控制 ID（control_id），十进制字符串。 */
    public String getControlId() {
        return controlId;
    }

    /** 设置控制 ID（control_id），十进制字符串。 */
    public void setControlId(String controlId) {
        this.controlId = controlId;
    }

    /** 读取控制编号（control_no）。 */
    public String getControlNo() {
        return controlNo;
    }

    /** 设置控制编号（control_no）。 */
    public void setControlNo(String controlNo) {
        this.controlNo = controlNo;
    }

    /** 读取角色类型（role_type）：CLIENT、LOCAL_PARTNER。 */
    public String getRoleType() {
        return roleType;
    }

    /** 设置角色类型（role_type）：CLIENT、LOCAL_PARTNER。 */
    public void setRoleType(String roleType) {
        this.roleType = roleType;
    }

    /** 读取角色 ID（role_id），十进制字符串。 */
    public String getRoleId() {
        return roleId;
    }

    /** 设置角色 ID（role_id），十进制字符串。 */
    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    /** 读取控制类型（control_type）：SETTLEMENT_BLOCK、MIN_SECURITY_DEPOSIT。 */
    public String getControlType() {
        return controlType;
    }

    /** 设置控制类型（control_type）：SETTLEMENT_BLOCK、MIN_SECURITY_DEPOSIT。 */
    public void setControlType(String controlType) {
        this.controlType = controlType;
    }

    /** 读取控制原因代码（reason_code），取自结算资格控制原因目录。 */
    public String getReasonCode() {
        return reasonCode;
    }

    /** 设置控制原因代码（reason_code），取自结算资格控制原因目录。 */
    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    /** 读取生效时间（effective_from），UTC 六位微秒。 */
    public String getEffectiveFrom() {
        return effectiveFrom;
    }

    /** 设置生效时间（effective_from），UTC 六位微秒。 */
    public void setEffectiveFrom(String effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    /** 读取失效时间（effective_to），UTC 六位微秒；为空表示长期。 */
    public String getEffectiveTo() {
        return effectiveTo;
    }

    /** 设置失效时间（effective_to），UTC 六位微秒；为空表示长期。 */
    public void setEffectiveTo(String effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    /** 读取来源 Action Effect ID（source_action_effect_id），十进制字符串。 */
    public String getSourceActionEffectId() {
        return sourceActionEffectId;
    }

    /** 设置来源 Action Effect ID（source_action_effect_id），十进制字符串。 */
    public void setSourceActionEffectId(String sourceActionEffectId) {
        this.sourceActionEffectId = sourceActionEffectId;
    }

    /** 读取控制状态（status）：ACTIVE、RELEASED、EXPIRED。 */
    public String getStatus() {
        return status;
    }

    /** 设置控制状态（status）：ACTIVE、RELEASED、EXPIRED。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 读取最低保证金金额（required_amount），十进制字符串，最多 8 位小数；仅 MIN_SECURITY_DEPOSIT。 */
    public String getRequiredAmount() {
        return requiredAmount;
    }

    /** 设置最低保证金金额（required_amount），十进制字符串，最多 8 位小数；仅 MIN_SECURITY_DEPOSIT。 */
    public void setRequiredAmount(String requiredAmount) {
        this.requiredAmount = requiredAmount;
    }

    /** 读取最低保证金币种（required_currency），ISO 4217 alpha-3。 */
    public String getRequiredCurrency() {
        return requiredCurrency;
    }

    /** 设置最低保证金币种（required_currency），ISO 4217 alpha-3。 */
    public void setRequiredCurrency(String requiredCurrency) {
        this.requiredCurrency = requiredCurrency;
    }

    /** 读取释放条件代码（release_condition_code）；可空。 */
    public String getReleaseConditionCode() {
        return releaseConditionCode;
    }

    /** 设置释放条件代码（release_condition_code）；可空。 */
    public void setReleaseConditionCode(String releaseConditionCode) {
        this.releaseConditionCode = releaseConditionCode;
    }

    /** 读取控制实例键（control_instance_key），同一角色同一实例键同时只允许一条 ACTIVE。 */
    public String getControlInstanceKey() {
        return controlInstanceKey;
    }

    /** 设置控制实例键（control_instance_key），同一角色同一实例键同时只允许一条 ACTIVE。 */
    public void setControlInstanceKey(String controlInstanceKey) {
        this.controlInstanceKey = controlInstanceKey;
    }

    /** 读取控制内容哈希（content_hash），SHA-256 小写十六进制。 */
    public String getContentHash() {
        return contentHash;
    }

    /** 设置控制内容哈希（content_hash），SHA-256 小写十六进制。 */
    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    /** 读取活动控制占位（active_slot）：ACTIVE 时为 true，否则为空。 */
    public Boolean getActiveSlot() {
        return activeSlot;
    }

    /** 设置活动控制占位（active_slot）：ACTIVE 时为 true，否则为空。 */
    public void setActiveSlot(Boolean activeSlot) {
        this.activeSlot = activeSlot;
    }

    /** 读取控制并发版本（version）。 */
    public Integer getVersion() {
        return version;
    }

    /** 设置控制并发版本（version）。 */
    public void setVersion(Integer version) {
        this.version = version;
    }

    /** 读取创建时间（create_time），UTC 六位微秒。 */
    public String getCreateTime() {
        return createTime;
    }

    /** 设置创建时间（create_time），UTC 六位微秒。 */
    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    /** 读取最后更新时间（update_time），UTC 六位微秒。 */
    public String getUpdateTime() {
        return updateTime;
    }

    /** 设置最后更新时间（update_time），UTC 六位微秒。 */
    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    /** 读取适用范围。 */
    public List<SettlementControlScopeResponseBody> getScopes() {
        return scopes;
    }

    /** 设置适用范围。 */
    public void setScopes(List<SettlementControlScopeResponseBody> scopes) {
        this.scopes = scopes;
    }
}
