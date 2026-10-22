package com.paytech.client.console.bff.service.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 开始上传入驻材料：只声明文件本身，不接受申请、主体、账号或存储目标。 */
@Getter
@Setter
public class OnboardingUploadRequestBody {

    /**
     * 展示文件名
     * <li>最多 255 个 Unicode 字符；Platform 清除路径与控制字符后不能为空、"." 或 ".."。</li>
     */
    @NotBlank
    private String filename;

    /**
     * 声明的规范 MIME
     * <li>小写 type/subtype；实际字节在完成时由 Platform 独立识别。</li>
     */
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*")
    private String mimeType;

    /**
     * 声明字节数
     * <li>无前导零的正整数十进制字符串。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String sizeBytes;

    /** 不记录可能包含个人信息的文件名。 */
    @Override
    public String toString() {
        return "OnboardingUploadRequestBody[REDACTED]";
    }
}
