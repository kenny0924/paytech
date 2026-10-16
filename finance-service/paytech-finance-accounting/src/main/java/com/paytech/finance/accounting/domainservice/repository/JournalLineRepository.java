package com.paytech.finance.accounting.domainservice.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.paytech.finance.accounting.core.dal.dataobject.JournalLine;
import com.paytech.finance.accounting.domain.view.AccountJournalLineView;
import com.paytech.finance.accounting.domain.view.JournalEntryDetailView;
import com.paytech.finance.accounting.domain.view.ProfitCloseBalanceView;
import com.paytech.finance.accounting.domain.view.TrialBalanceAggregateView;
import com.paytech.foundation.web.paging.PagingRequestBody;

/** t_JournalLine：会计分录行追加。 */
public interface JournalLineRepository {

    /** 批量插入分录行。 */
    void insertBatch(List<JournalLine> lines);

    /** 分页读取账户流水，累计余额按全量历史计算。 */
    List<AccountJournalLineView> findAccountLines(long accountId, LocalDate from, LocalDate to, PagingRequestBody paging);

    /** 统计账户在日期区间内的流水行数。 */
    long countAccountLines(long accountId, LocalDate from, LocalDate to);

    /** 读取一条分录的全部行及账户科目与核算对象。 */
    List<JournalEntryDetailView.Line> findEntryLines(long journalEntryId);

    /** 按科目汇总试算所需的期初与本期发生额。 */
    List<TrialBalanceAggregateView> findTrialAggregates(long ledgerBookId, long asOfSequence, LocalDate start, LocalDate end);

    /** 读取账簿内收入/费用账户在期间内的借贷发生额。 */
    List<ProfitCloseBalanceView> findProfitCloseBalances(long ledgerBookId, long asOfSequence, LocalDate start, LocalDate end);

    /** 读取账户在水位以内、过账日不晚于截止日的贷减借累计额，无流水为零。 */
    BigDecimal findCreditNetUpTo(long accountId, long asOfSequence, LocalDate end);
}
