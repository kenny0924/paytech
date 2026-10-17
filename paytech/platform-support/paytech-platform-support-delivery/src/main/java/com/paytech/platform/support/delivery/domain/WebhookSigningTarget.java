package com.paytech.platform.support.delivery.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 测试发送所需的端点地址与当前可签名密钥引用；Vault Token 只用于事务外 Reveal。 */
@Getter
@AllArgsConstructor
public class WebhookSigningTarget {

    /** 端点地址。 */
    private final String url;

    /** ACTIVE 在前、宽限密钥在后的 Vault 引用。 */
    private final List<String> secretVaultTokens;
}
