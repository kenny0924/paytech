package com.paytech.finance.accounting.service.processor.request;

import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 对一个账簿和会计期间执行试算。 */
@Getter
@Setter
public class RunTrialBalanceRequest {
    /** 账簿主键 */
    @NotNull
    private Long ledgerBookId;

    /** 会计期间主键 */
    @NotNull
    private Long periodId;
}
