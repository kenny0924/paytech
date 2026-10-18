package com.paytech.platform.support.delivery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 一次 HTTP 投递尝试的状态；遗留 PROCESSING 表示发送中断。 */
@Getter
@RequiredArgsConstructor
public enum WebhookAttemptStatus implements CodeEnum {

    PROCESSING("PROCESSING"),

    SUCCEEDED("SUCCEEDED"),

    FAILED("FAILED");

    private final String code;
}
