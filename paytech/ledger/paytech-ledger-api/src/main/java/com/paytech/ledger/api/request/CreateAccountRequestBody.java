package com.paytech.ledger.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 在账簿下创建统一账户请求。 */
@Getter
@Setter
public class CreateAccountRequestBody {
    /**
     * 科目代码
     * <li>必须属于账簿采用的科目表且可直接过账</li>
     */
    @NotBlank
    @Size(max = 64)
    private String accountCode;

    /**
     * 核算对象类型
     * <li>OPERATING_ENTITY、FINANCE_ENTITY、CHANNEL_ACCOUNT、CASH_ACCOUNT、TAX_AUTHORITY（{@code LedgerOwnerType}）。</li>
     * <li>控制科目（CLIENT_PAYABLE、CLIENT_SECURITY_DEPOSIT、CLIENT_RECOVERY_RECEIVABLE、PARTNER_PAYABLE、PARTNER_RECOVERY_RECEIVABLE）
     * 只允许账簿主体类型且 ownerId 等于账簿主体，否则 ACCOUNT_IDENTITY_INVALID。</li>
     */
    @NotBlank
    @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY|CHANNEL_ACCOUNT|CASH_ACCOUNT|TAX_AUTHORITY", message = "核算对象类型非法")
    private String ownerType;

    /**
     * 核算对象标识
     * <li>OPERATING_ENTITY 为 entityNo，其余为正十进制字符串</li>
     */
    @NotBlank
    @Size(max = 64)
    private String ownerId;

    /**
     * 余额桶
     * <li>ledger 账户一律 NONE（分桶明细只在 account）；其他取值违反账户身份矩阵。</li>
     */
    @NotBlank
    @Pattern(regexp = "PENDING|AVAILABLE|LOCKED|NONE", message = "余额桶非法")
    private String balanceBucket;
}
