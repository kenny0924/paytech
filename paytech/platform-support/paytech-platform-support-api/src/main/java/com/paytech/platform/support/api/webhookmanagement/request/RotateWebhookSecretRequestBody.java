package com.paytech.platform.support.api.webhookmanagement.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 轮换端点签名密钥。 */
@Getter
@Setter
public class RotateWebhookSecretRequestBody {

    /**
     * 是否立即作废旧密钥
     * <li>false：旧密钥再参与签名 1 小时；true：旧密钥立即失效，用于确认泄露。</li>
     */
    private boolean immediate;

    /**
     * 操作原因
     * <li>1..500 字符，写入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private String reason;
}
