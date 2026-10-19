package com.paytech.commercial.api.agreement.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 签署证据或终止通知等单份合同文件。 */
@Getter
@Setter
public class AgreementFileEntry {
    /** Platform Support 文件 ID */
    @NotBlank(message = "fileId 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "fileId 必须是规范正十进制 ID")
    private String fileId;

    /** 文件 SHA-256，64 位小写十六进制 */
    @NotBlank(message = "contentHash 必填")
    @Pattern(regexp = "[0-9a-f]{64}", message = "contentHash 必须是 64 位小写十六进制 SHA-256")
    private String contentHash;
}
