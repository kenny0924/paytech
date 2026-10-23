package com.paytech.platform.support.api.localization.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 预览一次 key 导入：文件须已经文件服务以原因码 I18N_KEY_IMPORT 上传并通过检查。 */
@Getter
@Setter
public class PreviewKeyImportRequestBody {

    /** 已上传文件 ID，十进制字符串。 */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "fileId 必须是十进制字符串 ID")
    private String fileId;
}
