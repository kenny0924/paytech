package com.paytech.risk.api.compliance.response;

/** Risk 已登记的审核任务；同一来源提交重放返回同一任务。 */
public class ClientReviewRegistrationResponseBody {

    /** 来源提交编号，与请求一致。 */
    private String sourceSubmissionId;

    /** 主审核任务 ID，正 BIGINT 十进制字符串。 */
    private String caseId;

    /** 读取来源提交编号，与请求一致。 */
    public String getSourceSubmissionId() {
        return sourceSubmissionId;
    }

    /** 设置来源提交编号，与请求一致。 */
    public void setSourceSubmissionId(String sourceSubmissionId) {
        this.sourceSubmissionId = sourceSubmissionId;
    }

    /** 读取主审核任务 ID，正 BIGINT 十进制字符串。 */
    public String getCaseId() {
        return caseId;
    }

    /** 设置主审核任务 ID，正 BIGINT 十进制字符串。 */
    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }
}
