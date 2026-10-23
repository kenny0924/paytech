package com.paytech.ledger.domainservice.service.impl;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.ChartOfAccounts;
import com.paytech.ledger.core.dal.dataobject.FinanceEntity;
import com.paytech.ledger.core.dal.dataobject.FiscalCalendar;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.enums.ChartAccountStatus;
import com.paytech.ledger.core.enums.FinanceEntityStatus;
import com.paytech.ledger.domain.AccountQuery;
import com.paytech.ledger.domain.AccountingPeriodQuery;
import com.paytech.ledger.domain.FinanceEntityQuery;
import com.paytech.ledger.domain.FiscalCalendarQuery;
import com.paytech.ledger.domain.LedgerBookQuery;
import com.paytech.ledger.domain.view.AccountJournalLineView;
import com.paytech.ledger.domain.view.ChartAccountChangeView;
import com.paytech.ledger.domain.view.AccountView;
import com.paytech.ledger.domain.view.JournalEntryDetailView;
import com.paytech.ledger.domain.view.LedgerBookView;
import com.paytech.ledger.domainservice.repository.AccountingPeriodRepository;
import com.paytech.ledger.domainservice.repository.ChartAccountChangeRepository;
import com.paytech.ledger.domainservice.repository.ChartAccountRepository;
import com.paytech.ledger.domainservice.repository.ChartOfAccountsRepository;
import com.paytech.ledger.domainservice.repository.FinanceEntityRepository;
import com.paytech.ledger.domainservice.repository.FiscalCalendarRepository;
import com.paytech.ledger.domainservice.repository.JournalEntryRepository;
import com.paytech.ledger.domainservice.repository.JournalLineRepository;
import com.paytech.ledger.domainservice.repository.LedgerAccountRepository;
import com.paytech.ledger.domainservice.repository.LedgerBookRepository;
import com.paytech.ledger.domainservice.service.CurrencyRuleService;
import com.paytech.ledger.domainservice.service.LedgerQueryService;

import lombok.AllArgsConstructor;

/** 账簿只读查询：先确认归属，再读取组合结果并按币种精度规范金额。 */
@Service
@AllArgsConstructor
public class LedgerQueryServiceImpl implements LedgerQueryService {
    private final CurrencyRuleService currencyRuleService;
    private final ChartAccountRepository chartAccountRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerBookRepository ledgerBookRepository;
    private final JournalLineRepository journalLineRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final FinanceEntityRepository financeEntityRepository;
    private final FiscalCalendarRepository fiscalCalendarRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final ChartAccountChangeRepository chartAccountChangeRepository;

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

    /** 三个余额字段按币种精度补齐。 */
    void scale(AccountView account, int minorUnit) {
        account.setDebitTotal(account.getDebitTotal().setScale(minorUnit));
        account.setCreditTotal(account.getCreditTotal().setScale(minorUnit));
        account.setNetBalance(account.getNetBalance().setScale(minorUnit));
    }

    /** 分页读取财务主体。 */
    @Override
    public PagingResponseBody<FinanceEntity> pageFinanceEntities(FinanceEntityQuery query) {
        return page(financeEntityRepository.findPage(query), query.getPaging(),
            financeEntityRepository.countPage(query));
    }

    /** 法律 Party 唯一定位财务主体；CLOSED 与不存在一样拒绝，不能用于新交易。 */
    @Override
    public FinanceEntity getActiveFinanceEntityByParty(long legalPartyId) {
        FinanceEntity entity = financeEntityRepository.findByLegalPartyId(legalPartyId);
        if (entity == null || entity.getStatus() != FinanceEntityStatus.ACTIVE) {
            throw new FailException(LedgerSetupErrorCode.FINANCE_ENTITY_NOT_FOUND);
        }
        return entity;
    }

    /** 分页读取财务日历。 */
    @Override
    public PagingResponseBody<FiscalCalendar> pageFiscalCalendars(FiscalCalendarQuery query) {
        return page(fiscalCalendarRepository.findPage(query), query.getPaging(),
            fiscalCalendarRepository.countPage(query));
    }

    /** 日历存在后分页读取期间。 */
    @Override
    public PagingResponseBody<AccountingPeriod> pageAccountingPeriods(AccountingPeriodQuery query) {
        if (fiscalCalendarRepository.findById(query.getFiscalCalendarId()) == null) {
            throw new FailException(LedgerSetupErrorCode.FISCAL_CALENDAR_NOT_FOUND);
        }
        return page(accountingPeriodRepository.findPage(query), query.getPaging(),
            accountingPeriodRepository.countPage(query));
    }

    /** 全部科目表数量有限，不分页。 */
    @Override
    public List<ChartOfAccounts> listChartsOfAccounts() {
        return chartOfAccountsRepository.findAll();
    }

    /** 科目表存在后读取其科目；科目表状态不过滤，供页面展示全部已发布科目表。 */
    @Override
    public List<ChartAccount> listChartAccountsByChart(String chartCode, Boolean postingAllowed,
        ChartAccountStatus accountStatus) {
        return chartAccountRepository.findByChart(getChart(chartCode).getId(), postingAllowed, accountStatus);
    }

    /** 科目表存在、限定科目时科目属于该科目表，再分页读取变更记录。 */
    @Override
    public PagingResponseBody<ChartAccountChangeView> pageChartAccountChanges(String chartCode, Long chartAccountId,
        PagingRequestBody paging) {
        ChartOfAccounts chart = getChart(chartCode);
        if (chartAccountId != null) {
            ChartAccount account = chartAccountRepository.findById(chartAccountId);
            if (account == null || !account.getChartId().equals(chart.getId())) {
                throw new FailException(LedgerSetupErrorCode.CHART_ACCOUNT_NOT_FOUND);
            }
        }
        return page(chartAccountChangeRepository.findPage(chart.getId(), chartAccountId, paging), paging,
            chartAccountChangeRepository.countPage(chart.getId(), chartAccountId));
    }

    /** 读取科目表，不存在时以 CHART_NOT_ACTIVE 拒绝。 */
    ChartOfAccounts getChart(String chartCode) {
        ChartOfAccounts chart = chartOfAccountsRepository.findByChartCode(chartCode);
        if (chart == null) {
            throw new FailException(LedgerSetupErrorCode.CHART_NOT_ACTIVE);
        }
        return chart;
    }

    /** 读取账簿，不存在时以 LEDGER_BOOK_NOT_FOUND 拒绝（LedgerBookQueryApi 的页面区分处理）。 */
    LedgerBook getBook(long ledgerBookId) {
        LedgerBook book = ledgerBookRepository.findById(ledgerBookId);
        if (book == null) {
            throw new FailException(LedgerSetupErrorCode.LEDGER_BOOK_NOT_FOUND);
        }
        return book;
    }

    /** 账簿币种的小数位。 */
    int minorUnit(LedgerBook book) {
        return minorUnit(book.getCurrency());
    }

    /** 币种的小数位。 */
    int minorUnit(String currency) {
        return currencyRuleService.requireUsable(currency).getMinorUnit();
    }

    /** 组装分页结果。 */
    <T> PagingResponseBody<T> page(List<T> items, PagingRequestBody paging, long total) {
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }
}
