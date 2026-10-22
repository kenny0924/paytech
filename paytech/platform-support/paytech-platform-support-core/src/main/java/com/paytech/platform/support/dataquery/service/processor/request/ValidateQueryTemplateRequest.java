package com.paytech.platform.support.dataquery.service.processor.request;

import com.paytech.platform.support.dataquery.domain.QueryTemplateDraft;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 草稿校验的内部请求。 */
@Getter
@AllArgsConstructor
public class ValidateQueryTemplateRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 模板内容。 */
    @NotNull
    private final QueryTemplateDraft draft;
}
