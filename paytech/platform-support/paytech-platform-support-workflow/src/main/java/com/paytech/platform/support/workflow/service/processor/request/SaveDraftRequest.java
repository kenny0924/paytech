package com.paytech.platform.support.workflow.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 保存草稿的内部请求。 */
@Getter
@AllArgsConstructor
public class SaveDraftRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话。 */
    @NotBlank
    private final String session;

    /** 定义 ID。 */
    @NotNull
    private final Long definitionId;

    /** 流程名称。 */
    @NotBlank
    private final String definitionName;

    /** 说明；可空。 */
    private final String description;

    /** 流程图 JSON 文本。 */
    @NotBlank
    private final String graphJson;

    /** 启动表单 JSON 文本。 */
    @NotBlank
    private final String startFormJson;

    /** 定义原版本。 */
    @NotNull
    private final Integer expectedVersion;
}
