package com.paytech.ledger.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 账簿某期间的科目余额（实时）：末级与各级汇总行。 */
@Getter
@Setter
public class AccountBalanceListResponseBody {
    /** 账簿主键。 */
    private String ledgerBookId;

    /** 会计期间主键。 */
    private String periodId;

    /** 账簿币种，金额的主货币单位。 */
    private String currency;

    /**
     * 按科目编号排序的余额行
     * <li>只含有发生额或余额的末级科目及其各级上级；期初为期间开始前全部分录，本期为期间内分录。</li>
     */
    private List<AccountBalanceLine> lines;
}
