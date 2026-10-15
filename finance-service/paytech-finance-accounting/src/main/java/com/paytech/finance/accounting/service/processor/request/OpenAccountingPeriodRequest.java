package com.paytech.finance.accounting.service.processor.request;

import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 把 FUTURE 期间开放过账。 */
@Getter
@Setter
public class OpenAccountingPeriodRequest {
    /** 会计期间主键 */
    @NotNull
    private Long accountingPeriodId;
}
