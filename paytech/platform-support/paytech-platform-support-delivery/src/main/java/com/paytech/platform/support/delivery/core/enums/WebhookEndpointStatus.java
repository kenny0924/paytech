package com.paytech.platform.support.delivery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Webhook 端点状态。 */
@Getter
@RequiredArgsConstructor
public enum WebhookEndpointStatus implements CodeEnum {

    ACTIVE("ACTIVE"),

    DISABLED("DISABLED");

    private final String code;
}
