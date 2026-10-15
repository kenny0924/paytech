package com.paytech.finance.api.accounting.response;

import lombok.Getter;
import lombok.Setter;

/** 新建会计期间结果。 */
@Getter
@Setter
public class CreateAccountingPeriodResponseBody {
    /**
     * 新建会计期间主键
     */
    private String accountingPeriodId;
}
