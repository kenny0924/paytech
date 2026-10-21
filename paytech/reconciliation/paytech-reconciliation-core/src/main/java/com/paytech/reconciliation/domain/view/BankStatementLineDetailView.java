package com.paytech.reconciliation.domain.view;

import java.util.List;

import com.paytech.reconciliation.core.dal.dataobject.BankMatch;
import com.paytech.reconciliation.core.dal.dataobject.BankStatement;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 银行流水详情：流水、所属对账单与银行账户、全部银行匹配（含预期银行流水）与未结异常。 */
@Getter
@AllArgsConstructor
public class BankStatementLineDetailView {
    /** 银行流水 */
    private final BankStatementLine line;

    /** 所属银行对账单 */
    private final BankStatement statement;

    /** 所属银行账户（流水币种） */
    private final CashAccount cashAccount;

    /** 全部银行匹配，按主键升序 */
    private final List<MatchItem> matches;

    /** 流水上的未结异常，按主键升序 */
    private final List<ReconException> openExceptions;

    /** 一条银行匹配与它分配到的预期银行流水。 */
    @Getter
    @AllArgsConstructor
    public static class MatchItem {
        /** 银行匹配 */
        private final BankMatch match;

        /** 预期银行流水 */
        private final ExpectedBankMovement expectedMovement;
    }
}
