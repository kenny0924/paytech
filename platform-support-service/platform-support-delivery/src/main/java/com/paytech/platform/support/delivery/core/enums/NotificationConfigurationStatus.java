package com.paytech.platform.support.delivery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 当前配置启停状态。 */
@Getter
@RequiredArgsConstructor
public enum NotificationConfigurationStatus implements CodeEnum {

    ACTIVE("ACTIVE"),

    DISABLED("DISABLED");

    private final String code;
}
