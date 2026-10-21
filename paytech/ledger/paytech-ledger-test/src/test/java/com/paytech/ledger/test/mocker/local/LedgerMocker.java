package com.paytech.ledger.test.mocker.local;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleLine;
import com.paytech.ledger.core.dal.dataobject.BookSequence;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.ChartOfAccounts;
import com.paytech.ledger.core.dal.dataobject.FinanceEntity;
import com.paytech.ledger.core.dal.dataobject.FiscalCalendar;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.dal.mapper.AccountingPeriodMapper;
import com.paytech.ledger.core.dal.mapper.AccountingRuleBundleVerMapper;
import com.paytech.ledger.core.dal.mapper.AccountingRuleLineMapper;
import com.paytech.ledger.core.dal.mapper.BookSequenceMapper;
import com.paytech.ledger.core.dal.mapper.ChartAccountMapper;
import com.paytech.ledger.core.dal.mapper.FinanceEntityMapper;
import com.paytech.ledger.core.dal.mapper.FiscalCalendarMapper;
import com.paytech.ledger.core.dal.mapper.LedgerAccountMapper;
import com.paytech.ledger.core.dal.mapper.LedgerBookMapper;
import com.paytech.ledger.core.enums.AccountClass;
import com.paytech.ledger.core.enums.AccountStatus;
import com.paytech.ledger.core.enums.BookStatus;
import com.paytech.ledger.core.enums.ChartAccountStatus;
import com.paytech.ledger.core.enums.FinanceEntityStatus;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.core.enums.ReferenceDataStatus;
import com.paytech.ledger.test.dal.AccountingTestMapper;

/**
 * 通过 Mapper 直接插入账簿、日历、期间与账户前置事实，不调用被测 HTTP 入口
 * <li>科目使用初始化 DML 的 STANDARD 科目表；主键由进程 Snowflake 生成器分配。</li>
 */
@TestComponent
public class LedgerMocker {
    /** 初始化 DML 中 STANDARD 科目表的固定主键。 */
    public static final long STANDARD_CHART_ID = 2001L;

    /** Mapper 直接插入的账户使用的账户号前缀（生产账户号格式见 LedgerAccountNo），后接账户主键保证唯一。 */
    public static final String MOCK_ACCOUNT_NO_PREFIX = "MOCK-";

    /** Mapper 直接插入的规则版本使用的编制人。 */
    public static final long RULE_MAKER_ID = 81001L;

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
    private final AccountingRuleLineMapper accountingRuleLineMapper;
    private final AccountingRuleBundleVerMapper accountingRuleBundleVerMapper;

    public LedgerMocker(IdGenerator idGenerator, LedgerAccountMapper ledgerAccountMapper, LedgerBookMapper ledgerBookMapper,
        AccountingTestMapper testMapper, ChartAccountMapper chartAccountMapper, BookSequenceMapper bookSequenceMapper,
        FinanceEntityMapper financeEntityMapper, FiscalCalendarMapper fiscalCalendarMapper,
        AccountingPeriodMapper accountingPeriodMapper, AccountingRuleLineMapper accountingRuleLineMapper,
        AccountingRuleBundleVerMapper accountingRuleBundleVerMapper) {
        this.idGenerator = idGenerator;
        this.ledgerAccountMapper = ledgerAccountMapper;
        this.ledgerBookMapper = ledgerBookMapper;
        this.testMapper = testMapper;
        this.chartAccountMapper = chartAccountMapper;
        this.bookSequenceMapper = bookSequenceMapper;
        this.financeEntityMapper = financeEntityMapper;
        this.fiscalCalendarMapper = fiscalCalendarMapper;
        this.accountingPeriodMapper = accountingPeriodMapper;
        this.accountingRuleLineMapper = accountingRuleLineMapper;
        this.accountingRuleBundleVerMapper = accountingRuleBundleVerMapper;
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
        account.setAccountNo(MOCK_ACCOUNT_NO_PREFIX + account.getId());
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

    /** 在测试科目表下插入资产类借方一级科目，返回主键。 */
    public long mockChartAccount(long chartId, String accountNo, String accountCode, boolean postingAllowed) {
        return mockChartAccount(chartId, null, accountNo, accountCode, AccountClass.ASSET, NormalSide.DEBIT,
            postingAllowed).getId();
    }

    /** 在测试科目表下插入 ACTIVE 科目，返回插入的科目。 */
    public ChartAccount mockChartAccount(long chartId, Long parentAccountId, String accountNo, String accountCode,
        AccountClass accountClass, NormalSide normalSide, boolean postingAllowed) {
        ChartAccount account = new ChartAccount();
        account.setId(idGenerator.nextId());
        account.setChartId(chartId);
        account.setParentAccountId(parentAccountId);
        account.setAccountNo(accountNo);
        account.setAccountCode(accountCode);
        account.setAccountName("Test " + accountCode);
        account.setAccountClass(accountClass);
        account.setNormalSide(normalSide);
        account.setPostingAllowed(postingAllowed);
        account.setAccountStatus(ChartAccountStatus.ACTIVE);
        account.setCreateTime(now());
        account.setUpdateTime(now());
        testMapper.insertChartAccount(account);
        return account;
    }

    /**
     * 插入规则版本，规则行逐行复制模板版本；可按行代码把规则行改记到另一科目，使各版本的记账结果可区分
     *
     * @param ver 调用方给出规则集、版本号、状态与生效区间；主键、编制人与时间戳由本方法补齐
     * @param templateVerId 被复制规则行的模板版本主键
     * @param accountByLineCode 行代码 → 改记科目代码，未列出的行照抄模板
     * @return 版本主键（Snowflake，由全局清理删除）
     */
    public long mockRuleVersion(AccountingRuleBundleVer ver, long templateVerId, Map<String, String> accountByLineCode) {
        ver.setId(idGenerator.nextId());
        ver.setMakerId(RULE_MAKER_ID);
        ver.setCreateTime(now());
        ver.setUpdateTime(now());
        accountingRuleBundleVerMapper.insert(ver);
        List<AccountingRuleLine> lines = accountingRuleLineMapper.selectByVersion(templateVerId).stream()
            .map(template -> copyRuleLine(template, ver.getId(), accountByLineCode)).toList();
        accountingRuleLineMapper.insertBatch(lines);
        return ver.getId();
    }

    /** 复制一条模板规则行到目标版本，按行代码改记科目。 */
    AccountingRuleLine copyRuleLine(AccountingRuleLine template, long bundleVerId, Map<String, String> accountByLineCode) {
        AccountingRuleLine line = new AccountingRuleLine();
        line.setId(idGenerator.nextId());
        line.setBundleVerId(bundleVerId);
        line.setInstructionType(template.getInstructionType());
        line.setLineNo(template.getLineNo());
        line.setLineCode(template.getLineCode());
        line.setAmountSourceCode(template.getAmountSourceCode());
        line.setOwnerRole(template.getOwnerRole());
        line.setAccountCode(accountByLineCode.getOrDefault(template.getLineCode(), template.getAccountCode()));
        line.setBalanceBucket(template.getBalanceBucket());
        line.setEntrySide(template.getEntrySide());
        line.setRequiredFlag(template.getRequiredFlag());
        line.setCreateTime(now());
        return line;
    }

    /** 微秒精度的当前 UTC 时间。 */
    Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
