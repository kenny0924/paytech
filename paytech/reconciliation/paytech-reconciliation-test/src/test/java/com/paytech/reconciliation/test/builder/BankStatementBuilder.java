package com.paytech.reconciliation.test.builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import com.paytech.reconciliation.api.enums.BankStatementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.core.dal.dataobject.BankStatement;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

/** 银行对账单头（t_bank_statement）与银行流水（t_bank_statement_line）的合法模板：已导入（IMPORTED），流水默认 UNMATCHED。 */
public final class BankStatementBuilder extends DataObjectBuilder<BankStatement, BankStatementBuilder> {

    private BankStatementBuilder(BankStatement statement) {
        super(statement);
    }

    /** IMPORTED 对账单：期间为当天，期初、期末余额由用例按恒等式给出。 */
    public static BankStatementBuilder imported(CashAccount account, String statementRef, String openingBalance,
        String closingBalance, int lineCount) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        BankStatement statement = new BankStatement();
        statement.setId(ReconciliationTestConstants.nextId());
        statement.setCashAccountId(account.getId());
        statement.setStatementRef(statementRef);
        statement.setPeriodStart(today);
        statement.setPeriodEnd(today);
        statement.setCurrency(account.getCurrency());
        statement.setOpeningBalance(new BigDecimal(openingBalance));
        statement.setClosingBalance(new BigDecimal(closingBalance));
        statement.setDeclaredLineCount(lineCount);
        statement.setImportedLineCount(lineCount);
        statement.setDuplicateLineCount(0);
        statement.setFileName(statementRef + ".csv");
        statement.setFileContentHash("b".repeat(64));
        statement.setStatus(BankStatementStatus.IMPORTED);
        statement.setMakerId(ReconciliationTestConstants.MAKER_ID);
        statement.setVersion(1L);
        statement.setCreateTime(now);
        statement.setUpdateTime(now);
        return new BankStatementBuilder(statement);
    }

    /** 未匹配的银行流水；起息日取对账单期间结束日。 */
    public static BankStatementLine line(BankStatement statement, int lineNo, String bankTransactionRef,
        SettlementDirection direction, String amount) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        BankStatementLine line = new BankStatementLine();
        line.setId(ReconciliationTestConstants.nextId());
        line.setStatementId(statement.getId());
        line.setCashAccountId(statement.getCashAccountId());
        line.setLineNo(lineNo);
        line.setBankTransactionRef(bankTransactionRef);
        line.setValueDate(statement.getPeriodEnd());
        line.setDirection(direction);
        line.setAmount(new BigDecimal(amount));
        line.setMatchedAmount(BigDecimal.ZERO);
        line.setMatchStatus(LineMatchStatus.UNMATCHED);
        line.setVersion(1L);
        line.setCreateTime(now);
        line.setUpdateTime(now);
        return line;
    }

    /** 返回自身。 */
    @Override
    protected BankStatementBuilder self() {
        return this;
    }
}
