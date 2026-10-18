package com.paytech.platform.support.delivery.domain;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建端点及首个签名密钥；地址与订阅已校验，密钥已在事务外保存到 Vault。 */
@Getter
@AllArgsConstructor
public class CreateWebhookEndpointCommand {

    /** 所属 CLIENT 租户。 */
    private final long tenantId;

    /** 已校验的 HTTPS 地址。 */
    private final String url;

    /** 已校验的订阅事件类型。 */
    private final List<String> eventTypes;

    /** 首个密钥的 Vault 引用。 */
    private final String secretVaultToken;

    /** 写入时间。 */
    private final Instant now;
}
