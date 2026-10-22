package com.paytech.platform.support.dataquery.service.processor.request;

import com.paytech.platform.support.dataquery.domain.QueryTemplateDraft;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 修改模板（保存为新版本）的内部请求。 */
@Getter
@AllArgsConstructor
public class UpdateQueryTemplateRequest {

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

    /** 模板名称，已去除首尾空白。 */
    @NotBlank
    @Size(max = 128)
    private final String templateName;

    /** 说明，可为 null。 */
    @Size(max = 512)
    private final String description;

    /** 模板内容。 */
    @NotNull
    private final QueryTemplateDraft draft;

    /** 变更说明，可为 null。 */
    @Size(max = 512)
    private final String changeNote;
}
