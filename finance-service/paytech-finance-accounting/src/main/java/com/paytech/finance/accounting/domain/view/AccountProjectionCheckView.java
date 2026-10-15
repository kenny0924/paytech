package com.paytech.finance.accounting.domain.view;

import java.math.BigDecimal;

import com.paytech.finance.accounting.core.enums.NormalSide;

import lombok.Getter;
import lombok.Setter;

/** 账户余额投影与水位内全量分录重算的对照读取结果。 */
@Getter
@Setter
public class AccountProjectionCheckView {
    /** 账户主键。 */
    private Long accountId;

    /** 账户冻结的正常方向。 */
    private NormalSide normalSide;

    /** 投影累计借方。 */
    private BigDecimal debitTotal;

    /** 投影累计贷方。 */
    private BigDecimal creditTotal;

    /** 投影净余额。 */
    private BigDecimal netBalance;

    /** 水位内分录借方合计。 */
    private BigDecimal journalDebit;

    /** 水位内分录贷方合计。 */
    private BigDecimal journalCredit;
}
