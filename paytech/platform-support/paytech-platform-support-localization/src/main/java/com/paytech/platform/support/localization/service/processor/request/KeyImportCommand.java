package com.paytech.platform.support.localization.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** key 导入预览或确认的内部请求；预览时 previewDigest 为 null。 */
@Getter
@AllArgsConstructor
public class KeyImportCommand {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 应用编码。 */
    @NotBlank
    private final String applicationCode;

    /** 已上传文件 ID。 */
    @Positive
    private final long fileId;

    /** 预览摘要；预览时为 null。 */
    private final String previewDigest;
}
