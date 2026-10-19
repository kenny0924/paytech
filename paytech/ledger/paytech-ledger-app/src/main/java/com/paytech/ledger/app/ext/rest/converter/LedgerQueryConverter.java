package com.paytech.ledger.app.ext.rest.converter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.ledger.api.response.AccountJournalLineResponseBody;
import com.paytech.ledger.api.response.AccountResponseBody;
import com.paytech.ledger.api.response.AccountingPeriodResponseBody;
import com.paytech.ledger.api.response.ChartOfAccountsListResponseBody;
import com.paytech.ledger.api.response.ChartOfAccountsResponseBody;
import com.paytech.ledger.api.response.CurrencyListResponseBody;
import com.paytech.ledger.api.response.CurrencyResponseBody;
import com.paytech.ledger.api.response.FinanceEntityResponseBody;
import com.paytech.ledger.api.response.FiscalCalendarResponseBody;
import com.paytech.ledger.api.response.JournalEntryDetailResponseBody;
import com.paytech.ledger.api.response.LedgerBookResponseBody;
import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.dal.dataobject.ChartOfAccounts;
import com.paytech.ledger.core.dal.dataobject.CurrencySpec;
import com.paytech.ledger.core.dal.dataobject.FinanceEntity;
import com.paytech.ledger.core.dal.dataobject.FiscalCalendar;
import com.paytech.ledger.core.enums.FinanceEntityStatus;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.domain.AccountQuery;
import com.paytech.ledger.domain.AccountingPeriodQuery;
import com.paytech.ledger.domain.FinanceEntityQuery;
import com.paytech.ledger.domain.FiscalCalendarQuery;
import com.paytech.ledger.domain.LedgerBookQuery;
import com.paytech.ledger.domain.view.AccountJournalLineView;
import com.paytech.ledger.domain.view.AccountView;
import com.paytech.ledger.domain.view.JournalEntryDetailView;
import com.paytech.ledger.domain.view.LedgerBookView;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

/** 账本与账务基础资料查询的 Query 参数解析与查询结果到 ResponseBody 的转换；ID 与金额输出为字符串。 */
@Component
public class LedgerQueryConverter {

    /** 解析账簿列表条件；空字符串视为不过滤。 */
    public LedgerBookQuery toLedgerBookQuery(String entityType, String entityId, String currency, Integer page,
        Integer size) {
        return new LedgerBookQuery(optionalCode(LedgerEntityType.class, entityType), blankToNull(entityId),
            blankToNull(currency), PagingRequestBody.of(page, size));
    }

    /** 解析账户列表条件。 */
    public AccountQuery toAccountQuery(String ledgerBookId, String accountCode, String ownerType, String ownerId,
        String balanceBucket, PagingRequestBody paging) {
        return new AccountQuery(Long.parseLong(ledgerBookId), blankToNull(accountCode),
            optionalCode(LedgerOwnerType.class, ownerType), blankToNull(ownerId),
            optionalCode(BalanceBucket.class, balanceBucket), paging);
    }

    /** 解析财务主体列表条件。 */
    public FinanceEntityQuery toFinanceEntityQuery(String status, Integer page, Integer size) {
        return new FinanceEntityQuery(optionalCode(FinanceEntityStatus.class, status), PagingRequestBody.of(page, size));
    }

    /** 解析财务日历列表条件；空字符串视为不过滤。 */
    public FiscalCalendarQuery toFiscalCalendarQuery(String entityType, String entityId, Integer page, Integer size) {
        return new FiscalCalendarQuery(optionalCode(LedgerEntityType.class, entityType), blankToNull(entityId),
            PagingRequestBody.of(page, size));
    }

    /** 解析日历下会计期间列表条件。 */
    public AccountingPeriodQuery toAccountingPeriodQuery(String fiscalCalendarId, Integer fiscalYear, Integer page,
        Integer size) {
        return new AccountingPeriodQuery(Long.parseLong(fiscalCalendarId), fiscalYear, PagingRequestBody.of(page, size));
    }

    /** 解析可选币种状态。 */
    public CurrencySpecStatus toCurrencyStatus(String status) {
        return optionalCode(CurrencySpecStatus.class, status);
    }

