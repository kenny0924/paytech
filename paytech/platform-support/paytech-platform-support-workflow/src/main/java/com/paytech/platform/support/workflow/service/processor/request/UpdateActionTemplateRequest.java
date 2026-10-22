package com.paytech.platform.support.workflow.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按原版本修改动作模板的内部请求。 */
@Getter
@AllArgsConstructor
public class UpdateActionTemplateRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话。 */
    @NotBlank
    private final String session;

    /** 模板 ID。 */
    @NotNull
    private final Long templateId;

    /** 模板名称。 */
    @NotBlank
    private final String templateName;

    /** 说明；可空。 */
    private final String description;

    /** 配置 JSON 文本。 */
    @NotBlank
    private final String configJson;

    /** 原版本。 */
    @NotNull
    private final Integer expectedVersion;
}
