package com.paytech.compliance.api.response;

import java.util.List;

/** Client 入驻审核任务；列表只填摘要，详情另含主体、分类与通过门禁。 */
public class ClientReviewCaseResponseBody {

    /** 主审核任务 ID。 */
    private String caseId;

    /** 任务编号。 */
    private String caseNo;

    /** CCT 申请 ID。 */
    private String applicationId;

    /** CCT Client ID。 */
    private String clientId;

    /** 来源提交编号。 */
    private String sourceSubmissionId;

    /** 资格类型。 */
    private String qualificationType;

    /** 主任务状态。 */
    private String status;

    /** 主任务并发版本。 */
    private String version;

    /** 提交时间，UTC ISO-8601。 */
    private String submittedAt;

    /** 结束时间；进行中为空。 */
    private String decidedAt;

    /** 客户可见说明。 */
    private String publicNote;

    /** 退回分区。 */
    private String returnSection;

    /** 主体与材料；列表为空。 */
    private List<ClientReviewSubjectResponseBody> subjects;

    /** 已接收分类；列表为空。 */
    private List<ClientReviewClassificationResponseBody> classifications;

    /** 通过门禁未满足项；列表为空。 */
    private List<String> approvalBlockers;

    /** 读取主审核任务 ID。 */
    public String getCaseId() {
        return caseId;
    }

    /** 设置主审核任务 ID。 */
    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    /** 读取任务编号。 */
    public String getCaseNo() {
        return caseNo;
    }

    /** 设置任务编号。 */
    public void setCaseNo(String caseNo) {
        this.caseNo = caseNo;
    }

    /** 读取CCT 申请 ID。 */
    public String getApplicationId() {
        return applicationId;
    }

    /** 设置CCT 申请 ID。 */
    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    /** 读取CCT Client ID。 */
    public String getClientId() {
        return clientId;
    }

    /** 设置CCT Client ID。 */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /** 读取来源提交编号。 */
    public String getSourceSubmissionId() {
        return sourceSubmissionId;
    }

    /** 设置来源提交编号。 */
    public void setSourceSubmissionId(String sourceSubmissionId) {
        this.sourceSubmissionId = sourceSubmissionId;
    }

    /** 读取资格类型。 */
    public String getQualificationType() {
        return qualificationType;
    }

    /** 设置资格类型。 */
    public void setQualificationType(String qualificationType) {
        this.qualificationType = qualificationType;
    }

    /** 读取主任务状态。 */
    public String getStatus() {
        return status;
    }

    /** 设置主任务状态。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 读取主任务并发版本。 */
    public String getVersion() {
        return version;
    }

    /** 设置主任务并发版本。 */
    public void setVersion(String version) {
        this.version = version;
    }

    /** 读取提交时间，UTC ISO-8601。 */
    public String getSubmittedAt() {
        return submittedAt;
    }

    /** 设置提交时间，UTC ISO-8601。 */
    public void setSubmittedAt(String submittedAt) {
        this.submittedAt = submittedAt;
    }

    /** 读取结束时间；进行中为空。 */
    public String getDecidedAt() {
        return decidedAt;
    }

    /** 设置结束时间；进行中为空。 */
    public void setDecidedAt(String decidedAt) {
        this.decidedAt = decidedAt;
    }

    /** 读取客户可见说明。 */
    public String getPublicNote() {
        return publicNote;
    }

    /** 设置客户可见说明。 */
    public void setPublicNote(String publicNote) {
        this.publicNote = publicNote;
    }

    /** 读取退回分区。 */
    public String getReturnSection() {
        return returnSection;
    }

    /** 设置退回分区。 */
    public void setReturnSection(String returnSection) {
        this.returnSection = returnSection;
    }

    /** 读取主体与材料；列表为空。 */
    public List<ClientReviewSubjectResponseBody> getSubjects() {
        return subjects;
    }

    /** 设置主体与材料；列表为空。 */
    public void setSubjects(List<ClientReviewSubjectResponseBody> subjects) {
        this.subjects = subjects;
    }

    /** 读取已接收分类；列表为空。 */
    public List<ClientReviewClassificationResponseBody> getClassifications() {
        return classifications;
    }

    /** 设置已接收分类；列表为空。 */
    public void setClassifications(List<ClientReviewClassificationResponseBody> classifications) {
        this.classifications = classifications;
    }

    /** 读取通过门禁未满足项；列表为空。 */
    public List<String> getApprovalBlockers() {
        return approvalBlockers;
    }

    /** 设置通过门禁未满足项；列表为空。 */
    public void setApprovalBlockers(List<String> approvalBlockers) {
        this.approvalBlockers = approvalBlockers;
    }
}
