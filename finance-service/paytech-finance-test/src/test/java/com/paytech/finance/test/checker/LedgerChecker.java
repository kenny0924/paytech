package com.paytech.finance.test.checker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.finance.accounting.core.dal.dataobject.Account;
import com.paytech.finance.accounting.core.dal.dataobject.JournalEntry;
import com.paytech.finance.accounting.core.dal.dataobject.JournalLine;
import com.paytech.finance.accounting.core.enums.EntrySide;
import com.paytech.finance.accounting.core.enums.NormalSide;
import com.paytech.finance.test.dal.AccountingTestMapper;

/** 从真实数据库读取账户、分录与序号，只读断言；预期值由用例给出，Journal 重算由本类独立计算。 */
@TestComponent
public class LedgerChecker {
    private final AccountingTestMapper mapper;

    public LedgerChecker(AccountingTestMapper mapper) {
        this.mapper = mapper;
    }

    /** 断言账户累计借贷、净余额与最后序号。 */
    public LedgerChecker assertAccount(long accountId, String debitTotal, String creditTotal, String netBalance,
        long lastJournalSequence) {
        Account account = mapper.selectAccount(accountId);
        assertNotNull(account, "账户不存在: " + accountId);
        assertAll("账户 " + accountId,
            () -> assertEquals(0, new BigDecimal(debitTotal).compareTo(account.getDebitTotal()), "debitTotal"),
            () -> assertEquals(0, new BigDecimal(creditTotal).compareTo(account.getCreditTotal()), "creditTotal"),
            () -> assertEquals(0, new BigDecimal(netBalance).compareTo(account.getNetBalance()), "netBalance"),
            () -> assertEquals(lastJournalSequence, account.getLastJournalSequence(), "lastJournalSequence"));
        return this;
    }

    /** 从账户全部分录行独立重算借贷累计与净余额，必须等于余额投影。 */
    public LedgerChecker assertProjectionMatchesJournal(long accountId) {
        Account account = mapper.selectAccount(accountId);
        List<JournalLine> lines = mapper.selectAccountJournalLines(accountId);
        BigDecimal debit = total(lines, EntrySide.DEBIT);
        BigDecimal credit = total(lines, EntrySide.CREDIT);
        BigDecimal net = account.getNormalSide() == NormalSide.DEBIT ? debit.subtract(credit) : credit.subtract(debit);
        assertAll("账户 " + accountId + " 投影与 Journal 重算",
            () -> assertEquals(0, debit.compareTo(account.getDebitTotal())),
            () -> assertEquals(0, credit.compareTo(account.getCreditTotal())),
            () -> assertEquals(0, net.compareTo(account.getNetBalance())));
        return this;
    }

    /** 断言账簿分录序号恰为 1..n，且每条分录借贷相等、大于零。 */
    public LedgerChecker assertBalancedJournals(long ledgerBookId, int expectedCount) {
        List<JournalEntry> entries = mapper.selectJournalEntries(ledgerBookId);
        assertEquals(expectedCount, entries.size(), "账簿分录数量");
        for (int i = 0; i < entries.size(); i++) {
            JournalEntry entry = entries.get(i);
            assertEquals(i + 1L, entry.getJournalSequence(), "分录序号应连续");
            List<JournalLine> lines = mapper.selectJournalLines(entry.getId());
            BigDecimal debit = total(lines, EntrySide.DEBIT);
            assertEquals(1, debit.signum(), "分录借方合计应大于零: " + entry.getId());
            assertEquals(0, debit.compareTo(total(lines, EntrySide.CREDIT)), "分录借贷应相等: " + entry.getId());
        }
        assertEquals(expectedCount + 1L, mapper.selectNextSequence(ledgerBookId), "下一序号");
        return this;
    }

    /** 读取账簿全部分录头。 */
    public List<JournalEntry> journalEntries(long ledgerBookId) {
        return mapper.selectJournalEntries(ledgerBookId);
    }

    /** 读取分录全部分录行。 */
    public List<JournalLine> journalLines(long journalEntryId) {
        return mapper.selectJournalLines(journalEntryId);
    }

    /** 汇总一个方向的金额。 */
    BigDecimal total(List<JournalLine> lines, EntrySide side) {
        return lines.stream().filter(line -> line.getEntrySide() == side).map(JournalLine::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
