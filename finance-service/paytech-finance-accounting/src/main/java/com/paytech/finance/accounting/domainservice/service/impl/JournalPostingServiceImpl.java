package com.paytech.finance.accounting.domainservice.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.finance.accounting.core.dal.dataobject.Account;
import com.paytech.finance.accounting.core.dal.dataobject.AccountingInstruction;
import com.paytech.finance.accounting.core.dal.dataobject.AccountingPeriod;
import com.paytech.finance.accounting.core.dal.dataobject.BookSequence;
import com.paytech.finance.accounting.core.dal.dataobject.FiscalCalendar;
import com.paytech.finance.accounting.core.dal.dataobject.InstructionLine;
import com.paytech.finance.accounting.core.dal.dataobject.JournalEntry;
import com.paytech.finance.accounting.core.dal.dataobject.JournalLine;
import com.paytech.finance.accounting.core.dal.dataobject.LedgerBook;
import com.paytech.finance.accounting.core.enums.AccountStatus;
import com.paytech.finance.accounting.core.enums.BookStatus;
import com.paytech.finance.accounting.core.enums.EntrySide;
import com.paytech.finance.accounting.core.enums.InstructionStatus;
import com.paytech.finance.accounting.core.enums.NormalSide;
import com.paytech.finance.accounting.core.enums.PeriodStatus;
import com.paytech.finance.accounting.domain.JournalPostingContext;
import com.paytech.finance.accounting.domainservice.repository.AccountRepository;
import com.paytech.finance.accounting.domainservice.repository.AccountingInstructionRepository;
import com.paytech.finance.accounting.domainservice.repository.AccountingPeriodRepository;
import com.paytech.finance.accounting.domainservice.repository.BookSequenceRepository;
import com.paytech.finance.accounting.domainservice.repository.CurrencySpecRepository;
import com.paytech.finance.accounting.domainservice.repository.FiscalCalendarRepository;
import com.paytech.finance.accounting.domainservice.repository.JournalEntryRepository;
import com.paytech.finance.accounting.domainservice.repository.JournalLineRepository;
import com.paytech.finance.accounting.domainservice.repository.LedgerBookRepository;
import com.paytech.finance.accounting.domainservice.service.JournalPostingService;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;

import lombok.AllArgsConstructor;

/** 单账簿 Journal 过账；锁后全部使用当前读复验，不信任事务前的快照。 */
@Service
@AllArgsConstructor
public class JournalPostingServiceImpl implements JournalPostingService {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final AccountRepository accountRepository;
    private final LedgerBookRepository ledgerBookRepository;
    private final JournalLineRepository journalLineRepository;
    private final BookSequenceRepository bookSequenceRepository;
    private final CurrencySpecRepository currencySpecRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final FiscalCalendarRepository fiscalCalendarRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final AccountingInstructionRepository accountingInstructionRepository;

