package com.paytech.ledger.domain;

import java.math.BigDecimal;

import com.paytech.ledger.core.dal.dataobject.Account;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 账户余额投影的四个可重建值，用于投影重建前后对比与摘要。 */
@Getter
@AllArgsConstructor
public class ProjectionSnapshot {
    /** 累计借方。 */
    private final BigDecimal debitTotal;

    /** 累计贷方。 */
    private final BigDecimal creditTotal;

    /** 按正常方向的有符号净余额。 */
    private final BigDecimal netBalance;

    /** 已应用的最后分录序号。 */
    private final long lastJournalSequence;

    /** 复制账户当前投影。 */
    public static ProjectionSnapshot of(Account account) {
        return new ProjectionSnapshot(account.getDebitTotal(), account.getCreditTotal(), account.getNetBalance(),
            account.getLastJournalSequence());
    }
}
