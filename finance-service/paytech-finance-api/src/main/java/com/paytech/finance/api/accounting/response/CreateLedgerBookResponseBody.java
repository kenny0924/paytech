package com.paytech.finance.api.accounting.response;

import lombok.Getter;
import lombok.Setter;

/** 新建账簿结果。 */
@Getter
@Setter
public class CreateLedgerBookResponseBody {
    /**
     * 新建账簿主键
     */
    private String ledgerBookId;
}
