package com.paytech.payout.integration;

/**
 * 收款账户原值的 Vault 出站端口（technique 8.1 P1、P10，db/vault/dml.sql）
 * <li>PAYOUT_BENEFICIARY_V1（REFERENCE、JSON）保存完整原值，PAYOUT_BANK_ACCOUNT_KEY_V1（STABLE、TEXT）保存规范化账号、只作查重键。</li>
 * <li>只能在数据库事务之外调用；Vault 失败原样上抛（不可用为 503），调用方不落库。</li>
 */
public interface BeneficiaryVaultClient {
    /**
     * Protect 原值 JSON（用途 PAYOUT_BENEFICIARY_SUBMIT）
     *
     * @param vaultJson 原值 Canonical JSON
     * @param idempotencyKey 同一内容重放复用的幂等键
     * @return REFERENCE Token
     */
    String protectDetails(String vaultJson, String idempotencyKey);

    /**
     * Protect 规范化账号（用途 PAYOUT_BENEFICIARY_SUBMIT），同值同 Token
     *
     * @param accountIdentifier 规范化账号或 IBAN
     * @param idempotencyKey 幂等键
     * @return STABLE Token
     */
    String protectAccountKey(String accountIdentifier, String idempotencyKey);

    /**
     * Reveal 原值 JSON（用途 PAYOUT_BENEFICIARY_REVIEW）；结果不缓存、不落库、不记日志
     *
     * @param beneficiaryVaultToken REFERENCE Token
     * @return 原值 JSON
     */
    String revealDetails(String beneficiaryVaultToken);
}
