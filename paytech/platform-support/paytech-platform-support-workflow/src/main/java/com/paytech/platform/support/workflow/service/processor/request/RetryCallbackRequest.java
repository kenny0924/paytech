package com.paytech.platform.support.workflow.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 手动重试通知回调的内部请求。 */
@Getter
@AllArgsConstructor
public class RetryCallbackRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话。 */
    @NotBlank
    private final String session;

    /** 通知回调 ID。 */
    @NotNull
    private final Long callbackId;
}
