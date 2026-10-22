package com.paytech.platform.support.workflow.service.processor.request;

import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建动作模板的内部请求。 */
@Getter
@AllArgsConstructor
public class CreateActionTemplateRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话。 */
    @NotBlank
    private final String session;

    /** 模板编码。 */
    @NotBlank
    private final String templateCode;

    /** 模板名称。 */
    @NotBlank
    private final String templateName;

    /** 动作类型。 */
    @NotNull
    private final WorkflowActionType actionType;

    /** 说明；可空。 */
    private final String description;

    /** 配置 JSON 文本。 */
    @NotBlank
    private final String configJson;
}
