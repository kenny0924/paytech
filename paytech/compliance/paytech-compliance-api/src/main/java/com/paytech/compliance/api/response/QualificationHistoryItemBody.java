package com.paytech.compliance.api.response;

/** 一次资格状态变更及引起它的决定。 */
public class QualificationHistoryItemBody {

    /**
     * 状态变更 ID
     * <li>十进制字符串。</li>
     */
    private String changeId;

    /**
     * 变更后的资格版本
     * <li>十进制字符串。</li>
     */
    private String aggregateVersion;

    /**
     * 原状态
     * <li>首次建立资格时为空。</li>
     */
    private String fromStatus;

    /** 新状态 */
    private String toStatus;

    /**
     * 触发类型
     * <li>OPERATIONS_REVIEW（入驻审核）、OPERATIONS_MANUAL（手工登记或撤销）、BACKFILL（存量回填）。</li>
     */
    private String triggerType;

    /**
     * 变更原因代码
     * <li>可空。</li>
     */
    private String reasonCode;

    /**
     * 操作人 Account ID
     * <li>十进制字符串；回填为空。</li>
     */
    private String actorId;

    /**
     * 发生时间
     * <li>UTC 六位微秒。</li>
     */
    private String occurredAt;

    /**
     * 决定 ID
     * <li>十进制字符串。</li>
     */
    private String decisionId;

    /**
     * 决定来源
     * <li>REVIEWER、MANUAL 等。</li>
     */
    private String decisionSource;

    /**
     * 决定结论
     * <li>APPROVED、REJECTED、REVOKED。</li>
     */
    private String outcome;

    /**
     * 决定人 Account ID
     * <li>十进制字符串；可空。</li>
     */
    private String decidedBy;

    /**
     * 依据说明
     * <li>只有 MANUAL 决定有值。</li>
     */
    private String basisNote;

    /**
     * 决定的有效开始时间
     * <li>UTC 六位微秒。</li>
     */
    private String validFrom;

    /**
     * 决定的有效结束时间
     * <li>UTC 六位微秒；可空。</li>
     */
    private String validTo;

    /** 读取状态变更 ID。 */
    public String getChangeId() {
        return changeId;
    }

    /** 设置状态变更 ID。 */
    public void setChangeId(String changeId) {
        this.changeId = changeId;
    }

    /** 读取变更后的资格版本。 */
    public String getAggregateVersion() {
        return aggregateVersion;
    }

    /** 设置变更后的资格版本。 */
    public void setAggregateVersion(String aggregateVersion) {
        this.aggregateVersion = aggregateVersion;
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

    /** 读取触发类型。 */
    public String getTriggerType() {
        return triggerType;
    }

    /** 设置触发类型。 */
    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    /** 读取变更原因代码。 */
    public String getReasonCode() {
        return reasonCode;
    }

    /** 设置变更原因代码。 */
    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    /** 读取操作人 Account ID。 */
    public String getActorId() {
        return actorId;
    }

    /** 设置操作人 Account ID。 */
    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    /** 读取发生时间。 */
    public String getOccurredAt() {
        return occurredAt;
    }

    /** 设置发生时间。 */
    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }

    /** 读取决定 ID。 */
    public String getDecisionId() {
        return decisionId;
    }

    /** 设置决定 ID。 */
    public void setDecisionId(String decisionId) {
        this.decisionId = decisionId;
    }

    /** 读取决定来源。 */
    public String getDecisionSource() {
        return decisionSource;
    }

    /** 设置决定来源。 */
    public void setDecisionSource(String decisionSource) {
        this.decisionSource = decisionSource;
    }

    /** 读取决定结论。 */
    public String getOutcome() {
        return outcome;
    }

    /** 设置决定结论。 */
    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    /** 读取决定人 Account ID。 */
    public String getDecidedBy() {
        return decidedBy;
    }

    /** 设置决定人 Account ID。 */
    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    /** 读取依据说明。 */
    public String getBasisNote() {
        return basisNote;
    }

    /** 设置依据说明。 */
    public void setBasisNote(String basisNote) {
        this.basisNote = basisNote;
    }

    /** 读取决定的有效开始时间。 */
    public String getValidFrom() {
        return validFrom;
    }

    /** 设置决定的有效开始时间。 */
    public void setValidFrom(String validFrom) {
        this.validFrom = validFrom;
    }

    /** 读取决定的有效结束时间。 */
    public String getValidTo() {
        return validTo;
    }

    /** 设置决定的有效结束时间。 */
    public void setValidTo(String validTo) {
        this.validTo = validTo;
    }
}
