package com.paytech.finance.accounting.domainservice.service;

import java.time.LocalDate;

import com.paytech.finance.accounting.domain.AccountQuery;
import com.paytech.finance.accounting.domain.LedgerBookQuery;
import com.paytech.finance.accounting.domain.view.AccountJournalLineView;
import com.paytech.finance.accounting.domain.view.AccountView;
import com.paytech.finance.accounting.domain.view.JournalEntryDetailView;
import com.paytech.finance.accounting.domain.view.LedgerBookView;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

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
}
