package com.paytech.platform.support.workflow.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 发布草稿的内部请求。 */
@Getter
@AllArgsConstructor
public class PublishDefinitionRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话。 */
    @NotBlank
    private final String session;

    /** 定义 ID。 */
    @NotNull
    private final Long definitionId;

    /** 定义原版本。 */
    @NotNull
    private final Integer expectedVersion;
}
