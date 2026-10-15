package com.paytech.finance.accounting.domain;

import java.util.List;

import com.paytech.finance.accounting.core.dal.dataobject.AccountingPeriod;
import com.paytech.finance.accounting.core.dal.dataobject.LedgerBook;
import com.paytech.finance.accounting.core.dal.dataobject.TrialBalanceException;
import com.paytech.finance.accounting.core.dal.dataobject.TrialBalanceLine;
import com.paytech.finance.accounting.core.dal.dataobject.TrialBalanceRun;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/** 一次试算：事务前已校验的账簿与期间，以及事务内生成的运行、行与异常。 */
@Getter
@RequiredArgsConstructor
public class TrialBalanceContext {
    /** 已存在的账簿。 */
    private final LedgerBook book;

    /** 非 FUTURE 且属于账簿主体的期间。 */
    private final AccountingPeriod period;

    /** 账簿币种小数位。 */
    private final int minorUnit;

    /** 生成的运行；事务成功后可用。 */
    @Setter
    private TrialBalanceRun run;

    /** 生成的试算行。 */
    @Setter
    private List<TrialBalanceLine> lines;

    /** 生成的异常。 */
    @Setter
    private List<TrialBalanceException> exceptions;
}
