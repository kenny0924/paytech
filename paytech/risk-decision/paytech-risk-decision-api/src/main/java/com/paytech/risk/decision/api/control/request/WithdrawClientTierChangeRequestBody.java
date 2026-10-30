package com.paytech.risk.decision.api.control.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 申请人撤回待审批的档位变更申请（L5）。 */
@Getter
@Setter
public class WithdrawClientTierChangeRequestBody {

    /** 期望的申请并发版本。 */
    @NotNull
    @Positive
    private Integer expectedVersion;
}
