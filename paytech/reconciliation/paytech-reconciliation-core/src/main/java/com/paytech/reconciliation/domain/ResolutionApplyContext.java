package com.paytech.reconciliation.domain;

import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.core.dal.dataobject.ReconResolution;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 批准并应用处置的上下文：锁内读取的处置、异常与银行层应用对象
 * <li>交易层的应用对象由 ChannelReconciliationResolutionService 按 ID 读取并在事务内校验，不在此持有。</li>
 * <li>银行层：RE_MATCH 持有已校验的匹配上下文；预期流水归类持有预期流水与渠道来源账单；银行流水归类持有流水。不适用的为空。</li>
 */
@Getter
@AllArgsConstructor
public class ResolutionApplyContext {
    /** 待复核处置 */
    private final ReconResolution resolution;

    /** 所属异常（UNDER_REVIEW） */
    private final ReconException exception;

    /** 银行层 RE_MATCH 的匹配上下文 */
    private final BankMatchContext bankMatch;

    /** 预期银行流水归类的对象 */
    private final ExpectedBankMovement movement;

    /** 渠道预期流水的来源账单 */
    private final ChannelStatement channelStatement;

    /** 银行流水归类的对象 */
    private final BankStatementLine bankLine;
}
