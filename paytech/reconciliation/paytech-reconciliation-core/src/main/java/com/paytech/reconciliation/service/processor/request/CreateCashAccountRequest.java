package com.paytech.reconciliation.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.paytech.reconciliation.api.enums.AccountPurpose;
import com.paytech.reconciliation.api.enums.ReconEntityType;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 提交新建银行账户命令
 * <li>含银行账号明文：不生成 toString、不使用 Builder（Builder 的 toString 会输出字段），只在 Vault Protect 前使用。</li>
 */
@Getter
@AllArgsConstructor
public class CreateCashAccountRequest {
    /** 提交人 Operations Account ID */
    @NotNull
    private final Long actorId;

    /** 主体类型 */
    @NotNull
    private final ReconEntityType entityType;

    /** 主体标识 */
    @NotBlank
    private final String entityId;

    /** 资金用途 */
    @NotNull
    private final AccountPurpose accountPurpose;

    /** 开户银行代码 */
    @NotBlank
    private final String bankCode;

    /** 账户显示名称 */
    @NotBlank
    private final String accountName;

    /** 账户币种 */
    @NotBlank
    private final String currency;

    /** 银行账号明文 */
    @NotBlank
    private final String accountNumber;
}
