package com.paytech.ledger.test.mocker.local;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.dal.dataobject.BookSequence;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.ChartOfAccounts;
import com.paytech.ledger.core.dal.dataobject.CurrencySpec;
import com.paytech.ledger.core.dal.dataobject.FinanceEntity;
import com.paytech.ledger.core.dal.dataobject.FiscalCalendar;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.dal.mapper.AccountingPeriodMapper;
import com.paytech.ledger.core.dal.mapper.BookSequenceMapper;
import com.paytech.ledger.core.dal.mapper.ChartAccountMapper;
import com.paytech.ledger.core.dal.mapper.FinanceEntityMapper;
import com.paytech.ledger.core.dal.mapper.FiscalCalendarMapper;
import com.paytech.ledger.core.dal.mapper.LedgerAccountMapper;
import com.paytech.ledger.core.dal.mapper.LedgerBookMapper;
import com.paytech.ledger.core.enums.AccountClass;
import com.paytech.ledger.core.enums.AccountStatus;
import com.paytech.ledger.core.enums.BookStatus;
import com.paytech.ledger.core.enums.FinanceEntityStatus;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.core.enums.ReferenceDataStatus;
import com.paytech.ledger.test.dal.AccountingTestMapper;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

/**
 * 通过 Mapper 直接插入账簿、日历、期间与账户前置事实，不调用被测 HTTP 入口
 * <li>科目使用初始化 DML 的 STANDARD 科目表；主键由进程 Snowflake 生成器分配。</li>
 */
@TestComponent
public class LedgerMocker {
    /** 初始化 DML 中 STANDARD 科目表的固定主键。 */
    public static final long STANDARD_CHART_ID = 2001L;

    /** 测试主体统一使用的财务日历时区。 */
    public static final String TIMEZONE = "Asia/Dubai";

    private final IdGenerator idGenerator;
    private final LedgerAccountMapper ledgerAccountMapper;
    private final LedgerBookMapper ledgerBookMapper;
    private final AccountingTestMapper testMapper;
    private final ChartAccountMapper chartAccountMapper;
    private final BookSequenceMapper bookSequenceMapper;
    private final FinanceEntityMapper financeEntityMapper;
    private final FiscalCalendarMapper fiscalCalendarMapper;
    private final AccountingPeriodMapper accountingPeriodMapper;

    public LedgerMocker(IdGenerator idGenerator, LedgerAccountMapper ledgerAccountMapper, LedgerBookMapper ledgerBookMapper,
        AccountingTestMapper testMapper, ChartAccountMapper chartAccountMapper, BookSequenceMapper bookSequenceMapper,
        FinanceEntityMapper financeEntityMapper, FiscalCalendarMapper fiscalCalendarMapper,
        AccountingPeriodMapper accountingPeriodMapper) {
        this.idGenerator = idGenerator;
        this.ledgerAccountMapper = ledgerAccountMapper;
        this.ledgerBookMapper = ledgerBookMapper;
        this.testMapper = testMapper;
        this.chartAccountMapper = chartAccountMapper;
        this.bookSequenceMapper = bookSequenceMapper;
        this.financeEntityMapper = financeEntityMapper;
        this.fiscalCalendarMapper = fiscalCalendarMapper;
        this.accountingPeriodMapper = accountingPeriodMapper;
    }

    /** 插入 OE 账簿（序号从 1 开始）、日历及一个覆盖今天的 OPEN 期间。 */
    public LedgerFixture mockOperatingEntityLedger(String entityNo, String currency) {
        return mockEntityLedger(LedgerEntityType.OPERATING_ENTITY, entityNo, currency);
    }

    /** 插入 Finance Entity 账簿、日历及一个覆盖今天的 OPEN 期间；主体标识为财务主体主键十进制字符串。 */
    public LedgerFixture mockFinanceEntityLedger(long financeEntityId, String currency) {
        return mockEntityLedger(LedgerEntityType.FINANCE_ENTITY, String.valueOf(financeEntityId), currency);
    }

    /** 插入主体账簿（序号从 1 开始）、日历及一个覆盖今天的 OPEN 期间。 */
    LedgerFixture mockEntityLedger(LedgerEntityType entityType, String entityId, String currency) {
        long calendarId = mockCalendar(entityType, entityId);
        LocalDate today = LocalDate.now();
        long periodId = mockPeriod(calendarId, today.getYear(), today.getMonthValue(), today.withDayOfMonth(1),
            today.withDayOfMonth(today.lengthOfMonth()), PeriodStatus.OPEN);
        long bookId = mockBook(entityType, entityId, currency, STANDARD_CHART_ID);
        return new LedgerFixture(bookId, calendarId, periodId, entityId, currency);
    }

    /** 插入 OPEN 账簿及其序号行。 */
    public long mockBook(LedgerEntityType entityType, String entityId, String currency, long chartId) {
        LedgerBook book = new LedgerBook();
        book.setId(idGenerator.nextId());
        book.setEntityType(entityType);
        book.setEntityId(entityId);
        book.setCurrency(currency);
        book.setChartId(chartId);
        book.setBookStatus(BookStatus.OPEN);
        book.setVersion(0L);
        book.setCreateTime(now());
        book.setUpdateTime(now());
        ledgerBookMapper.insert(book);
        BookSequence sequence = new BookSequence();
        sequence.setId(idGenerator.nextId());
        sequence.setLedgerBookId(book.getId());
        sequence.setNextValue(1L);
        sequence.setCreateTime(now());
        sequence.setUpdateTime(now());
        bookSequenceMapper.insert(sequence);
        return book.getId();
    }

