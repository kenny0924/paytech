package com.paytech.platform.support.service.processor.webhook.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 轮换结果；新密钥明文只在本次响应中返回。 */
@Getter
@AllArgsConstructor
public class RotateWebhookSecretResponse {

    /** 新密钥序号。 */
    private final int rotationNo;

    /** 新密钥明文。 */
    private final String signingSecret;

    /** 旧密钥签名截止时间；立即作废时为空。 */
    private final Instant previousValidUntil;
}
