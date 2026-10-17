package com.paytech.platform.support.delivery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 收件人独立投递状态。 */
@Getter
@RequiredArgsConstructor
public enum NotificationDeliveryStatus implements CodeEnum {

    PENDING("PENDING"),

    PROCESSING("PROCESSING"),

    RETRY_WAIT("RETRY_WAIT"),

    SENT("SENT"),

    DELIVERED("DELIVERED"),

    FAILED("FAILED"),

    UNKNOWN("UNKNOWN");

    private final String code;
}
