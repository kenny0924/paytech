package com.paytech.platform.support.accesscontrol.integration.vault;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.paytech.foundation.common.error.ServiceUnavailableException;

/** 仅用于显式 local/auto 的确定性 Vault Mock；邮箱原值仅保留于进程内，以演示受控密码上下文校验。 */
public final class LocalLoginEmailVault implements LoginEmailVault {
    private final java.util.Map<String, String> values = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public String protectCanonicalEmail(String canonicalEmail) {
        try {
            String token = "mock_" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(("local-vault-email-v1:" + canonicalEmail).getBytes(StandardCharsets.UTF_8)));
            if (values.size() >= 10000 && !values.containsKey(token)) {
                throw new IllegalStateException("本地 Vault Mock 容量已满");
            }
            values.put(token, canonicalEmail);
            return token;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("本机 SHA-256 不可用", exception);
        }
    }

    @Override
    public String revealCanonicalEmail(String token) {
        String email = values.get(token);
        if (email == null) {
            throw new ServiceUnavailableException();
        }
        return email;
    }
}
