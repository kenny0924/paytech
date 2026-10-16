package com.paytech.finance.accounting.service.processor.request;

import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 发起期间关账。 */
@Getter
@Setter
public class StartPeriodCloseRequest {
    /** 会计期间主键 */
    @NotNull
    private Long periodId;

    /** 编制人 */
    @NotNull
    private Long makerId;
}