    /** 校验期间与账簿 → 取序号 → 锁账户并校验平衡 → 写分录 → 更新余额 → 指令 POSTED。 */
    @Override
    public JournalEntry post(JournalPostingContext context) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ErrorException("过账必须在调用方事务内执行");
        }
        AccountingInstruction instruction = context.getInstruction();
        if (instruction.getStatus() != InstructionStatus.VALIDATED) {
            throw new ErrorException("只有 VALIDATED 指令可以过账: " + instruction.getId());
        }
        LedgerBook book = checkPostable(context, instruction.getLedgerBookId());
        long journalSequence = nextJournalSequence(book.getId());
        Map<Long, Account> accounts = lockAccounts(book, context.getLines());
        checkBalanced(book, context.getLines());
        JournalEntry entry = writeJournal(context, journalSequence);
        applyBalances(accounts, context.getLines(), journalSequence);
        accountingInstructionRepository.updateStatus(instruction, InstructionStatus.POSTED);
        return entry;
    }

    /** 共享锁读取期间，校验期间开放、属于账簿主体日历且包含过账日期，并确认账簿开放。 */
    LedgerBook checkPostable(JournalPostingContext context, long ledgerBookId) {
        AccountingPeriod period = accountingPeriodRepository.lockSharedById(context.getAccountingPeriodId());
        if (period == null) {
            throw new BizCheckFailException("会计期间不存在: " + context.getAccountingPeriodId());
        }
        if (period.getStatus() != PeriodStatus.OPEN) {
            throw new BizCheckFailException("会计期间未开放: " + period.getId() + ", 状态 " + period.getStatus().getCode());
        }
        if (context.getPostingDate().isBefore(period.getStartDate())
            || context.getPostingDate().isAfter(period.getEndDate())) {
            throw new BizCheckFailException("过账日期不在会计期间内: " + context.getPostingDate() + ", 期间 " + period.getId());
        }
        LedgerBook book = ledgerBookRepository.findById(ledgerBookId);
        if (book == null || book.getBookStatus() != BookStatus.OPEN) {
            throw new BizCheckFailException("账簿不存在或未开放: " + ledgerBookId);
        }
        checkSameEntity(book, fiscalCalendarRepository.findById(period.getCalendarId()), period.getId());
        return book;
    }

    /** 期间所属日历必须属于账簿主体。 */
    void checkSameEntity(LedgerBook book, FiscalCalendar calendar, long periodId) {
        if (calendar == null || calendar.getEntityType() != book.getEntityType()
            || !calendar.getEntityId().equals(book.getEntityId())) {
            throw new BizCheckFailException("会计期间不属于账簿主体: 期间 " + periodId + ", 账簿 " + book.getId());
        }
    }

    /** 排他锁定账簿序号行并推进，返回本次分录序号。 */
    long nextJournalSequence(long ledgerBookId) {
        BookSequence sequence = bookSequenceRepository.lockByLedgerBook(ledgerBookId);
        if (sequence == null) {
            throw new ErrorException("账簿缺少序号行: " + ledgerBookId);
        }
        long journalSequence = sequence.getNextValue();
        bookSequenceRepository.advance(sequence);
        return journalSequence;
    }

    /** 按主键升序排他锁定全部账户，并确认账户存在、开放且属于本账簿。 */
    Map<Long, Account> lockAccounts(LedgerBook book, List<InstructionLine> lines) {
        List<Long> ids = lines.stream().map(InstructionLine::getAccountId).distinct().sorted().toList();
        Map<Long, Account> accounts = new HashMap<>();
        for (Account account : accountRepository.lockByIds(ids)) {
            accounts.put(account.getId(), account);
        }
        for (Long id : ids) {
            Account account = accounts.get(id);
            if (account == null || !account.getLedgerBookId().equals(book.getId())) {
                throw new BizCheckFailException("账户不存在或不属于账簿: 账户 " + id + ", 账簿 " + book.getId());
            }
            if (account.getAccountStatus() != AccountStatus.ACTIVE) {
                throw new BizCheckFailException("账户未开放过账: " + id + ", 状态 " + account.getAccountStatus().getCode());
            }
        }
        return accounts;
    }

    /** 每行金额大于零且不超过币种精度，借贷合计相等且大于零。 */
    void checkBalanced(LedgerBook book, List<InstructionLine> lines) {
        int minorUnit = currencySpecRepository.findByCurrency(book.getCurrency()).getMinorUnit();
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (InstructionLine line : lines) {
            BigDecimal amount = line.getAmount();
            if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > minorUnit) {
                throw new BizCheckFailException("指令行金额非法: 行 " + line.getLineNo() + ", 金额 " + amount.toPlainString());
            }
            debit = line.getEntrySide() == EntrySide.DEBIT ? debit.add(amount) : debit;
            credit = line.getEntrySide() == EntrySide.CREDIT ? credit.add(amount) : credit;
        }
        if (debit.signum() <= 0 || debit.compareTo(credit) != 0) {
            throw new BizCheckFailException("指令借贷不平衡: 借方 " + debit.toPlainString() + ", 贷方 " + credit.toPlainString());
        }
    }

    /** 写入不可变分录头和与指令行一一对应的分录行。 */
    JournalEntry writeJournal(JournalPostingContext context, long journalSequence) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        JournalEntry entry = new JournalEntry();
        entry.setId(idGenerator.nextId());
        entry.setLedgerBookId(context.getInstruction().getLedgerBookId());
        entry.setAccountingPeriodId(context.getAccountingPeriodId());
        entry.setInstructionId(context.getInstruction().getId());
        entry.setJournalSequence(journalSequence);
        entry.setPostingDate(context.getPostingDate());
        entry.setEntryType(context.getEntryType());
        entry.setCreateTime(now);
        journalEntryRepository.insert(entry);
        List<JournalLine> lines = new ArrayList<>();
        for (InstructionLine source : context.getLines()) {
            lines.add(journalLine(entry, source, now));
        }
        journalLineRepository.insertBatch(lines);
        return entry;
    }

    /** 由指令行构造分录行。 */
    JournalLine journalLine(JournalEntry entry, InstructionLine source, Instant now) {
        JournalLine line = new JournalLine();
        line.setId(idGenerator.nextId());
        line.setJournalEntryId(entry.getId());
        line.setLineNo(source.getLineNo());
        line.setInstructionLineId(source.getId());
        line.setAccountId(source.getAccountId());
        line.setEntrySide(source.getEntrySide());
        line.setAmount(source.getAmount());
        line.setCreateTime(now);
        return line;
    }

    /** 按主键升序逐账户累加本分录金额，并按账户冻结的正常方向重算净余额。 */
    void applyBalances(Map<Long, Account> accounts, List<InstructionLine> lines, long journalSequence) {
        for (Long accountId : accounts.keySet().stream().sorted().toList()) {
            Account account = accounts.get(accountId);
            BigDecimal debit = account.getDebitTotal().add(sum(lines, accountId, EntrySide.DEBIT));
            BigDecimal credit = account.getCreditTotal().add(sum(lines, accountId, EntrySide.CREDIT));
            BigDecimal net = account.getNormalSide() == NormalSide.DEBIT ? debit.subtract(credit) : credit.subtract(debit);
            accountRepository.applyPosting(account, debit, credit, net, journalSequence);
        }
    }

    /** 汇总一个账户在本分录中某一方向的金额。 */
    BigDecimal sum(List<InstructionLine> lines, long accountId, EntrySide side) {
        return lines.stream().filter(line -> line.getAccountId() == accountId && line.getEntrySide() == side)
            .map(InstructionLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
