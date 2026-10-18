package com.paytech.ledger.api.response;

import lombok.Getter;
import lombok.Setter;

/** 新建财务主体结果。 */
@Getter
@Setter
public class CreateFinanceEntityResponseBody {
    /**
     * 新建财务主体主键
     */
    private String financeEntityId;
}
