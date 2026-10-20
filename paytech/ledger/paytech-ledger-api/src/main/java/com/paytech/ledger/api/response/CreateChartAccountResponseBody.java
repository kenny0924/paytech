package com.paytech.ledger.api.response;

import lombok.Getter;
import lombok.Setter;

/** 新增科目结果。 */
@Getter
@Setter
public class CreateChartAccountResponseBody {
    /**
     * 新科目主键
     */
    private String chartAccountId;
}
