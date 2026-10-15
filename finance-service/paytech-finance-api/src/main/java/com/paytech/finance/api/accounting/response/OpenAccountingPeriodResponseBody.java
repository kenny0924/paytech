package com.paytech.finance.api.accounting.response;

import lombok.Getter;
import lombok.Setter;

/** 开放会计期间结果。 */
@Getter
@Setter
public class OpenAccountingPeriodResponseBody {
    /**
     * 会计期间主键
     */
    private String accountingPeriodId;

    /**
     * 期间当前状态
     * <li>成功时为 OPEN</li>
     */
    private String status;
}
