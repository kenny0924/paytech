package com.paytech.platform.support.delivery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 一次发送提交的确认状态。 */
@Getter
@RequiredArgsConstructor
public enum NotificationAttemptStatus implements CodeEnum {

    PROCESSING("PROCESSING"),

    ACCEPTED("ACCEPTED"),

    FAILED("FAILED"),

    UNKNOWN("UNKNOWN");

    private final String code;
}
