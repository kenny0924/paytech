package com.paytech.compliance.api.event;

import java.util.List;

/**
 * SettlementRestrictionChanged Schema v1 的 Payload（D27，事件目录 paytech-compliance-api/event/SettlementRestrictionChanged/v1）
 * <li>阶段 0 只定义 Schema：compliance 尚无结算控制实现与发布调用方；producer=compliance，consumers=payout、account，strictOrder=true。</li>
 * <li>聚合为结算资格控制：aggregateType={@link #AGGREGATE_TYPE}、aggregateId=controlId、aggregateVersion=t_settle_control_state_change.aggregate_version。</li>
 * <li>字段取自 t_settlement_control 与一条 t_settle_control_state_change；ID、金额与时间一律为字符串。</li>
 */
public class SettlementRestrictionChangedPayload {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "SettlementRestrictionChanged";

    /** Payload Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 聚合类型。 */
    public static final String AGGREGATE_TYPE = "SETTLEMENT_CONTROL";

    /** Payload 字段及顺序。 */
    public static final List<String> FIELDS = List.of("controlId", "controlNo", "roleType", "roleId", "controlType",
        "reasonCode", "sourceActionEffectId", "status", "effectiveFrom", "effectiveTo", "requiredAmount",
        "requiredCurrency", "contentHash", "changeId", "fromStatus", "toStatus", "changeReasonCode", "triggerType",
        "triggerId", "actorId", "aggregateVersion", "occurredAt");

    /** 结算资格控制 ID（aggregateId），十进制字符串。 */
    private String controlId;

    /** 控制编号。 */
    private String controlNo;

    /** 角色类型：CLIENT、LOCAL_PARTNER。 */
    private String roleType;

    /** 角色 ID，十进制字符串。 */
    private String roleId;

    /** 控制类型：SETTLEMENT_BLOCK、MIN_SECURITY_DEPOSIT。 */
    private String controlType;

    /** 控制原因代码。 */
    private String reasonCode;

    /** 来源 Action Effect ID（决策侧 ID，只存不联）。 */
    private String sourceActionEffectId;

    /** 变更后的控制状态：ACTIVE、RELEASED、EXPIRED。 */
    private String status;

    /** 生效时间，UTC。 */
    private String effectiveFrom;

    /** 失效时间，UTC；可空。 */
    private String effectiveTo;

    /** 最低保证金金额，十进制字符串；可空。 */
    private String requiredAmount;

    /** 最低保证金币种；可空。 */
    private String requiredCurrency;

    /** 控制内容哈希。 */
    private String contentHash;

    /** 状态变更 ID（t_settle_control_state_change.change_id）。 */
    private String changeId;

    /** 原状态；首次创建为空。 */
    private String fromStatus;

    /** 新状态。 */
    private String toStatus;

    /** 变更原因代码；可空。 */
    private String changeReasonCode;

    /** 触发类型：ACTION_EFFECT、OPERATION_COMMAND、SYSTEM_TIMER。 */
    private String triggerType;

    /** 触发标识；ACTION_EFFECT 时为决策侧 Effect ID。 */
    private String triggerId;

    /** 操作人；系统触发为空。 */
    private String actorId;

    /** 聚合版本（t_settle_control_state_change.aggregate_version），同一控制连续递增，strictOrder 依据。 */
    private long aggregateVersion;

    /** 变更发生时间（occurred_time），UTC。 */
    private String occurredAt;

    /** 读取结算资格控制 ID。 */
    public String getControlId() {
        return controlId;
    }

    /** 设置结算资格控制 ID。 */
    public void setControlId(String controlId) {
        this.controlId = controlId;
    }

    /** 读取控制编号。 */
    public String getControlNo() {
        return controlNo;
    }

    /** 设置控制编号。 */
    public void setControlNo(String controlNo) {
        this.controlNo = controlNo;
    }

    /** 读取角色类型。 */
    public String getRoleType() {
        return roleType;
    }

    /** 设置角色类型。 */
    public void setRoleType(String roleType) {
        this.roleType = roleType;
    }

    /** 读取角色 ID，十进制字符串。 */
    public String getRoleId() {
        return roleId;
    }

    /** 设置角色 ID，十进制字符串。 */
    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    /** 读取控制类型。 */
    public String getControlType() {
        return controlType;
    }

    /** 设置控制类型。 */
    public void setControlType(String controlType) {
        this.controlType = controlType;
    }

    /** 读取控制原因代码。 */
    public String getReasonCode() {
        return reasonCode;
    }

    /** 设置控制原因代码。 */
    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    /** 读取来源 Action Effect ID。 */
    public String getSourceActionEffectId() {
        return sourceActionEffectId;
    }

    /** 设置来源 Action Effect ID。 */
    public void setSourceActionEffectId(String sourceActionEffectId) {
        this.sourceActionEffectId = sourceActionEffectId;
    }

    /** 读取变更后的控制状态。 */
    public String getStatus() {
        return status;
    }

    /** 设置变更后的控制状态。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 读取生效时间，UTC。 */
    public String getEffectiveFrom() {
        return effectiveFrom;
    }

    /** 设置生效时间，UTC。 */
    public void setEffectiveFrom(String effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    /** 读取失效时间，UTC。 */
    public String getEffectiveTo() {
        return effectiveTo;
    }

    /** 设置失效时间，UTC。 */
    public void setEffectiveTo(String effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    /** 读取最低保证金金额，十进制字符串。 */
    public String getRequiredAmount() {
        return requiredAmount;
    }

    /** 设置最低保证金金额，十进制字符串。 */
    public void setRequiredAmount(String requiredAmount) {
        this.requiredAmount = requiredAmount;
    }

    /** 读取最低保证金币种。 */
    public String getRequiredCurrency() {
        return requiredCurrency;
    }

    /** 设置最低保证金币种。 */
    public void setRequiredCurrency(String requiredCurrency) {
        this.requiredCurrency = requiredCurrency;
    }

    /** 读取控制内容哈希。 */
    public String getContentHash() {
        return contentHash;
    }

    /** 设置控制内容哈希。 */
    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    /** 读取状态变更 ID。 */
    public String getChangeId() {
        return changeId;
    }

    /** 设置状态变更 ID。 */
    public void setChangeId(String changeId) {
        this.changeId = changeId;
    }

    /** 读取原状态。 */
    public String getFromStatus() {
        return fromStatus;
    }

    /** 设置原状态。 */
    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    /** 读取新状态。 */
    public String getToStatus() {
        return toStatus;
    }

    /** 设置新状态。 */
    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    /** 读取变更原因代码。 */
    public String getChangeReasonCode() {
        return changeReasonCode;
    }

    /** 设置变更原因代码。 */
    public void setChangeReasonCode(String changeReasonCode) {
        this.changeReasonCode = changeReasonCode;
    }

    /** 读取触发类型。 */
    public String getTriggerType() {
        return triggerType;
    }

    /** 设置触发类型。 */
    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    /** 读取触发标识。 */
    public String getTriggerId() {
        return triggerId;
    }

    /** 设置触发标识。 */
    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId;
    }

    /** 读取操作人。 */
    public String getActorId() {
        return actorId;
    }

    /** 设置操作人。 */
    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    /** 读取聚合版本。 */
    public long getAggregateVersion() {
        return aggregateVersion;
    }

    /** 设置聚合版本。 */
    public void setAggregateVersion(long aggregateVersion) {
        this.aggregateVersion = aggregateVersion;
    }

    /** 读取变更发生时间。 */
    public String getOccurredAt() {
        return occurredAt;
    }

    /** 设置变更发生时间。 */
    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }
}
