package com.paytech.platform.support.api.onboardingfiles.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 入驻文件当前元数据；不包含摘要、存储坐标或临时 URL。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileResponseBody {

    /** 文件 ID，十进制字符串。 */
    private String fileId;

    /** 上传时固定的展示名。 */
    private String displayName;

    /** 实际字节数，十进制字符串。 */
    private String sizeBytes;

    /** 服务端识别的媒体类型。 */
    private String mimeType;

    /**
     * 当前扫描状态
     * <li>FileScanStatus 稳定代码。</li>
     */
    private String scanStatus;

    /**
     * 当前对象状态
     * <li>FileObjectStatus 稳定代码。</li>
     */
    private String objectStatus;

    /**
     * 当前允许的动作
     * <li>OnboardingFileAction 稳定代码；执行时仍重新授权。</li>
     */
    private List<String> allowedActions;

    /** 展示名可能含个人信息。 */
    @Override
    public String toString() {
        return "FileResponseBody[REDACTED]";
    }
}
