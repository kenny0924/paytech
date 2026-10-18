package com.paytech.platform.support.delivery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 供应商结果确认来源。 */
@Getter
@RequiredArgsConstructor
public enum NotificationResolutionSource implements CodeEnum {

    SEND_RESPONSE("SEND_RESPONSE"),

    PROVIDER_QUERY("PROVIDER_QUERY"),

    /** 供应商投递事件证明已受理。 */
    PROVIDER_EVENT("PROVIDER_EVENT");

    private final String code;
}
