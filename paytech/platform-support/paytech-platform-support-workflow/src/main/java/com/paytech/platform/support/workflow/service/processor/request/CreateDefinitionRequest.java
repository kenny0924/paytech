package com.paytech.platform.support.workflow.service.processor.request;

import jakarta.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建流程定义的内部请求。 */
@Getter
@AllArgsConstructor
public class CreateDefinitionRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话。 */
    @NotBlank
    private final String session;

    /** 流程编码。 */
    @NotBlank
    private final String definitionCode;

    /** 流程名称。 */
    @NotBlank
    private final String definitionName;

    /** 说明；可空。 */
    private final String description;
}
