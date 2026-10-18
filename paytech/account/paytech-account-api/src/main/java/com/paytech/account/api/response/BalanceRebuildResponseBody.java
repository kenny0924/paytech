package com.paytech.account.api.response;

import lombok.Getter;
import lombok.Setter;

/** 账簿余额重建结果。 */
@Getter
@Setter
public class BalanceRebuildResponseBody {
    /** ledger 账簿主键。 */
    private String ledgerBookId;

    /** 从 billing 重放的已批准经济事实数（含已被替代的事实）。 */
    private String replayedFactCount;

    /** 重放的最大 factId；没有任何事实时为空。 */
    private String maxFactId;

    /** 重建后账簿内的余额账户数。 */
    private String balanceCount;
}
