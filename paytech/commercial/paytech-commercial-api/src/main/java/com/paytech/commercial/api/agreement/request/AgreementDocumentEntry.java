package com.paytech.commercial.api.agreement.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 创建合同时登记的合同文件引用（Platform Support 文件与其 SHA-256 成组冻结）。 */
@Getter
@Setter
public class AgreementDocumentEntry {
    /**
     * 文件用途
     * <li>创建时只接受 SIGNING_DOCUMENT（至多一份，提交签署前必须恰好一份）与 ANNEX。</li>
     */
    @NotBlank(message = "documentType 必填")
    @Pattern(regexp = "SIGNING_DOCUMENT|ANNEX", message = "documentType 只能为 SIGNING_DOCUMENT、ANNEX")
    private String documentType;

    /** Platform Support 文件 ID */
    @NotBlank(message = "fileId 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "fileId 必须是规范正十进制 ID")
    private String fileId;

    /** 文件 SHA-256，64 位小写十六进制 */
    @NotBlank(message = "contentHash 必填")
    @Pattern(regexp = "[0-9a-f]{64}", message = "contentHash 必须是 64 位小写十六进制 SHA-256")
    private String contentHash;
}
