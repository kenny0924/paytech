package com.paytech.platform.support.delivery.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 轮换结果。 */
@Getter
@AllArgsConstructor
public class RotateWebhookSecretResult {

    /** 审计用的配置差异。 */
    private final WebhookConfigurationChange change;

    /** 新密钥序号。 */
    private final int rotationNo;

    /** 旧密钥签名截止时间；立即作废时为空。 */
    private final Instant previousValidUntil;
}
