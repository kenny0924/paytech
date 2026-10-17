package com.paytech.compliance.api.event;

import java.util.List;

/**
 * ClientReviewDecided Schema v1 的 Payload（事件目录 paytech-compliance-api/event/ClientReviewDecided/v1）
 * <li>与旧 topic_risk_client_review / client_review.decided.v1 报文的 payload 同字段、同顺序（{@link #FIELDS}）；client-master
 * 第 08 步切到 compliance.events 前两者并行发布。</li>
 * <li>聚合为审核主任务：aggregateType={@link #AGGREGATE_TYPE}、aggregateId=caseId、aggregateVersion=caseVersion（任务版本可能跳号，
 * 故 strictOrder=false）。</li>
 * <li>ID 一律十进制字符串，时间为 UTC ISO-8601；本类只描述合同，不依赖 JSON 库。</li>
 */
public class ClientReviewDecidedPayload {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "ClientReviewDecided";

    /** Payload Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 聚合类型：尽调审核主任务（与旧报文 sourceType 一致）。 */
    public static final String AGGREGATE_TYPE = "DD_CASE";

    /** Payload 字段及顺序。 */
    public static final List<String> FIELDS = List.of("caseId", "caseVersion", "applicationId", "submissionId", "outcome",
        "returnSection", "publicNote", "decidedAt", "decisionId", "decidedBy");

    /** 审核主任务 ID。 */
    private String caseId;

    /** 结束后的主任务版本。 */
    private long caseVersion;

    /** CCT 申请 ID。 */
    private String applicationId;

    /** 来源提交 ID。 */
    private String submissionId;

    /** 审核结果：RETURNED、APPROVED 或 REJECTED。 */
    private String outcome;

    /** 退回分区；其他结果为空。 */
    private String returnSection;

    /** 客户可见说明；未填写为空。 */
    private String publicNote;

    /** 决定时间，UTC。 */
    private String decidedAt;

    /** 资格决定 ID；无决定记录为空。 */
    private String decisionId;

    /** 决定人；系统决定为空。 */
    private String decidedBy;

    /** 读取审核主任务 ID。 */
    public String getCaseId() {
        return caseId;
    }

    /** 设置审核主任务 ID。 */
    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    /** 读取结束后的主任务版本。 */
    public long getCaseVersion() {
        return caseVersion;
    }

    /** 设置结束后的主任务版本。 */
    public void setCaseVersion(long caseVersion) {
        this.caseVersion = caseVersion;
    }

    /** 读取 CCT 申请 ID。 */
    public String getApplicationId() {
        return applicationId;
    }

    /** 设置 CCT 申请 ID。 */
    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    /** 读取来源提交 ID。 */
    public String getSubmissionId() {
        return submissionId;
    }

    /** 设置来源提交 ID。 */
    public void setSubmissionId(String submissionId) {
        this.submissionId = submissionId;
    }

    /** 读取审核结果。 */
    public String getOutcome() {
        return outcome;
    }

    /** 设置审核结果。 */
    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    /** 读取退回分区。 */
    public String getReturnSection() {
        return returnSection;
    }

    /** 设置退回分区。 */
    public void setReturnSection(String returnSection) {
        this.returnSection = returnSection;
    }

    /** 读取客户可见说明。 */
    public String getPublicNote() {
        return publicNote;
    }

    /** 设置客户可见说明。 */
    public void setPublicNote(String publicNote) {
        this.publicNote = publicNote;
    }

    /** 读取决定时间。 */
    public String getDecidedAt() {
        return decidedAt;
    }

    /** 设置决定时间。 */
    public void setDecidedAt(String decidedAt) {
        this.decidedAt = decidedAt;
    }

    /** 读取资格决定 ID。 */
    public String getDecisionId() {
        return decisionId;
    }

    /** 设置资格决定 ID。 */
    public void setDecisionId(String decisionId) {
        this.decisionId = decisionId;
    }

    /** 读取决定人。 */
    public String getDecidedBy() {
        return decidedBy;
    }

    /** 设置决定人。 */
    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }
}
