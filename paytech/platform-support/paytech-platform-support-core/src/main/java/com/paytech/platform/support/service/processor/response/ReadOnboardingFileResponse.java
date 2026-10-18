package com.paytech.platform.support.service.processor.response;

import java.util.List;

import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.OnboardingFileAction;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 入驻文件当前元数据；不包含摘要、存储坐标或临时 URL。 */
@Getter
@AllArgsConstructor
public class ReadOnboardingFileResponse {

    /** 文件 ID。 */
    private final long fileId;

    /** 上传时固定的展示名。 */
    private final String displayName;

    /** 实际字节数。 */
    private final long sizeBytes;

    /** 服务端识别的媒体类型。 */
    private final String mimeType;

    /** 当前扫描状态。 */
    private final FileScanStatus scanStatus;

    /** 当前对象状态。 */
    private final FileObjectStatus objectStatus;

    /** 当前允许的动作；执行时仍重新授权。 */
    private final List<OnboardingFileAction> allowedActions;

    /** 展示名可能含个人信息。 */
    @Override
    public String toString() {
        return "ReadOnboardingFileResponse[" + fileId + "]";
    }
}
