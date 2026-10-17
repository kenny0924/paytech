package com.paytech.risk.api.compliance.response;

/** 审核命令结果；同步本地完成。 */
public class ClientReviewCommandResponseBody {

    /** 主审核任务 ID。 */
    private String caseId;

    /** 命令完成后的主任务状态。 */
    private String status;

    /** 命令是否已被接受。 */
    private Boolean accepted;

    /** 是否仍在等待 Owner 衔接；本地同步完成时为 false。 */
    private Boolean processing;

    /** 读取主审核任务 ID。 */
    public String getCaseId() {
        return caseId;
    }

    /** 设置主审核任务 ID。 */
    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    /** 读取命令完成后的主任务状态。 */
    public String getStatus() {
        return status;
    }

    /** 设置命令完成后的主任务状态。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 读取命令是否已被接受。 */
    public Boolean getAccepted() {
        return accepted;
    }

    /** 设置命令是否已被接受。 */
    public void setAccepted(Boolean accepted) {
        this.accepted = accepted;
    }

    /** 读取是否仍在等待 Owner 衔接；本地同步完成时为 false。 */
    public Boolean getProcessing() {
        return processing;
    }

    /** 设置是否仍在等待 Owner 衔接；本地同步完成时为 false。 */
    public void setProcessing(Boolean processing) {
        this.processing = processing;
    }
}
