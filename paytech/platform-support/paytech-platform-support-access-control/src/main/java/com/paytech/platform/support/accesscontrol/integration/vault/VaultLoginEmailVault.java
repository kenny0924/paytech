package com.paytech.platform.support.accesscontrol.integration.vault;

import java.util.UUID;

import com.paytech.vault.client.ProtectCommand;
import com.paytech.vault.client.RevealQuery;
import com.paytech.vault.client.TokenMode;
import com.paytech.vault.client.VaultClient;

import lombok.RequiredArgsConstructor;

/** 使用正式 Vault V1 客户端保护和读取登录邮箱。 */
@RequiredArgsConstructor
public final class VaultLoginEmailVault implements LoginEmailVault {
    private static final String DATA_TYPE = "EMAIL_ADDRESS_V1";
    private static final String PURPOSE = "PLATFORM_LOGIN_EMAIL_V1";

    private final VaultClient client;

    @Override
    public String protectCanonicalEmail(String canonicalEmail) {
        return client.protect(new ProtectCommand(DATA_TYPE, TokenMode.STABLE, canonicalEmail, PURPOSE,
            UUID.randomUUID().toString())).token();
    }

    @Override
    public String revealCanonicalEmail(String token) {
        return client.reveal(new RevealQuery(token, PURPOSE)).value();
    }
}
