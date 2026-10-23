package com.paytech.platform.support.api.localization.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 确认一次 key 导入：文件与预览摘要须与预览时一致。 */
@Getter
@Setter
public class ConfirmKeyImportRequestBody {

    /** 预览时使用的文件 ID，十进制字符串。 */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "fileId 必须是十进制字符串 ID")
    private String fileId;

    /** 预览返回的 previewDigest，64 位小写十六进制。 */
    @NotBlank
    @Pattern(regexp = "[0-9a-f]{64}", message = "previewDigest 必须是 64 位小写十六进制")
    private String previewDigest;
}
