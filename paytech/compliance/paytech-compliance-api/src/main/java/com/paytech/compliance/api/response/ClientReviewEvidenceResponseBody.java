package com.paytech.compliance.api.response;

/** 一份证据引用及核验状态。 */
public class ClientReviewEvidenceResponseBody {

    /** 证据 ID。 */
    private String evidenceId;

    /** Platform 文件 ID。 */
    private String fileId;

    /** RECEIVED、ACCEPTED、REJECTED 或 SUPERSEDED。 */
    private String status;

    /** 读取证据 ID。 */
    public String getEvidenceId() {
        return evidenceId;
    }

    /** 设置证据 ID。 */
    public void setEvidenceId(String evidenceId) {
        this.evidenceId = evidenceId;
    }

    /** 读取Platform 文件 ID。 */
    public String getFileId() {
        return fileId;
    }

    /** 设置Platform 文件 ID。 */
    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    /** 读取RECEIVED、ACCEPTED、REJECTED 或 SUPERSEDED。 */
    public String getStatus() {
        return status;
    }

    /** 设置RECEIVED、ACCEPTED、REJECTED 或 SUPERSEDED。 */
    public void setStatus(String status) {
        this.status = status;
    }
}
