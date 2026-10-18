package com.paytech.ledger.domainservice.service.impl;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.paytech.account.api.enums.OwnerType;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.domain.AccountQuery;
import com.paytech.ledger.domain.LedgerBookQuery;
import com.paytech.ledger.domain.view.AccountJournalLineView;
import com.paytech.ledger.domain.view.AccountView;
import com.paytech.ledger.domain.view.JournalEntryDetailView;
import com.paytech.ledger.domain.view.LedgerBookView;
import com.paytech.ledger.domainservice.repository.ChartAccountRepository;
import com.paytech.ledger.domainservice.repository.CurrencySpecRepository;
import com.paytech.ledger.domainservice.repository.JournalEntryRepository;
import com.paytech.ledger.domainservice.repository.JournalLineRepository;
import com.paytech.ledger.domainservice.repository.LedgerAccountRepository;
import com.paytech.ledger.domainservice.repository.LedgerBookRepository;
import com.paytech.ledger.domainservice.service.LedgerQueryService;

import lombok.AllArgsConstructor;

/** 账簿只读查询：先确认归属，再读取组合结果并按币种精度规范金额。 */
@Service
@AllArgsConstructor
public class LedgerQueryServiceImpl implements LedgerQueryService {
    /** account 服务维护余额桶的 Owner 类型，日终核对按它们比对（design/04 §7）。 */
    static final List<OwnerType> OWNER_BALANCE_TYPES = List.of(OwnerType.CLIENT, OwnerType.LOCAL_PARTNER,
        OwnerType.CHANNEL_ACCOUNT, OwnerType.CASH_ACCOUNT);

    private final ChartAccountRepository chartAccountRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerBookRepository ledgerBookRepository;
    private final JournalLineRepository journalLineRepository;
    private final CurrencySpecRepository currencySpecRepository;
    private final JournalEntryRepository journalEntryRepository;

    /** 账簿列表无金额，直接分页返回。 */
    @Override
    public PagingResponseBody<LedgerBookView> pageLedgerBooks(LedgerBookQuery query) {
        return page(ledgerBookRepository.findPage(query), query.getPaging(), ledgerBookRepository.countPage(query));
    }

    /** 账簿存在后分页读取账户（余额由分录聚合），并把三个余额字段按币种精度补齐。 */
    @Override
    public PagingResponseBody<AccountView> pageAccounts(AccountQuery query) {
        int minorUnit = minorUnit(getBook(query.getLedgerBookId()));
        List<AccountView> accounts = ledgerAccountRepository.findPage(query);
        accounts.forEach(account -> scale(account, minorUnit));
        return page(accounts, query.getPaging(), ledgerAccountRepository.countPage(query));
    }

    /** 账户必须属于账簿；金额与累计余额按币种精度补齐。 */
    @Override
    public PagingResponseBody<AccountJournalLineView> pageAccountJournalLines(long ledgerBookId, long accountId,
        LocalDate from, LocalDate to, PagingRequestBody paging) {
        int minorUnit = minorUnit(getBook(ledgerBookId));
        LedgerAccount account = ledgerAccountRepository.findById(accountId);
        if (account == null || account.getLedgerBookId() != ledgerBookId) {
            throw new BizCheckFailException("账户不存在或不属于账簿: 账户 " + accountId + ", 账簿 " + ledgerBookId);
        }
        List<AccountJournalLineView> lines = journalLineRepository.findAccountLines(accountId, from, to, paging);
        lines.forEach(line -> {
            line.setAmount(line.getAmount().setScale(minorUnit));
            line.setBalanceAfter(line.getBalanceAfter().setScale(minorUnit));
        });
        return page(lines, paging, journalLineRepository.countAccountLines(accountId, from, to));
    }

    /** 分录必须属于账簿；分录行金额按币种精度补齐。 */
    @Override
    public JournalEntryDetailView getJournalEntry(long ledgerBookId, long journalEntryId) {
        int minorUnit = minorUnit(getBook(ledgerBookId));
        JournalEntryDetailView entry = journalEntryRepository.findDetail(journalEntryId);
        if (entry == null || entry.getLedgerBookId() != ledgerBookId) {
            throw new BizCheckFailException("分录不存在或不属于账簿: 分录 " + journalEntryId + ", 账簿 " + ledgerBookId);
        }
        List<JournalEntryDetailView.Line> lines = journalLineRepository.findEntryLines(journalEntryId);
        lines.forEach(line -> line.setAmount(line.getAmount().setScale(minorUnit)));
        entry.setLines(lines);
        return entry;
    }

    /** 账簿与科目表代码；不存在时业务拒绝。 */
    @Override
    public LedgerBookView getLedgerBook(long ledgerBookId) {
        LedgerBookView book = ledgerBookRepository.findView(ledgerBookId);
        if (book == null) {
            throw new BizCheckFailException("账簿不存在: " + ledgerBookId);
        }
        return book;
    }

    /** 主键数量受限；不存在的主键不返回。 */
    @Override
    public List<ChartAccount> listChartAccounts(List<Long> chartAccountIds) {
        if (chartAccountIds.isEmpty() || chartAccountIds.size() > MAX_CHART_ACCOUNT_IDS) {
            throw new InvalidParameterException("科目主键数量必须在 1 到 " + MAX_CHART_ACCOUNT_IDS + " 之间");
        }
        return chartAccountRepository.findByIds(chartAccountIds.stream().distinct().sorted().toList());
    }

    /** 账簿存在后读取全部 Owner 账户的分录聚合余额，并按币种精度补齐。 */
    @Override
    public List<AccountView> listOwnerAccountTotals(long ledgerBookId) {
        int minorUnit = minorUnit(getBook(ledgerBookId));
        List<AccountView> accounts = ledgerAccountRepository.findOwnerTotals(ledgerBookId, OWNER_BALANCE_TYPES);
        accounts.forEach(account -> scale(account, minorUnit));
        return accounts;
    }

    /** 三个余额字段按币种精度补齐。 */
    void scale(AccountView account, int minorUnit) {
        account.setDebitTotal(account.getDebitTotal().setScale(minorUnit));
        account.setCreditTotal(account.getCreditTotal().setScale(minorUnit));
        account.setNetBalance(account.getNetBalance().setScale(minorUnit));
    }

    /** 读取账簿，不存在时业务拒绝。 */
    LedgerBook getBook(long ledgerBookId) {
        LedgerBook book = ledgerBookRepository.findById(ledgerBookId);
        if (book == null) {
            throw new BizCheckFailException("账簿不存在: " + ledgerBookId);
        }
        return book;
    }

    /** 账簿币种的小数位。 */
    int minorUnit(LedgerBook book) {
        return currencySpecRepository.findByCurrency(book.getCurrency()).getMinorUnit();
    }

    /** 组装分页结果。 */
    <T> PagingResponseBody<T> page(List<T> items, PagingRequestBody paging, long total) {
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }
}
