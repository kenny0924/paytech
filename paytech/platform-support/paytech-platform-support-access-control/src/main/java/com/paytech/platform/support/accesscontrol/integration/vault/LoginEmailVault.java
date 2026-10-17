package com.paytech.platform.support.accesscontrol.integration.vault;

/** 登录邮箱的稳定 Value Token 边界；真实 Vault 由独立服务持有。 */
public interface LoginEmailVault {
    String protectCanonicalEmail(String canonicalEmail);

    String revealCanonicalEmail(String token);
}
