package com.paytech.reconciliation.test.mocker.local;

import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.reconciliation.core.dal.dataobject.BankStatement;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccountMid;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.core.dal.mapper.BankStatementLineMapper;
import com.paytech.reconciliation.core.dal.mapper.BankStatementMapper;
import com.paytech.reconciliation.core.dal.mapper.CashAccountMapper;
import com.paytech.reconciliation.core.dal.mapper.ChannelStatementLineMapper;
import com.paytech.reconciliation.core.dal.mapper.ChannelStatementMapper;
import com.paytech.reconciliation.core.dal.mapper.ClearingAccountMapper;
import com.paytech.reconciliation.core.dal.mapper.ClearingAccountMidMapper;
import com.paytech.reconciliation.core.dal.mapper.ExpectedBankMovementMapper;
import com.paytech.reconciliation.core.dal.mapper.PlatformTxnMapper;
import com.paytech.reconciliation.core.dal.mapper.ReconExceptionMapper;

/**
 * 本地前置数据：以生产 Mapper 把 Builder 构造的 DataObject 插入 mor_reconciliation 并立即提交（无外层事务），返回同一对象供用例关联
 * <li>只做 Arrange，不调用被测业务命令；清理由 ReconciliationDataCleaner 在 AfterEach 统一执行。</li>
 */
@TestComponent
public class ReconciliationDataMocker {
    private final CashAccountMapper cashAccounts;

    private final PlatformTxnMapper platformTxns;

    private final BankStatementMapper bankStatements;

    private final ReconExceptionMapper reconExceptions;

    private final ClearingAccountMapper clearingAccounts;

    private final ChannelStatementMapper channelStatements;

    private final BankStatementLineMapper bankStatementLines;

    private final ClearingAccountMidMapper clearingAccountMids;

    private final ExpectedBankMovementMapper expectedMovements;

    private final ChannelStatementLineMapper channelStatementLines;

    /** 以生产 Mapper 构造。 */
    public ReconciliationDataMocker(CashAccountMapper cashAccounts, PlatformTxnMapper platformTxns,
        BankStatementMapper bankStatements, ReconExceptionMapper reconExceptions,
            ClearingAccountMapper clearingAccounts,
        ChannelStatementMapper channelStatements, BankStatementLineMapper bankStatementLines,
        ClearingAccountMidMapper clearingAccountMids, ExpectedBankMovementMapper expectedMovements,
        ChannelStatementLineMapper channelStatementLines) {
        this.cashAccounts = cashAccounts;
        this.platformTxns = platformTxns;
        this.bankStatements = bankStatements;
        this.reconExceptions = reconExceptions;
        this.clearingAccounts = clearingAccounts;
        this.channelStatements = channelStatements;
        this.bankStatementLines = bankStatementLines;
        this.clearingAccountMids = clearingAccountMids;
        this.expectedMovements = expectedMovements;
        this.channelStatementLines = channelStatementLines;
    }

    /** 插入清算账户。 */
    public ClearingAccount clearingAccount(ClearingAccount account) {
        clearingAccounts.insert(account);
        return account;
    }

    /** 插入清算账户的 MID 归属行。 */
    public ClearingAccountMid clearingAccountMid(ClearingAccountMid mid) {
        clearingAccountMids.insert(mid);
        return mid;
    }

    /** 插入银行账户。 */
    public CashAccount cashAccount(CashAccount account) {
        cashAccounts.insert(account);
        return account;
    }

    /** 插入平台流水。 */
    public PlatformTxn platformTxn(PlatformTxn txn) {
        platformTxns.insert(txn);
        return txn;
    }

    /** 插入渠道账单头与其行。 */
    public ChannelStatement channelStatement(ChannelStatement statement, List<ChannelStatementLine> lines) {
        channelStatements.insert(statement);
        if (!lines.isEmpty()) {
            channelStatementLines.insertBatch(lines);
        }
        return statement;
    }

    /** 插入银行对账单头与其流水。 */
    public BankStatement bankStatement(BankStatement statement, List<BankStatementLine> lines) {
        bankStatements.insert(statement);
        if (!lines.isEmpty()) {
            bankStatementLines.insertBatch(lines);
        }
        return statement;
    }

    /** 插入预期银行流水。 */
    public ExpectedBankMovement expectedMovement(ExpectedBankMovement movement) {
        expectedMovements.insert(movement);
        return movement;
    }

    /** 插入对账异常。 */
    public ReconException reconException(ReconException exception) {
        reconExceptions.insert(exception);
        return exception;
    }
}
