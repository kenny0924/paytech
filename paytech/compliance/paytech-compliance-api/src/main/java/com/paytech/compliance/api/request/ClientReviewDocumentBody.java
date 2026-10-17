package com.paytech.risk.api.compliance.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 提交时关联的一份材料；Risk 只登记引用，文件由 Platform 持有。 */
public class ClientReviewDocumentBody {

    /** CCT 材料引用 ID。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String documentId;

    /** 材料所属主体 Party ID。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String subjectPartyId;

    /** 材料要求代码。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String requirementCode;

    /** 材料类型代码。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String documentType;

    /** Platform 文件 ID。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String fileId;

    /** Platform 确认的文件 SHA-256 小写十六进制。 */
    @NotBlank
    @Pattern(regexp = "^[0-9a-f]{64}$")
    private String contentHash;

    /** 读取CCT 材料引用 ID。 */
    public String getDocumentId() {
        return documentId;
    }

    /** 设置CCT 材料引用 ID。 */
    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    /** 读取材料所属主体 Party ID。 */
    public String getSubjectPartyId() {
        return subjectPartyId;
    }

    /** 设置材料所属主体 Party ID。 */
    public void setSubjectPartyId(String subjectPartyId) {
        this.subjectPartyId = subjectPartyId;
    }

    /** 读取材料要求代码。 */
    public String getRequirementCode() {
        return requirementCode;
    }

    /** 设置材料要求代码。 */
    public void setRequirementCode(String requirementCode) {
        this.requirementCode = requirementCode;
    }

    /** 读取材料类型代码。 */
    public String getDocumentType() {
        return documentType;
    }

    /** 设置材料类型代码。 */
    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    /** 读取Platform 文件 ID。 */
    public String getFileId() {
        return fileId;
    }

    /** 设置Platform 文件 ID。 */
    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    /** 读取Platform 确认的文件 SHA-256 小写十六进制。 */
    public String getContentHash() {
        return contentHash;
    }

    /** 设置Platform 确认的文件 SHA-256 小写十六进制。 */
    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }
}
