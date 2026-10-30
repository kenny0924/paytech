package com.paytech.payout.service.processor.request;

import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 查询 Owner 收款账户列表。 */
@Getter
@AllArgsConstructor
public class ListBeneficiaryAccountsRequest {
    /** Owner 类型 */
    @NotNull
    private final BeneficiaryOwnerType ownerType;

    /** Owner 标识 */
    @NotNull
    private final Long ownerId;

    /** 可选状态 */
    private final BeneficiaryAccountStatus status;

    /** 可选币种 */
    private final String currency;

    /** 是否运营视图（返回共用提示） */
    private final boolean operationsViewer;
}
