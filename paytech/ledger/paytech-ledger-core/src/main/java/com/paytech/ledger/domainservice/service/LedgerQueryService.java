package com.paytech.ledger.domainservice.service;

import java.time.LocalDate;
import java.util.List;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.domain.AccountQuery;
import com.paytech.ledger.domain.LedgerBookQuery;
import com.paytech.ledger.domain.view.AccountJournalLineView;
import com.paytech.ledger.domain.view.AccountView;
import com.paytech.ledger.domain.view.JournalEntryDetailView;
import com.paytech.ledger.domain.view.LedgerBookView;

/**
 * 账簿、账户、账户流水与分录详情的只读查询
 * <li>不加锁、不开启事务；金额按账簿币种精度补齐小数位。</li>
 * <li>账户与分录必须属于路径指定的账簿，否则业务拒绝。</li>
 */
public interface LedgerQueryService {

    /** 分页查询账簿。 */
    PagingResponseBody<LedgerBookView> pageLedgerBooks(LedgerBookQuery query);

    /** 分页查询账簿内账户；账簿不存在时拒绝。 */
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

    /**
     * 账簿内 Owner 账户按分录聚合的余额（design/04 §7 日终核对的 ledger 侧）
     * <li>Owner 类型为 account 服务维护余额的 CLIENT、LOCAL_PARTNER、CHANNEL_ACCOUNT、CASH_ACCOUNT。</li>
     *
     * @param ledgerBookId 账簿主键；不存在时拒绝
     * @return 按科目代码、核算对象、余额桶排序的全部 Owner 账户，金额按币种精度补齐
     */
    List<AccountView> listOwnerAccountTotals(long ledgerBookId);

    /** 批量读取科目的最大主键数。 */
    int MAX_CHART_ACCOUNT_IDS = 200;
}