    /** 解析可选 ISO 日期；格式合法但不存在的日期（如 02-30）按参数错误拒绝。 */
    public LocalDate toDate(String name, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidParameterException(name + " 不是合法日期: " + value, e);
        }
    }

    /** 日期区间下限不得晚于上限。 */
    public void checkDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidParameterException("postingDateFrom 不能晚于 postingDateTo: " + from + " / " + to);
        }
    }

    /** 转换账簿列表项。 */
    public LedgerBookResponseBody toResponseBody(LedgerBookView view) {
        LedgerBookResponseBody body = new LedgerBookResponseBody();
        body.setLedgerBookId(view.getLedgerBookId().toString());
        body.setEntityType(view.getEntityType().getCode());
        body.setEntityId(view.getEntityId());
        body.setCurrency(view.getCurrency());
        body.setChartCode(view.getChartCode());
        body.setBookStatus(view.getBookStatus().getCode());
        return body;
    }

    /** 转换财务主体列表项。 */
    public FinanceEntityResponseBody toResponseBody(FinanceEntity entity) {
        FinanceEntityResponseBody body = new FinanceEntityResponseBody();
        body.setFinanceEntityId(entity.getId().toString());
        body.setEntityCode(entity.getEntityCode());
        body.setLegalPartyId(entity.getLegalPartyId().toString());
        body.setDisplayName(entity.getDisplayName());
        body.setStatus(entity.getStatus().getCode());
        body.setCreateTime(entity.getCreateTime());
        return body;
    }

    /** 转换财务日历列表项。 */
    public FiscalCalendarResponseBody toResponseBody(FiscalCalendar calendar) {
        FiscalCalendarResponseBody body = new FiscalCalendarResponseBody();
        body.setFiscalCalendarId(calendar.getId().toString());
        body.setEntityType(calendar.getEntityType().getCode());
        body.setEntityId(calendar.getEntityId());
        body.setCalendarCode(calendar.getCalendarCode());
        body.setTimezone(calendar.getTimezone());
        body.setYearStartMonth(calendar.getYearStartMonth());
        body.setCreateTime(calendar.getCreateTime());
        return body;
    }

    /** 转换会计期间列表项。 */
    public AccountingPeriodResponseBody toResponseBody(AccountingPeriod period) {
        AccountingPeriodResponseBody body = new AccountingPeriodResponseBody();
        body.setAccountingPeriodId(period.getId().toString());
        body.setFiscalCalendarId(period.getCalendarId().toString());
        body.setFiscalYear(period.getFiscalYear());
        body.setPeriodNo(period.getPeriodNo());
        body.setStartDate(period.getStartDate());
        body.setEndDate(period.getEndDate());
        body.setStatus(period.getStatus().getCode());
        body.setReopenCount(period.getReopenCount());
        return body;
    }

    /** 转换科目表列表。 */
    public ChartOfAccountsListResponseBody toChartsResponseBody(List<ChartOfAccounts> charts) {
        ChartOfAccountsListResponseBody body = new ChartOfAccountsListResponseBody();
        body.setItems(charts.stream().map(this::toChartBody).toList());
        return body;
    }

    /** 转换币种列表。 */
    public CurrencyListResponseBody toCurrenciesResponseBody(List<CurrencySpec> currencies) {
        CurrencyListResponseBody body = new CurrencyListResponseBody();
        body.setItems(currencies.stream().map(this::toCurrencyBody).toList());
        return body;
    }

    /** 转换账户列表项。 */
    public AccountResponseBody toResponseBody(AccountView view) {
        AccountResponseBody body = new AccountResponseBody();
        body.setAccountId(view.getAccountId().toString());
        body.setAccountCode(view.getAccountCode());
        body.setOwnerType(view.getOwnerType().getCode());
        body.setOwnerId(view.getOwnerId());
        body.setBalanceBucket(view.getBalanceBucket().getCode());
        body.setNormalSide(view.getNormalSide().getCode());
        body.setAccountStatus(view.getAccountStatus().getCode());
        body.setDebitTotal(amount(view.getDebitTotal()));
        body.setCreditTotal(amount(view.getCreditTotal()));
        body.setNetBalance(amount(view.getNetBalance()));
        body.setLastJournalSequence(view.getLastJournalSequence().toString());
        return body;
    }

    /** 转换账户流水行。 */
    public AccountJournalLineResponseBody toResponseBody(AccountJournalLineView view) {
        AccountJournalLineResponseBody body = new AccountJournalLineResponseBody();
        body.setJournalEntryId(view.getJournalEntryId().toString());
        body.setJournalSequence(view.getJournalSequence().toString());
        body.setPostingDate(view.getPostingDate());
        body.setEntryType(view.getEntryType().getCode());
        body.setLineNo(view.getLineNo());
        body.setEntrySide(view.getEntrySide().getCode());
        body.setAmount(amount(view.getAmount()));
        body.setBalanceAfter(amount(view.getBalanceAfter()));
        body.setInstructionType(view.getInstructionType());
        body.setOriginType(view.getOriginType().getCode());
        body.setSourceSystem(view.getSourceSystem());
        body.setSourceEventKey(view.getSourceEventKey());
        body.setAdjustmentNo(view.getAdjustmentNo());
        body.setAccountPostingId(text(view.getAccountPostingId()));
        body.setAccountPostingSequence(text(view.getAccountPostingSequence()));
        return body;
    }

    /** 转换分录详情。 */
    public JournalEntryDetailResponseBody toResponseBody(JournalEntryDetailView view) {
        JournalEntryDetailResponseBody body = new JournalEntryDetailResponseBody();
        body.setJournalEntryId(view.getJournalEntryId().toString());
        body.setLedgerBookId(view.getLedgerBookId().toString());
        body.setAccountingPeriodId(view.getAccountingPeriodId().toString());
        body.setJournalSequence(view.getJournalSequence().toString());
        body.setPostingDate(view.getPostingDate());
        body.setEntryType(view.getEntryType().getCode());
        body.setReversalOfEntryId(text(view.getReversalOfEntryId()));
        body.setInstructionId(view.getInstructionId().toString());
        body.setInstructionType(view.getInstructionType());
        body.setOriginType(view.getOriginType().getCode());
        body.setSourceSystem(view.getSourceSystem());
        body.setSourceEventKey(view.getSourceEventKey());
        body.setManualAdjustmentId(text(view.getManualAdjustmentId()));
        body.setAdjustmentNo(view.getAdjustmentNo());
        body.setAccountPostingId(text(view.getAccountPostingId()));
        body.setAccountPostingSequence(text(view.getAccountPostingSequence()));
        body.setLines(view.getLines().stream().map(this::toLineBody).toList());
        return body;
    }

    /** 转换分录行。 */
    JournalEntryDetailResponseBody.Line toLineBody(JournalEntryDetailView.Line line) {
        JournalEntryDetailResponseBody.Line body = new JournalEntryDetailResponseBody.Line();
        body.setLineNo(line.getLineNo());
        body.setAccountId(line.getAccountId().toString());
        body.setAccountCode(line.getAccountCode());
        body.setOwnerType(line.getOwnerType().getCode());
        body.setOwnerId(line.getOwnerId());
        body.setBalanceBucket(line.getBalanceBucket().getCode());
        body.setEntrySide(line.getEntrySide().getCode());
        body.setAmount(amount(line.getAmount()));
        body.setAccountEntryId(text(line.getAccountEntryId()));
        return body;
    }

    /** 转换科目表。 */
    ChartOfAccountsResponseBody toChartBody(ChartOfAccounts chart) {
        ChartOfAccountsResponseBody body = new ChartOfAccountsResponseBody();
        body.setChartId(chart.getId().toString());
        body.setChartCode(chart.getChartCode());
        body.setChartName(chart.getChartName());
        body.setStatus(chart.getStatus().getCode());
        return body;
    }

    /** 转换币种规则。 */
    CurrencyResponseBody toCurrencyBody(CurrencySpec spec) {
        CurrencyResponseBody body = new CurrencyResponseBody();
        body.setCurrency(spec.getCurrency());
        body.setMinorUnit(spec.getMinorUnit());
        body.setRoundingMode(spec.getRoundingMode());
        body.setStatus(spec.getStatus().getCode());
        return body;
    }

    /** 可选 Code 解析，空值不过滤。 */
    <E extends Enum<E> & CodeEnum> E optionalCode(Class<E> type, String code) {
        return code == null || code.isBlank() ? null : CodeEnumConverter.parse(type, code);
    }

    /** 空白字符串视为未提供。 */
    String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** 已按币种精度规范的金额输出普通十进制字符串。 */
    String amount(BigDecimal value) {
        return value.toPlainString();
    }

    /** 可空 Long 转十进制字符串。 */
    String text(Long value) {
        return value == null ? null : value.toString();
    }
}
