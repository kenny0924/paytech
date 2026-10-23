package com.paytech.operations.console.bff.service.model.localization.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 开始上传一份多语言导入文件；原因码、类型与上传人由 BFF 固定，页面只声明用途 */
@Getter
@Setter
public class StartLocalizationUploadRequestBody {
    /**
     * 上传用途
     * <li>{@code LocalizationUploadPurpose} Code：KEY_IMPORT（key 导入）、TRANSLATION_IMPORT（翻译导入）。</li>
     */
    @NotBlank(message = "上传用途必填")
    @Pattern(regexp = "KEY_IMPORT|TRANSLATION_IMPORT", message = "上传用途只能为 KEY_IMPORT 或 TRANSLATION_IMPORT")
    private String purpose;

    /**
     * 文件名
     * <li>必须以 .json 结尾（不区分大小写），最多 255 个字符。</li>
     */
    @NotBlank(message = "文件名必填")
    @Size(max = 255, message = "文件名最多 255 个字符")
    @Pattern(regexp = "(?s).*\\.[jJ][sS][oO][nN]", message = "只接受 .json 文件")
    private String fileName;

    /**
     * 文件字节数
     * <li>无前导零的正整数十进制字符串；大小上限由 Platform 文件策略与导入接口判定。</li>
     */
    @NotBlank(message = "文件大小必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "文件大小必须为正整数")
    private String sizeBytes;
}
