package com.paytech.ledger.domainservice.service;

import java.time.LocalDate;
import java.util.List;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.ChartOfAccounts;
import com.paytech.ledger.core.dal.dataobject.CurrencySpec;
import com.paytech.ledger.core.dal.dataobject.FinanceEntity;
import com.paytech.ledger.core.dal.dataobject.FiscalCalendar;
import com.paytech.ledger.core.enums.ChartAccountStatus;
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
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

/**
 * 账簿、账户、账户流水与分录详情的只读查询
 * <li>不加锁、不开启事务；金额按账簿币种精度补齐小数位。</li>
 * <li>账户与分录必须属于路径指定的账簿，否则业务拒绝。</li>
 * <li>账务基础资料（财务主体、日历、期间、科目表、科目、币种）列表供 Operations 页面只读展示。</li>
 */
public interface LedgerQueryService {

    /** 分页查询账簿。 */
    PagingResponseBody<LedgerBookView> pageLedgerBooks(LedgerBookQuery query);

    /** 分页查询账簿内账户；账簿不存在时以 LEDGER_BOOK_NOT_FOUND 拒绝。 */
    PagingResponseBody<AccountView> pageAccounts(AccountQuery query);

    /**
     * 分页查询账户流水
     *
     * @param ledgerBookId 账簿主键
     * @param accountId 账户主键，必须属于该账簿
     * @param from 过账日期下限（含），可空
     * @param to 过账日期上限（含），可空
     * @param paging 已校验的分页参数
     * @return 按分录序号升序的流水；每行余额按全量历史累计
     */
    PagingResponseBody<AccountJournalLineView> pageAccountJournalLines(long ledgerBookId, long accountId, LocalDate from,
        LocalDate to, PagingRequestBody paging);

    /** 查询分录详情；分录不存在或不属于该账簿时拒绝。 */
    JournalEntryDetailView getJournalEntry(long ledgerBookId, long journalEntryId);

    /** 查询单个账簿与其科目表（design/04 §8，供 account 失败关闭地读取账簿身份）；不存在时拒绝。 */
    LedgerBookView getLedgerBook(long ledgerBookId);

    /**
     * 按主键批量读取科目（design/04 §6 科目名经 ledger-api 批量读）
     *
     * @param chartAccountIds 去重后的科目主键，不超过 {@value #MAX_CHART_ACCOUNT_IDS} 个
     * @return 按主键升序的科目；不存在的主键不返回
     */
    List<ChartAccount> listChartAccounts(List<Long> chartAccountIds);

    /** 分页查询财务主体，按主键升序。 */
    PagingResponseBody<FinanceEntity> pageFinanceEntities(FinanceEntityQuery query);

    /** 分页查询财务日历，按主键升序。 */
    PagingResponseBody<FiscalCalendar> pageFiscalCalendars(FiscalCalendarQuery query);

    /** 分页查询日历下的会计期间，按财年、期号升序；日历不存在时以 FISCAL_CALENDAR_NOT_FOUND 拒绝。 */
    PagingResponseBody<AccountingPeriod> pageAccountingPeriods(AccountingPeriodQuery query);

    /** 查询全部科目表，按科目表代码升序。 */
    List<ChartOfAccounts> listChartsOfAccounts();

    /**
     * 查询科目表下的科目
     *
     * @param chartCode 科目表代码；不存在时以 CHART_NOT_ACTIVE 拒绝
     * @param postingAllowed 是否可过账，为空时不过滤
     * @param accountStatus 启停状态，为空时不过滤
     * @return 按科目编号升序的科目
     */
    List<ChartAccount> listChartAccountsByChart(String chartCode, Boolean postingAllowed, ChartAccountStatus accountStatus);

    /**
     * 分页查询科目变更记录，按变更时间倒序
     *
     * @param chartCode 科目表代码；不存在时以 CHART_NOT_ACTIVE 拒绝
     * @param chartAccountId 可选科目主键；不属于该科目表时以 CHART_ACCOUNT_NOT_FOUND 拒绝
     * @param paging 已校验的分页参数
     * @return 变更记录分页
     */
    PagingResponseBody<ChartAccountChangeView> pageChartAccountChanges(String chartCode, Long chartAccountId,
        PagingRequestBody paging);

    /** 查询币种规则，status 为空时不过滤，按币种代码升序。 */
    List<CurrencySpec> listCurrencies(CurrencySpecStatus status);

    /** 批量读取科目的最大主键数。 */
    int MAX_CHART_ACCOUNT_IDS = 200;
}
