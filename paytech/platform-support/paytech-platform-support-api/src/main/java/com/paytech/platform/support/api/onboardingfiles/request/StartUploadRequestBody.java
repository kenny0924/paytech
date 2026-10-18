package com.paytech.platform.support.api.onboardingfiles.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 开始一次主体绑定的上传，不接受客户端指定 Tenant、存储坐标或扫描策略。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StartUploadRequestBody {

    /**
     * 原申请 ID
     * <li>无前导零的正整数十进制字符串。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String applicationId;

    /**
     * 材料主体 Party ID
     * <li>已由 CCT 授权的主主体或相关人，无前导零的正整数十进制字符串。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String subjectPartyId;

    /**
     * 展示文件名
     * <li>最多 255 个 Unicode 字符；服务器清除路径与控制字符后不能为空、"." 或 ".."。</li>
     */
    @NotBlank
    private String filename;

    /**
     * 声明的规范 MIME
     * <li>实际字节在完成时独立验证。</li>
     */
    @NotBlank
    @Size(max = 128)
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
        return "StartUploadRequestBody[REDACTED]";
    }
}
