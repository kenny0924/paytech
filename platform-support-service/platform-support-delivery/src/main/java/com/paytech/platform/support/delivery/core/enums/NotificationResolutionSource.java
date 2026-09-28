package com.paytech.platform.support.delivery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 供应商结果确认来源。 */
@Getter
@RequiredArgsConstructor
public enum NotificationResolutionSource implements CodeEnum {

    SEND_RESPONSE("SEND_RESPONSE"),

    PROVIDER_QUERY("PROVIDER_QUERY");

    private final String code;
}
