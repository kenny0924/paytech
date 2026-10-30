package com.paytech.payout.integration.impl;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.payout.integration.BeneficiaryVaultClient;
import com.paytech.vault.client.ProtectCommand;
import com.paytech.vault.client.RevealQuery;
import com.paytech.vault.client.TokenMode;
import com.paytech.vault.client.VaultClient;

import lombok.AllArgsConstructor;

/** 经 paytech-vault-client 访问 Vault；调用前拒绝活动数据库事务（事务公理）。 */
@Component
@AllArgsConstructor
public class BeneficiaryVaultClientImpl implements BeneficiaryVaultClient {
    /** 原值数据类型。 */
    static final String DETAILS_DATA_TYPE = "PAYOUT_BENEFICIARY_V1";

    /** 查重键数据类型。 */
    static final String ACCOUNT_KEY_DATA_TYPE = "PAYOUT_BANK_ACCOUNT_KEY_V1";

    /** 提交时的 Protect 用途。 */
    static final String SUBMIT_PURPOSE = "PAYOUT_BENEFICIARY_SUBMIT";

    /** 运营核验时的 Reveal 用途。 */
    static final String REVIEW_PURPOSE = "PAYOUT_BENEFICIARY_REVIEW";

    private final VaultClient vaultClient;

    /** Protect 原值 JSON。 */
    @Override
    public String protectDetails(String vaultJson, String idempotencyKey) {
        requireNoTransaction();
        return vaultClient.protect(new ProtectCommand(DETAILS_DATA_TYPE, TokenMode.REFERENCE, vaultJson, SUBMIT_PURPOSE,
            idempotencyKey)).token();
    }

    /** Protect 规范化账号。 */
    @Override
    public String protectAccountKey(String accountIdentifier, String idempotencyKey) {
        requireNoTransaction();
        return vaultClient.protect(new ProtectCommand(ACCOUNT_KEY_DATA_TYPE, TokenMode.STABLE, accountIdentifier,
            SUBMIT_PURPOSE, idempotencyKey)).token();
    }

    /** Reveal 原值 JSON。 */
    @Override
    public String revealDetails(String beneficiaryVaultToken) {
        requireNoTransaction();
        return vaultClient.reveal(new RevealQuery(beneficiaryVaultToken, REVIEW_PURPOSE)).value();
    }

    /** Vault 调用不能位于数据库事务内。 */
    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ErrorException("Vault 调用不能位于数据库事务内");
        }
    }
}
