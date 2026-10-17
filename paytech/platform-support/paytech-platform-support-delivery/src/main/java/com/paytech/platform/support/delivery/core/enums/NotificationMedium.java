package com.paytech.platform.support.delivery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 通知媒介；本期只允许邮件。 */
@Getter
@RequiredArgsConstructor
public enum NotificationMedium implements CodeEnum {

    EMAIL("EMAIL"),

    SMS("SMS");

    private final String code;
}
