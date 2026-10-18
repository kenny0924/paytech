package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.PreparedHumanCredentials;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsPasswordPolicy;
import com.paytech.platform.support.accesscontrol.integration.vault.LoginEmailVault;

import lombok.AllArgsConstructor;

/** 人员创建入口共用的事务外密码与 Vault 准备。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class OperationsHumanCredentialsPreparation {
    private final LoginEmailVault vault;
    private final OperationsPasswordPolicy passwords;

    /** 规范邮箱，派生密码并取得受控稳定 Token，不开启数据库事务。 */
    public PreparedHumanCredentials prepareHuman(String email, String initialPassword) {
        String canonical = passwords.canonicalEmail(email);
        String verifier = passwords.encode(passwords.validateNewPassword(initialPassword, canonical));
        String token = vault.protectCanonicalEmail(canonical);
        return new PreparedHumanCredentials(canonical, token, passwords.maskEmail(canonical), verifier);
    }
}
