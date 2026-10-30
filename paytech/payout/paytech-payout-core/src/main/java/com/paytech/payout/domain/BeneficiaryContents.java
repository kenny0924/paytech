package com.paytech.payout.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;

/**
 * 收款账户冻结内容摘要与 Vault 幂等键（B2、B3）
 * <li>内容摘要只覆盖库内字段与 Token，不含原值：同 Owner 以同一内容重复提交得到同一 REFERENCE Token，因而同一摘要（AC-10）。</li>
 */
public final class BeneficiaryContents {
    /** 原值 Protect 的幂等键前缀。 */
    static final String DETAILS_KEY_PREFIX = "payout-beneficiary.";

    /** 账号查重 Protect 的幂等键前缀。 */
    static final String ACCOUNT_KEY_PREFIX = "payout-bank-account.";

    private BeneficiaryContents() {
    }

    /**
     * 冻结内容摘要：Owner、收款方式、国家、币种、银行、两个 Token、脱敏户名与末四位
     *
     * @param account 已准备 Token 与脱敏值的新账户
     * @return 64 位小写十六进制 SHA-256
     */
    public static String contentHash(BeneficiaryAccount account) {
        return sha256(String.join("\n", account.getOwnerType().getCode(), account.getOwnerId().toString(),
            account.getRailType().getCode(), account.getBankCountry(), account.getCurrency(), account.getBankName(),
            Objects.toString(account.getBankCode(), ""), account.getBeneficiaryVaultToken(), account.getBankAccountToken(),
            account.getHolderNameMasked(), account.getAccountLast4()));
    }

    /**
     * 原值 Protect 的幂等键：同 Owner、同路由、同原值重放得到同一 Token
     *
     * @param ownerScope Owner 与路由的稳定描述（不含原值）
     * @param vaultJson 原值 JSON
     * @return 不含可读原值的安全 ASCII 键
     */
    public static String detailsIdempotencyKey(String ownerScope, String vaultJson) {
        return DETAILS_KEY_PREFIX + sha256(ownerScope + "\n" + vaultJson);
    }

    /**
     * 账号查重 Protect 的幂等键（STABLE 同值同 Token，键只保证重试安全）
     *
     * @param ownerScope Owner 的稳定描述
     * @param accountIdentifier 规范化账号
     * @return 不含可读原值的安全 ASCII 键
     */
    public static String accountIdempotencyKey(String ownerScope, String accountIdentifier) {
        return ACCOUNT_KEY_PREFIX + sha256(ownerScope + "\n" + accountIdentifier);
    }

    /** UTF-8 字节的 SHA-256 小写十六进制。 */
    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 不可用", unavailable);
        }
    }
}
