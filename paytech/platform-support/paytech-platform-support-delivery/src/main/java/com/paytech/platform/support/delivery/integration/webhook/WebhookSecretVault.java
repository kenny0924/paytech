package com.paytech.platform.support.delivery.integration.webhook;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.delivery.domain.WebhookSignature;
import com.paytech.vault.client.ProtectCommand;
import com.paytech.vault.client.RevealQuery;
import com.paytech.vault.client.TokenMode;
import com.paytech.vault.client.VaultClient;

import lombok.RequiredArgsConstructor;

/**
 * 用正式 Vault V1 客户端保存与读取 Webhook 签名密钥
 * <li>数据类型 WEBHOOK_SIGNING_SECRET_V1，REFERENCE Token，用途 WEBHOOK_SIGNING；需由部署显式授权</li>
 * <li>只在数据库事务外调用；原值不缓存、不记录、不进入异常或补偿 Payload</li>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "webhook.enabled", havingValue = "true")
public class WebhookSecretVault {

    /** Vault 数据类型。 */
    public static final String DATA_TYPE = "WEBHOOK_SIGNING_SECRET_V1";

    /** Platform 保存与读取签名密钥的用途。 */
    public static final String PURPOSE = "WEBHOOK_SIGNING";

    private final VaultClient client;

    /** 保存一把新密钥，返回引用 Token；每次使用新的幂等键，得到独立引用。 */
    public String protect(String secret) {
        return client.protect(new ProtectCommand(DATA_TYPE, TokenMode.REFERENCE, secret, PURPOSE,
            UUID.randomUUID().toString())).token();
    }

    /** 发送前读取密钥；格式不符时失败关闭。 */
    public String reveal(String token) {
        String secret = client.reveal(new RevealQuery(token, PURPOSE)).value();
        if (secret == null || !secret.matches(WebhookSignature.SECRET_PATTERN)) {
            throw new ErrorException("Webhook 签名密钥格式不合法");
        }
        return secret;
    }
}
