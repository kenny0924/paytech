package com.paytech.operations.console.bff.service.model.fileupload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 开始上传一个公开文件；上传原因、访问级别与上传人由 BFF 固定，页面不能指定 */
@Getter
@Setter
public class StartPublicFileUploadRequestBody {
    /**
     * 文件名
     * <li>最多 255 个字符；Platform 去除路径与控制字符后作为展示名。</li>
     */
    @NotBlank(message = "文件名必填")
    @Size(max = 255, message = "文件名最多 255 个字符")
    private String filename;

    /**
     * 浏览器声明的媒体类型
     * <li>规范小写 MIME；是否允许由 Platform 写入规则的检查策略判定，页面预校验只用于提示。</li>
     */
    @NotBlank(message = "mimeType 必填")
    @Size(max = 128, message = "mimeType 最多 128 个字符")
    @Pattern(regexp = "[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*", message = "mimeType 必须为规范媒体类型")
    private String mimeType;

    /**
     * 文件字节数
     * <li>无前导零的正整数十进制字符串；上限由 Platform 检查策略判定。</li>
     */
    @NotBlank(message = "文件大小必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "文件大小必须为正整数")
    private String sizeBytes;
}
