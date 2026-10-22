package com.paytech.platform.support.workflow.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 启停流程定义或动作模板的内部请求。 */
@Getter
@AllArgsConstructor
public class ChangeWorkflowStatusRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话。 */
    @NotBlank
    private final String session;

    /** 定义或模板 ID。 */
    @NotNull
    private final Long targetId;

    /** 目标是否为 ACTIVE。 */
    @NotNull
    private final Boolean active;

    /** 原版本。 */
    @NotNull
    private final Integer expectedVersion;
}
