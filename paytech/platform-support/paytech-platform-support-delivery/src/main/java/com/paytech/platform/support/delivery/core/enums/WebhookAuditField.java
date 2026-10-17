package com.paytech.platform.support.delivery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Webhook 配置审计记录的字段；不记录密钥值或 Vault Token。 */
@Getter
@RequiredArgsConstructor
public enum WebhookAuditField implements CodeEnum {

    /** 端点地址。 */
    ENDPOINT_URL("ENDPOINT_URL"),

    /** 订阅事件类型，逗号分隔。 */
    EVENT_TYPES("EVENT_TYPES"),

    /** 端点状态。 */
    STATUS("STATUS"),

    /** 当前签名密钥序号。 */
    SIGNING_ROTATION("SIGNING_ROTATION");

    private final String code;
}
