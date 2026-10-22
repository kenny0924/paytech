package com.paytech.platform.support.workflow.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 转交动作或审批的内部请求。 */
@Getter
@AllArgsConstructor
public class TransferTaskRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话。 */
    @NotBlank
    private final String session;

    /** 动作运行或审批 ID。 */
    @NotNull
    private final Long targetId;

    /** 新处理人。 */
    @NotNull
    private final Long toUserId;

    /** 说明；可空。 */
    private final String reason;
}
