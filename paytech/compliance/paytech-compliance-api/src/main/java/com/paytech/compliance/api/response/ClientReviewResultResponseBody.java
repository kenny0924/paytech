package com.paytech.risk.api.compliance.response;

/** C05 审核结果投影：客户可见的最近一次审核结果；不含内部理由、核验记录和操作人。 */
public class ClientReviewResultResponseBody {

    /** CCT 申请 ID。 */
    private String applicationId;

    /** 最近一次主审核任务 ID。 */
    private String caseId;

    /** 主审核任务当前状态。 */
    private String status;

    /** 已结束任务的结果：RETURNED、APPROVED 或 REJECTED；进行中为空。 */
    private String outcome;

    /** 退回时客户需要修改的分区：BASIC、BUSINESS、PEOPLE 或 MATERIALS；其他结果为空。 */
    private String returnSection;

    /** 运营填写的客户可见说明；未填写为空。 */
    private String publicNote;

    /** 结束时间，UTC 六位微秒；进行中为空。 */
    private String decidedAt;

    /** 读取CCT 申请 ID。 */
    public String getApplicationId() {
        return applicationId;
    }

    /** 设置CCT 申请 ID。 */
    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    /** 读取最近一次主审核任务 ID。 */
    public String getCaseId() {
        return caseId;
    }

    /** 设置最近一次主审核任务 ID。 */
    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    /** 读取主审核任务当前状态。 */
    public String getStatus() {
        return status;
    }

    /** 设置主审核任务当前状态。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 读取已结束任务的结果：RETURNED、APPROVED 或 REJECTED；进行中为空。 */
    public String getOutcome() {
        return outcome;
    }

    /** 设置已结束任务的结果：RETURNED、APPROVED 或 REJECTED；进行中为空。 */
    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    /** 读取退回时客户需要修改的分区：BASIC、BUSINESS、PEOPLE 或 MATERIALS；其他结果为空。 */
    public String getReturnSection() {
        return returnSection;
    }

    /** 设置退回时客户需要修改的分区：BASIC、BUSINESS、PEOPLE 或 MATERIALS；其他结果为空。 */
    public void setReturnSection(String returnSection) {
        this.returnSection = returnSection;
    }

    /** 读取运营填写的客户可见说明；未填写为空。 */
    public String getPublicNote() {
        return publicNote;
    }

    /** 设置运营填写的客户可见说明；未填写为空。 */
    public void setPublicNote(String publicNote) {
        this.publicNote = publicNote;
    }

    /** 读取结束时间，UTC 六位微秒；进行中为空。 */
    public String getDecidedAt() {
        return decidedAt;
    }

    /** 设置结束时间，UTC 六位微秒；进行中为空。 */
    public void setDecidedAt(String decidedAt) {
        this.decidedAt = decidedAt;
    }
}
