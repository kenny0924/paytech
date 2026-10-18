package com.paytech.platform.support.delivery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Webhook 签名密钥状态；RETIRED 在 valid_until 前仍参与签名。 */
@Getter
@RequiredArgsConstructor
public enum WebhookSecretStatus implements CodeEnum {

    ACTIVE("ACTIVE"),

    RETIRED("RETIRED");

    private final String code;
}
