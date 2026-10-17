package com.paytech.platform.support.delivery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Webhook 投递状态；SUCCEEDED、DEAD、CANCELLED 为终态。 */
@Getter
@RequiredArgsConstructor
public enum WebhookDeliveryStatus implements CodeEnum {

    CREATED("CREATED"),

    PROCESSING("PROCESSING"),

    RETRY_WAIT("RETRY_WAIT"),

    SUCCEEDED("SUCCEEDED"),

    DEAD("DEAD"),

    CANCELLED("CANCELLED");

    private final String code;

    /** 终态不再推进，补偿任务据此结束。 */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == DEAD || this == CANCELLED;
    }
}
