package com.paytech.ledger.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** account 对一个 ledger 账簿报告的余额消费进度（design/04 §6），关账步骤 2、3 使用。 */
@Getter
@AllArgsConstructor
public class BookBalanceConsistency {
    /** ledger 账簿主键。 */
    private final long ledgerBookId;

    /** account 已处理的属于该账簿主体的最大经济事实 ID；尚未处理任何事实时为空。 */
    private final Long maxAppliedFactId;

    /** account 中该账簿 CHANNEL_ACCOUNT 与 CASH_ACCOUNT 净余额非零的账户数。 */
    private final long nonZeroTreasuryAccounts;
}
