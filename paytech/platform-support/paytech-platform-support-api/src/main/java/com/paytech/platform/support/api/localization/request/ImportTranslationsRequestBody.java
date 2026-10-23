package com.paytech.platform.support.api.localization.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 导入一个语言的翻译文件：文件须已经文件服务以原因码 I18N_TRANSLATION_IMPORT 上传并通过检查。 */
@Getter
@Setter
public class ImportTranslationsRequestBody {

    /** 已上传文件 ID，十进制字符串。 */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "fileId 必须是十进制字符串 ID")
    private String fileId;

    /** 目标语言代码；须与文件中的 locale 一致，ENABLED 且不是 en。 */
    @NotBlank
    @Size(max = 35)
    private String locale;
}
