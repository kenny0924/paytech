package com.paytech.client.console.bff.service.model.response;

import java.util.List;

import com.paytech.client.console.bff.domain.enums.OnboardingFileAction;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 入驻文件元数据；不包含摘要、来源系统、上传人、存储坐标或临时 URL。 */
@Getter
@AllArgsConstructor
public class OnboardingFileResponseBody {

    /** 文件 ID，十进制字符串 */
    private final String fileId;

    /**
     * 文件地址 /files/v1/{fileId}
     * <li>关联材料（C12）时原样提交。</li>
     */
    private final String fileAddress;

    /** 上传时固定的展示名 */
    private final String displayName;

    /** 实际字节数，十进制字符串 */
    private final String sizeBytes;

    /** 服务端识别的媒体类型 */
    private final String mimeType;

    /** 扫描状态：FileScanStatus 稳定代码 */
    private final String scanStatus;

    /** 对象状态：FileObjectStatus 稳定代码 */
    private final String objectStatus;

    /**
     * 当前允许的动作
     * <li>OnboardingFileAction 稳定代码；扫描可用且对象有效时为 DOWNLOAD，否则为空。</li>
     */
    private final List<String> allowedActions;

    /**
     * 从 Platform 元数据裁剪页面字段
     * <li>只保留页面需要的字段；摘要、来源系统、上传原因与上传人不返回浏览器。</li>
     *
     * @param file 已校验文件身份与状态码的 Platform 元数据
     * @return 页面文件模型
     */
    public static OnboardingFileResponseBody of(StoredFileResponseBody file) {
        boolean downloadable = FileScanStatus.AVAILABLE.getCode().equals(file.getScanStatus())
            && FileObjectStatus.ACTIVE.getCode().equals(file.getObjectStatus());
        return new OnboardingFileResponseBody(file.getFileId(), file.getFileAddress(), file.getDisplayName(),
            file.getSizeBytes(), file.getMimeType(), file.getScanStatus(), file.getObjectStatus(),
            downloadable ? List.of(OnboardingFileAction.DOWNLOAD.getCode()) : List.of());
    }

    /** 展示名可能含个人信息。 */
    @Override
    public String toString() {
        return "OnboardingFileResponseBody[REDACTED]";
    }
}
