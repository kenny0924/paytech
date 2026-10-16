package com.paytech.finance.accounting.domain.view;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** 从账户全部分录行重算的借贷累计与最后分录序号。 */
@Getter
@Setter
public class AccountJournalTotalsView {
    /** 全部借方分录行金额合计；无分录时为零。 */
    private BigDecimal debitTotal;

    /** 全部贷方分录行金额合计；无分录时为零。 */
    private BigDecimal creditTotal;

    /** 涉及该账户的最大分录序号；无分录时为 0。 */
    private Long lastJournalSequence;
}