    /** 插入主体财务日历。 */
    public long mockCalendar(LedgerEntityType entityType, String entityId) {
        FiscalCalendar calendar = new FiscalCalendar();
        calendar.setId(idGenerator.nextId());
        calendar.setEntityType(entityType);
        calendar.setEntityId(entityId);
        calendar.setCalendarCode("CAL-" + entityId);
        calendar.setTimezone(TIMEZONE);
        calendar.setYearStartMonth(1);
        calendar.setVersion(0L);
        calendar.setCreateTime(now());
        calendar.setUpdateTime(now());
        fiscalCalendarMapper.insert(calendar);
        return calendar.getId();
    }

    /** 插入指定状态的会计期间。 */
    public long mockPeriod(long calendarId, int fiscalYear, int periodNo, LocalDate start, LocalDate end,
        PeriodStatus status) {
        AccountingPeriod period = new AccountingPeriod();
        period.setId(idGenerator.nextId());
        period.setCalendarId(calendarId);
        period.setFiscalYear(fiscalYear);
        period.setPeriodNo(periodNo);
        period.setStartDate(start);
        period.setEndDate(end);
        period.setStatus(status);
        period.setReopenCount(0);
        period.setVersion(0L);
        period.setCreateTime(now());
        period.setUpdateTime(now());
        accountingPeriodMapper.insert(period);
        return period.getId();
    }

    /** 在 STANDARD 科目下插入 ACTIVE 账户身份，正常方向取自科目；余额由分录聚合。 */
    public long mockAccount(long ledgerBookId, String accountCode, LedgerOwnerType ownerType, String ownerId,
        BalanceBucket bucket) {
        ChartAccount chartAccount = chartAccountMapper.selectByChartAndCode(STANDARD_CHART_ID, accountCode);
        LedgerAccount account = new LedgerAccount();
        account.setId(idGenerator.nextId());
        account.setLedgerBookId(ledgerBookId);
        account.setChartAccountId(chartAccount.getId());
        account.setOwnerType(ownerType);
        account.setOwnerId(ownerId);
        account.setBalanceBucket(bucket);
        account.setNormalSide(chartAccount.getNormalSide());
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setCreateTime(now());
        account.setUpdateTime(now());
        ledgerAccountMapper.insert(account);
        return account.getId();
    }

    /** 插入 ACTIVE 财务主体。 */
    public long mockFinanceEntity(String entityCode, long legalPartyId, FinanceEntityStatus status) {
        FinanceEntity entity = new FinanceEntity();
        entity.setId(idGenerator.nextId());
        entity.setEntityCode(entityCode);
        entity.setLegalPartyId(legalPartyId);
        entity.setDisplayName("Test " + entityCode);
        entity.setStatus(status);
        entity.setCreateTime(now());
        entity.setUpdateTime(now());
        financeEntityMapper.insert(entity);
        return entity.getId();
    }

    /** 插入启用的测试币种规则，用于非 USD 精度场景。 */
    public void mockCurrency(String currency, int minorUnit) {
        mockCurrency(currency, minorUnit, CurrencySpecStatus.ACTIVE);
    }

    /** 插入指定状态的测试币种规则。 */
    public void mockCurrency(String currency, int minorUnit, CurrencySpecStatus status) {
        CurrencySpec spec = new CurrencySpec();
        spec.setId(idGenerator.nextId());
        spec.setCurrency(currency);
        spec.setMinorUnit(minorUnit);
        spec.setRoundingMode("HALF_UP");
        spec.setStatus(status);
        spec.setCreateTime(now());
        spec.setUpdateTime(now());
        testMapper.insertCurrencySpec(spec);
    }

    /** 插入指定状态的测试科目表，返回主键（Snowflake，>= 1000000）。 */
    public long mockChart(String chartCode, ReferenceDataStatus status) {
        ChartOfAccounts chart = new ChartOfAccounts();
        chart.setId(idGenerator.nextId());
        chart.setChartCode(chartCode);
        chart.setChartName("Test " + chartCode);
        chart.setStatus(status);
        chart.setCreateTime(now());
        chart.setUpdateTime(now());
        testMapper.insertChartOfAccounts(chart);
        return chart.getId();
    }

    /** 在测试科目表下插入资产类借方科目，返回主键。 */
    public long mockChartAccount(long chartId, String accountCode, boolean postingAllowed) {
        ChartAccount account = new ChartAccount();
        account.setId(idGenerator.nextId());
        account.setChartId(chartId);
        account.setAccountCode(accountCode);
        account.setAccountName("Test " + accountCode);
        account.setAccountClass(AccountClass.ASSET);
        account.setNormalSide(NormalSide.DEBIT);
        account.setPostingAllowed(postingAllowed);
        account.setCreateTime(now());
        account.setUpdateTime(now());
        testMapper.insertChartAccount(account);
        return account.getId();
    }

    /** 微秒精度的当前 UTC 时间。 */
    Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
