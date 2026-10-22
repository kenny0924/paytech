package com.paytech.platform.support.workflow.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 终止实例的内部请求。 */
@Getter
@AllArgsConstructor
public class TerminateInstanceRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话。 */
    @NotBlank
    private final String session;

    /** 实例 ID。 */
    @NotNull
    private final Long instanceId;

    /** 终止原因。 */
    @NotBlank
    private final String reason;
}
