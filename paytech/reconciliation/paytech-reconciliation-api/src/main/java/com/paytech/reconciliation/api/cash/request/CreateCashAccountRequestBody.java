package com.paytech.reconciliation.api.cash.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 提交新建银行账户请求
 * <li>含银行账号明文：不生成 toString，不得进入普通日志。</li>
 */
@Getter
@Setter
public class CreateCashAccountRequestBody {
    /**
     * 主体类型
     * <li>OPERATING_ENTITY 或 FINANCE_ENTITY。</li>
     */
    @NotBlank
    @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY", message = "只能为 OPERATING_ENTITY 或 FINANCE_ENTITY")
    private String entityType;

    /**
     * 主体标识
     * <li>经营主体为 entityNo（两位大写字母加三位数字），财务主体为 ledger 主键十进制字符串；本期不向 Owner 核对。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z]{2}[0-9]{3}|[1-9][0-9]{0,18}", message = "必须是经营主体编号或正十进制整数字符串")
    private String entityId;

    /**
     * 资金用途
     * <li>CHANNEL_SETTLEMENT、PAYOUT 或 TAX；创建后不可改。</li>
     */
    @NotBlank
    @Pattern(regexp = "CHANNEL_SETTLEMENT|PAYOUT|TAX", message = "只能为 CHANNEL_SETTLEMENT、PAYOUT 或 TAX")
    private String accountPurpose;

    /**
     * 开户银行代码
     * <li>运营录入的银行机构代码，非固定枚举。</li>
     */
    @NotBlank
    @Size(max = 64)
    private String bankCode;

    /**
     * 账户显示名称
     */
    @NotBlank
    @Size(max = 128)
    private String accountName;

    /**
     * 账户币种
     * <li>大写 ISO 4217。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "必须是大写 ISO 4217 三位代码")
    private String currency;

    /**
     * 银行账号明文
     * <li>4–34 位字母或数字（含 IBAN）；事务外经 Vault Protect 后只存令牌与末四位，不落库、不回显。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9]{4,34}", message = "必须是 4–34 位字母或数字")
    private String accountNumber;
}
