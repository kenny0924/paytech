package com.paytech.platform.support.delivery.integration.vault;

import java.util.UUID;
import com.paytech.vault.client.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** 使用正式 Vault V1 客户端保护邮件地址；用途需由部署显式授权。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.enabled", havingValue = "true")
public class NotificationAddressVault {

    private static final String PURPOSE = "PLATFORM_NOTIFICATION_EMAIL_V1";
    private final VaultClient client;

    /** 每次受理使用新 Protect 身份；地址相等由 Vault STABLE 语义保证。 */
    public String protect(String email) {
        return client.protect(new ProtectCommand("EMAIL_ADDRESS_V1", TokenMode.STABLE, email, PURPOSE,
            UUID.randomUUID().toString())).token();
    }

    /** 仅在实际发送前瞬时读取，不缓存、记录或传递到补偿 Payload。 */
    public String reveal(String token) {
        return client.reveal(new RevealQuery(token, PURPOSE)).value();
    }
}
