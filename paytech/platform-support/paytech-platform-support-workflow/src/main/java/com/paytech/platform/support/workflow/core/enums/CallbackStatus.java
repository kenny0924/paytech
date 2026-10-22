package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 通知与回调的投递状态，与 t_workflow_callback.status 一致。 */
@Getter
@RequiredArgsConstructor
public enum CallbackStatus implements CodeEnum {

    /** 待发送或重试中。 */
    PENDING("PENDING"),

    /** 已送达。 */
    SENT("SENT"),

    /** 超过重试上限，可人工重试。 */
    FAILED("FAILED");

    private final String code;
}
