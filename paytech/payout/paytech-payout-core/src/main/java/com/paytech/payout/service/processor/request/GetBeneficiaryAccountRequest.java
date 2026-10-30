package com.paytech.payout.service.processor.request;

import com.paytech.payout.api.enums.BeneficiaryOwnerType;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 查询收款账户详情。 */
@Getter
@AllArgsConstructor
public class GetBeneficiaryAccountRequest {
    /** 收款账户主键 */
    @NotNull
    private final Long beneficiaryAccountId;

    /** 可选归属 Owner 类型 */
    private final BeneficiaryOwnerType ownerType;

    /** 可选归属 Owner 标识 */
    private final Long ownerId;

    /** 是否运营视图（返回共用提示） */
    private final boolean operationsViewer;
}
