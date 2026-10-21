package com.paytech.reconciliation.domainservice.service;

import java.util.List;

import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.domain.StatementMatchContext;

/**
 * 渠道账单匹配结果与对平的本地保存（CHANNEL_STATEMENT_MATCH，technique D-05、D-10）
 * <li>在匹配处理器的本地事务内调用；行与平台流水按版本条件更新，冲突抛 409 使整个匹配任务回滚后重试。</li>
 */
public interface ChannelStatementMatchService {

    /**
     * 保存本次匹配计划：账单行的匹配 / 异常状态与平台流水的已匹配金额、实际费用、状态
     *
     * @param context 已完成计划的匹配上下文
     */
    void saveMatches(StatementMatchContext context);

    /**
     * 对平：覆盖的平台流水累加已结清金额（满额 SETTLED）、账单转 RECONCILED（净额方向 NONE 直接 CASH_CONFIRMED）
     * <li>同一事务登记 ChannelBatchReconciled，以及本账单使其 SETTLED 且实际费用 ≠ 预估的每笔平台流水的 ActualChannelCostConfirmed。</li>
     *
     * @param context 门禁已通过的匹配上下文
     * @param coveredTxns 本账单行匹配到的平台流水（事务内重新读取的最新版本）
     */
    void reconcile(StatementMatchContext context, List<PlatformTxn> coveredTxns);
}
