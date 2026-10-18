package com.paytech.account.domain.view;

import com.paytech.account.domain.LedgerBookRef;

/**
 * 账簿余额重建结果
 *
 * @param book ledger 账簿身份
 * @param replayedFactCount 重放的经济事实数
 * @param maxFactId 重放的最大 factId，可空
 * @param balanceCount 重建后账簿内的余额账户数
 */
public record BalanceRebuildView(LedgerBookRef book, int replayedFactCount, Long maxFactId, int balanceCount) {
}
