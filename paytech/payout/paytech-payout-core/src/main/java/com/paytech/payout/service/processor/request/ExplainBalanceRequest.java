package com.paytech.payout.service.processor.request;

import com.paytech.payout.api.enums.BeneficiaryOwnerType;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 查询 Owner 余额解释。 */
@Getter
@AllArgsConstructor
public class ExplainBalanceRequest {
    /** Owner 类型 */
    @NotNull
    private final BeneficiaryOwnerType ownerType;

    /** Owner 标识 */
    @NotNull
    private final Long ownerId;
}
