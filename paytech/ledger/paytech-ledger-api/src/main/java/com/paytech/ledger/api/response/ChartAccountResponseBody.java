package com.paytech.ledger.api.response;

import lombok.Getter;
import lombok.Setter;

/** 一个会计科目。 */
@Getter
@Setter
public class ChartAccountResponseBody {
    /** 科目主键。 */
    private String chartAccountId;

    /** 所属科目表主键。 */
    private String chartId;

    /** 科目代码。 */
    private String accountCode;

    /** 科目名称。 */
    private String accountName;

    /** 科目类别：ASSET、LIABILITY、EQUITY、REVENUE 或 EXPENSE。 */
    private String accountClass;

    /** 正常余额方向：DEBIT 或 CREDIT。 */
    private String normalSide;

    /** 是否允许直接过账。 */
    private Boolean postingAllowed;
}
