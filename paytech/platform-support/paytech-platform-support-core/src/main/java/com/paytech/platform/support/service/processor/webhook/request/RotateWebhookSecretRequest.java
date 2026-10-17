package com.paytech.platform.support.service.processor.webhook.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 轮换端点签名密钥。 */
@Getter
@AllArgsConstructor
public class RotateWebhookSecretRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 当前普通 Operations 会话。 */
    private final String session;

    /** 端点 ID。 */
    private final long endpointId;

    /** 是否立即作废旧密钥。 */
    private final boolean immediate;

    /** 操作原因。 */
    private final String reason;
}
