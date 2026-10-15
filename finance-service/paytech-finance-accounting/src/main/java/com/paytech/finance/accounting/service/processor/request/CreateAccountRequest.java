package com.paytech.finance.accounting.service.processor.request;

import com.paytech.finance.accounting.core.enums.BalanceBucket;
import com.paytech.finance.accounting.core.enums.OwnerType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 在账簿下创建统一账户。 */
@Getter
@Setter
public class CreateAccountRequest {
    /** 账簿主键 */
    @NotNull
    private Long ledgerBookId;

    /** 科目代码 */
    @NotBlank
    private String accountCode;

    /** 核算对象类型 */
    @NotNull
    private OwnerType ownerType;

    /** 核算对象标识 */
    @NotBlank
    private String ownerId;

    /** 余额桶 */
    @NotNull
    private BalanceBucket balanceBucket;
}
