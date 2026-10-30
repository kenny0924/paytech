package com.paytech.payout.domain;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.paytech.foundation.common.error.ErrorException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 收款账户字段原值（户名、IBAN / 账号、银行代码、SWIFT BIC、银行名称、账户类型）
 * <li>只在提交时 Vault Protect 之前与运营 Reveal 之后存在于内存；不生成可读 toString，不得进入日志、事件、补偿载荷或数据库。</li>
 * <li>Vault 原值为不含 bankName 的 Canonical JSON（键按字典序、省略空值），bankName 非敏感、保存在本库列。</li>
 */
public final class BeneficiaryAccountDetails {
    /** Vault 原值 JSON 的序列化器（字典序键由 TreeMap 保证）。 */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String holderName;
    private final String iban;
    private final String accountNumber;
    private final String bankCode;
    private final String swiftBic;
    private final String bankName;
    private final String accountType;

    /**
     * 以全部字段构造；空串由调用方先规范化为 null。
     *
     * @param holderName 户名
     * @param iban IBAN
     * @param accountNumber 账号
     * @param bankCode 本地银行代码
     * @param swiftBic SWIFT BIC
     * @param bankName 银行名称
     * @param accountType 账户类型
     */
    public BeneficiaryAccountDetails(String holderName, String iban, String accountNumber, String bankCode, String swiftBic,
        String bankName, String accountType) {
        this.holderName = holderName;
        this.iban = iban;
        this.accountNumber = accountNumber;
        this.bankCode = bankCode;
        this.swiftBic = swiftBic;
        this.bankName = bankName;
        this.accountType = accountType;
    }

    /**
     * 从 Vault 原值 JSON 还原（Reveal 后使用）
     *
     * @param json Vault 返回的原值
     * @return 不含 bankName 的原值
     */
    public static BeneficiaryAccountDetails fromVaultJson(String json) {
        try {
            Map<String, String> values = JSON.readValue(json, new TypeReference<Map<String, String>>() {});
            return new BeneficiaryAccountDetails(values.get("holderName"), values.get("iban"), values.get("accountNumber"),
                values.get("bankCode"), values.get("swiftBic"), null, values.get("accountType"));
        } catch (JacksonException malformed) {
            throw new ErrorException("收款账户 Vault 原值不是合法 JSON");
        }
    }

    /** 按字段代码取值（字段要求校验使用）。 */
    public Map<String, String> byCode() {
        Map<String, String> values = new TreeMap<>();
        put(values, "holderName", holderName);
        put(values, "iban", iban);
        put(values, "accountNumber", accountNumber);
        put(values, "bankCode", bankCode);
        put(values, "swiftBic", swiftBic);
        put(values, "bankName", bankName);
        put(values, "accountType", accountType);
        return values;
    }

    /** Vault PAYOUT_BENEFICIARY_V1 原值：不含 bankName 的 Canonical JSON。 */
    public String vaultJson() {
        Map<String, String> values = byCode();
        values.remove("bankName");
        return JSON.writeValueAsString(values);
    }

    /** 账号标识：IBAN 优先，否则账号（已规范化）。 */
    public String accountIdentifier() {
        return iban != null ? iban : accountNumber;
    }

    /** 账号标识末四位。 */
    public String last4() {
        String identifier = accountIdentifier();
        return identifier.substring(identifier.length() - 4).toUpperCase(Locale.ROOT);
    }

    /** 户名脱敏：每个词保留首字符，其余以 * 代替，最长 255 字符。 */
    public String maskedHolderName() {
        StringBuilder masked = new StringBuilder();
        for (String word : holderName.split(" ")) {
            if (!masked.isEmpty()) {
                masked.append(' ');
            }
            int first = word.offsetByCodePoints(0, 1);
            masked.append(word, 0, first).append("*".repeat(word.codePointCount(first, word.length())));
        }
        return masked.length() > 255 ? masked.substring(0, 255) : masked.toString();
    }

    /** 银行代码列取值：SWIFT 为 BIC，本地为银行代码；都没有为空。 */
    public String bankCodeColumn() {
        return swiftBic != null ? swiftBic : bankCode;
    }

    /** 户名 */
    public String getHolderName() {
        return holderName;
    }

    /** IBAN */
    public String getIban() {
        return iban;
    }

    /** 账号 */
    public String getAccountNumber() {
        return accountNumber;
    }

    /** 本地银行代码 */
    public String getBankCode() {
        return bankCode;
    }

    /** SWIFT BIC */
    public String getSwiftBic() {
        return swiftBic;
    }

    /** 银行名称 */
    public String getBankName() {
        return bankName;
    }

    /** 账户类型 */
    public String getAccountType() {
        return accountType;
    }

    /** 不输出任何字段值。 */
    @Override
    public String toString() {
        return "BeneficiaryAccountDetails[redacted]";
    }

    /** 只放入非空值。 */
    private static void put(Map<String, String> values, String code, String value) {
        if (value != null) {
            values.put(code, value);
        }
    }
}
