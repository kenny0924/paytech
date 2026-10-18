package com.paytech.account.domain.view;

import java.util.Map;

import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.domain.LedgerBookRef;

/**
 * 账簿的余额消费进度
 *
 * @param book ledger 账簿身份
 * @param appliedFactCount 已处理（应用、冲回或跳过）的经济事实数
 * @param maxAppliedFactId 已处理经济事实中最大的 factId，可空
 * @param nonZeroAccounts Owner 类型 → 净余额非零的余额账户数，固定包含 CLIENT、LOCAL_PARTNER、CHANNEL_ACCOUNT、CASH_ACCOUNT
 */
public record BookConsistencyView(LedgerBookRef book, long appliedFactCount, Long maxAppliedFactId,
    Map<OwnerType, Long> nonZeroAccounts) {
}
