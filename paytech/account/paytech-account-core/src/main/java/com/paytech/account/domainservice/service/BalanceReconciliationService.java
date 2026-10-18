package com.paytech.account.domainservice.service;

import java.time.LocalDate;
import java.util.List;

import com.paytech.account.domain.LedgerAccountTotal;
import com.paytech.account.domain.LedgerBookRef;

/**
 * 日终核对（design/04 §7）：按账簿比对本服务余额与 ledger 按分录聚合的 Owner 账户余额
 * <li>按 (科目代码, Owner 类型, Owner, 余额桶) 配对，净额不等或只有一侧有非零余额即为差异；差异写 t_balance_discrepancy 并记 WARN。</li>
 * <li>调用方在事务外取得 ledger 侧余额；本服务只读写 mor_account。阶段 0 不接工单，裁决依据是 billing 经济事实。</li>
 */
public interface BalanceReconciliationService {

    /**
     * 核对一个账簿
     *
     * @param reconDate 核对日期（UTC）
     * @param book 已经 ledger-api 确认的账簿
     * @param ledgerTotals ledger 按分录聚合的 Owner 账户余额
     * @return 本次记录的差异数
     */
    int reconcile(LocalDate reconDate, LedgerBookRef book, List<LedgerAccountTotal> ledgerTotals);
}
