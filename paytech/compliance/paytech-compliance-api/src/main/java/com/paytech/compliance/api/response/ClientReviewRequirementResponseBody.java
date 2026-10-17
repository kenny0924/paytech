package com.paytech.risk.api.compliance.response;

import java.util.List;

/** 一项材料要求。 */
public class ClientReviewRequirementResponseBody {

    /** 任务材料要求 ID。 */
    private String requirementId;

    /** 要求代码。 */
    private String requirementCode;

    /** 材料类型。 */
    private String documentType;

    /** 是否基础必需。 */
    private Boolean required;

    /** 要求状态。 */
    private String status;

    /** 证据。 */
    private List<ClientReviewEvidenceResponseBody> evidence;

    /** 读取任务材料要求 ID。 */
    public String getRequirementId() {
        return requirementId;
    }

    /** 设置任务材料要求 ID。 */
    public void setRequirementId(String requirementId) {
        this.requirementId = requirementId;
    }

    /** 读取要求代码。 */
    public String getRequirementCode() {
        return requirementCode;
    }

    /** 设置要求代码。 */
    public void setRequirementCode(String requirementCode) {
        this.requirementCode = requirementCode;
    }

    /** 读取材料类型。 */
    public String getDocumentType() {
        return documentType;
    }

    /** 设置材料类型。 */
    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    /** 读取是否基础必需。 */
    public Boolean getRequired() {
        return required;
    }

    /** 设置是否基础必需。 */
    public void setRequired(Boolean required) {
        this.required = required;
    }

    /** 读取要求状态。 */
    public String getStatus() {
        return status;
    }

    /** 设置要求状态。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 读取证据。 */
    public List<ClientReviewEvidenceResponseBody> getEvidence() {
        return evidence;
    }

    /** 设置证据。 */
    public void setEvidence(List<ClientReviewEvidenceResponseBody> evidence) {
        this.evidence = evidence;
    }
}
