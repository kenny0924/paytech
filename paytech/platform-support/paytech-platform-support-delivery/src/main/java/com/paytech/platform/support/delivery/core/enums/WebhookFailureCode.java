package com.paytech.platform.support.delivery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 投递尝试的失败原因；不保存响应体或异常原文。 */
@Getter
@RequiredArgsConstructor
public enum WebhookFailureCode implements CodeEnum {

    HTTP_STATUS("HTTP_STATUS"),

    TIMEOUT("TIMEOUT"),

    CONNECT_FAILED("CONNECT_FAILED"),

    TLS_FAILED("TLS_FAILED"),

    DNS_FAILED("DNS_FAILED"),

    BLOCKED_ADDRESS("BLOCKED_ADDRESS"),

    INTERRUPTED("INTERRUPTED");

    private final String code;
}
