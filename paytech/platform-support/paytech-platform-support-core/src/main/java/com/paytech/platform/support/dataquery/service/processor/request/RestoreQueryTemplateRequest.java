package com.paytech.platform.support.dataquery.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 以历史版本内容生成新版本的内部请求。 */
@Getter
@AllArgsConstructor
public class RestoreQueryTemplateRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 模板 ID。 */
    @Positive
    private final long templateId;

    /** 调用方读取的模板原版本。 */
    @Positive
    private final int expectedVersion;

    /** 被恢复的版本号。 */
    @Positive
    private final int versionNo;

    /** 变更说明，可为 null。 */
    @Size(max = 512)
    private final String changeNote;
}
