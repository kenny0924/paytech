package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 提交时关联的一份材料；compliance 只登记文件服务地址，文件由 Platform 持有。 */
@Getter
@Setter
public class ClientReviewDocumentBody {

    /** client-master 材料引用 ID。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String documentId;

    /** 材料所属主体 Party ID。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String subjectPartyId;

    /** 材料要求代码，必须是该主体所解析程序中的材料槽。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String requirementCode;

    /** 材料类型代码，必须在材料槽允许的类型内。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String documentType;

    /**
     * 文件服务地址
     * <li>格式 /files/v1/{fileId}（FileAddress）。</li>
     */
    @NotBlank
    @Pattern(regexp = "^/files/v1/[1-9][0-9]{0,18}$")
    private String fileAddress;

    /** Platform 确认的文件 SHA-256 小写十六进制。 */
    @NotBlank
    @Pattern(regexp = "^[0-9a-f]{64}$")
    private String contentHash;

    /** 材料签发日期 YYYY-MM-DD，可空。 */
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private String issueDate;

    /** 材料到期日期 YYYY-MM-DD，可空。 */
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private String expiryDate;
}
