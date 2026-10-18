package com.paytech.ledger.test.checker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.ledger.core.dal.dataobject.JournalEntry;
import com.paytech.ledger.core.dal.dataobject.JournalLine;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.core.enums.EntrySide;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.test.dal.AccountingTestMapper;

/** 从真实数据库读取账户、分录与序号，只读断言；预期值由用例给出，账户余额由本类从 Journal 独立聚合。 */
@TestComponent
public class LedgerChecker {
    private final AccountingTestMapper mapper;

    public LedgerChecker(AccountingTestMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 断言账户按全部分录聚合的累计借贷、净余额与最后序号
     * <li>第 11b 步起 ledger 不保存余额投影（design/04 §3.1），原投影断言改为分录聚合断言，预期值不变。</li>
     */
    public LedgerChecker assertAccount(long accountId, String debitTotal, String creditTotal, String netBalance,
        long lastJournalSequence) {
        LedgerAccount account = mapper.selectAccount(accountId);
        assertNotNull(account, "账户不存在: " + accountId);
        Map<String, Object> totals = mapper.selectAccountJournalTotals(accountId);
        BigDecimal debit = (BigDecimal)totals.get("debit_total");
        BigDecimal credit = (BigDecimal)totals.get("credit_total");
        BigDecimal net = account.getNormalSide() == NormalSide.DEBIT ? debit.subtract(credit) : credit.subtract(debit);
        assertAll("账户 " + accountId,
            () -> assertEquals(0, new BigDecimal(debitTotal).compareTo(debit), "debitTotal"),
            () -> assertEquals(0, new BigDecimal(creditTotal).compareTo(credit), "creditTotal"),
            () -> assertEquals(0, new BigDecimal(netBalance).compareTo(net), "netBalance"),
            () -> assertEquals(lastJournalSequence, ((Number)totals.get("last_journal_sequence")).longValue(),
                "lastJournalSequence"));
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
