package com.paytech.platform.support.delivery.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 轮换签名密钥；新密钥已在事务外保存到 Vault。 */
@Getter
@AllArgsConstructor
public class RotateWebhookSecretCommand {

    /** 端点 ID。 */
    private final long endpointId;

    /** 新密钥的 Vault 引用。 */
    private final String secretVaultToken;

    /** 旧密钥签名截止时间：宽限轮换为 now 加宽限时长，立即作废为 now。 */
    private final Instant previousValidUntil;

    /** 写入时间。 */
    private final Instant now;
}
