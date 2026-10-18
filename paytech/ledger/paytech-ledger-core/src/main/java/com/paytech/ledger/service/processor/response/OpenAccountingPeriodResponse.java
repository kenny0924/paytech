package com.paytech.ledger.service.processor.response;

import com.paytech.ledger.core.enums.PeriodStatus;

import lombok.Getter;
import lombok.Setter;

/** 开放期间的结果。 */
@Getter
@Setter
public class OpenAccountingPeriodResponse {
    /** 会计期间主键 */
    private Long accountingPeriodId;

    /** 当前状态 */
    private PeriodStatus status;
}
