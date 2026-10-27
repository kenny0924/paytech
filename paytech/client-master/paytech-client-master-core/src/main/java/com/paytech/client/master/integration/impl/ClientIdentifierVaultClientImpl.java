package com.paytech.client.master.integration.impl;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.ONBOARDING_DEPENDENCY_UNAVAILABLE;

import java.util.UUID;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.client.master.integration.ClientIdentifierVaultClient;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.vault.client.ProtectCommand;
import com.paytech.vault.client.TokenMode;
import com.paytech.vault.client.VaultClient;

import lombok.AllArgsConstructor;

/**
 * LEGAL_IDENTIFIER_V1 的 Protect；用途 CLIENT_ONBOARDING_IDENTIFIER（Vault 访问规则 client-master-service × PROTECT/REVEAL）
 * <li>每次保存使用随机操作键：STABLE 模式下同值仍得到同一 Token，不用证件号派生键。</li>
 * <li>不缓存原值或 Token；失败不写库。</li>
 */
@AllArgsConstructor
public class ClientIdentifierVaultClientImpl implements ClientIdentifierVaultClient {
    /** Vault 数据类型。 */
    static final String DATA_TYPE = "LEGAL_IDENTIFIER_V1";
    /** Vault 访问用途。 */
    static final String PURPOSE = "CLIENT_ONBOARDING_IDENTIFIER";

    private final VaultClient vault;

    /** 事务外 Protect；Vault 失败统一为依赖不可用。 */
    @Override
    public String protect(String normalizedValue) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Vault 调用不能位于数据库事务内");
        }
        try {
            return vault.protect(new ProtectCommand(DATA_TYPE, TokenMode.STABLE, normalizedValue, PURPOSE,
                UUID.randomUUID().toString())).token();
        } catch (RuntimeException failure) {
            throw new ErrorException(ONBOARDING_DEPENDENCY_UNAVAILABLE);
        }
    }
}
