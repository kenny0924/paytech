package com.paytech.reconciliation.domain.view;

import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.domain.ReconcileGate;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道账单详情：账单、清算账户、行计数、未结异常数、预期银行流水与对平门禁（多表组合）。 */
@Getter
@AllArgsConstructor
public class ChannelStatementDetailView {
    /** 渠道账单 */
    private final ChannelStatement statement;

    /** 所属清算账户 */
    private final ClearingAccount clearingAccount;

    /** 未匹配行数 */
    private final long unmatchedLineCount;

    /** 已匹配行数 */
    private final long matchedLineCount;

    /** 异常行数 */
    private final long exceptionLineCount;

    /** 本账单未结异常数 */
    private final long openExceptionCount;

    /** 本账单生成的预期银行流水主键；没有为空 */
    private final Long expectedMovementId;

    /** 对平门禁；只对 MATCHING 账单计算，其他状态为空 */
    private final ReconcileGate gate;
}
