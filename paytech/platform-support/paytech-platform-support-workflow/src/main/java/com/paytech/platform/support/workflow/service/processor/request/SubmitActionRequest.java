package com.paytech.platform.support.workflow.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

import tools.jackson.databind.JsonNode;

/** 提交动作的内部请求。 */
@Getter
@AllArgsConstructor
public class SubmitActionRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话。 */
    @NotBlank
    private final String session;

    /** 动作运行 ID。 */
    @NotNull
    private final Long actionRunId;

    /** 提交内容。 */
    @NotNull
    private final JsonNode output;
}
