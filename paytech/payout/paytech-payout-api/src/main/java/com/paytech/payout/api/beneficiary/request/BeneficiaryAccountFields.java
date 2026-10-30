package com.paytech.payout.api.beneficiary.request;

import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 收款账户字段（含原值）
 * <li>户名、账号 / IBAN、银行代码、SWIFT BIC、账户类型只经 Vault Protect 后保存 Token；本对象不生成 toString，不得进入普通日志。</li>
 * <li>必填与格式按收款方式 × 开户国家 × 币种的字段要求校验（GET /payout/v1/beneficiary-requirements），这里只限长度。</li>
 */
@Getter
@Setter
public class BeneficiaryAccountFields {
    /**
     * 户名
     * <li>首尾空白去除后保存到 Vault；库内只有脱敏值。</li>
     */
    @Size(max = 140)
    private String holderName;

    /**
     * IBAN
     * <li>去空白、转大写后按字段要求校验；与 accountNumber 按字段要求二选一或必填。</li>
     */
    @Size(max = 42)
    private String iban;

    /**
     * 账号
     * <li>去空白、转大写后按字段要求校验。</li>
     */
    @Size(max = 42)
    private String accountNumber;

    /**
     * 本地银行代码
     * <li>非敏感，同时保存在 bank_code 列；只用 IBAN 的国家为空。</li>
     */
    @Size(max = 32)
    private String bankCode;

    /**
     * SWIFT BIC
     * <li>SWIFT 收款方式必填，8 或 11 位；非敏感，同时保存在 bank_code 列。</li>
     */
    @Size(max = 11)
    private String swiftBic;

    /**
     * 银行显示名称
     * <li>非敏感，保存在 bank_name 列。</li>
     */
    @Size(max = 128)
    private String bankName;

    /**
     * 账户类型
     * <li>可空，例如 CURRENT、SAVINGS；本期不校验取值。</li>
     */
    @Size(max = 32)
    private String accountType;
}
