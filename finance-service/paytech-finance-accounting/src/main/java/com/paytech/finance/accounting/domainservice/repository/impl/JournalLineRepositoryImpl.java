package com.paytech.finance.accounting.domainservice.repository.impl;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.JournalLine;
import com.paytech.finance.accounting.core.dal.mapper.JournalLineMapper;
import com.paytech.finance.accounting.domain.view.AccountJournalLineView;
import com.paytech.finance.accounting.domain.view.JournalEntryDetailView;
import com.paytech.finance.accounting.domain.view.ProfitCloseBalanceView;
import com.paytech.finance.accounting.domain.view.TrialBalanceAggregateView;
import com.paytech.finance.accounting.domainservice.repository.JournalLineRepository;
import com.paytech.foundation.web.paging.PagingRequestBody;

import lombok.AllArgsConstructor;

/** JournalLineRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class JournalLineRepositoryImpl implements JournalLineRepository {
    private final JournalLineMapper mapper;

    /** 批量插入分录行。 */
    @Override
    public void insertBatch(List<JournalLine> lines) {
        mapper.insertBatch(lines);
    }

    /** 分页读取账户流水，累计余额按全量历史计算。 */
    @Override
    public List<AccountJournalLineView> findAccountLines(long accountId, LocalDate from, LocalDate to, PagingRequestBody paging) {
        return mapper.selectAccountLines(accountId, from, to, paging.size(), paging.offset());
    }

    /** 统计账户在日期区间内的流水行数。 */
    @Override
    public long countAccountLines(long accountId, LocalDate from, LocalDate to) {
        return mapper.countAccountLines(accountId, from, to);
    }

    /** 读取一条分录的全部行及账户科目与核算对象。 */
    @Override
    public List<JournalEntryDetailView.Line> findEntryLines(long journalEntryId) {
        return mapper.selectEntryLines(journalEntryId);
    }

    /** 按科目汇总试算所需的期初与本期发生额。 */
    @Override
    public List<TrialBalanceAggregateView> findTrialAggregates(long ledgerBookId, long asOfSequence, LocalDate start, LocalDate end) {
        return mapper.selectTrialAggregates(ledgerBookId, asOfSequence, start, end);
    }

    /** 读取账簿内收入/费用账户在期间内的借贷发生额。 */
    @Override
    public List<ProfitCloseBalanceView> findProfitCloseBalances(long ledgerBookId, long asOfSequence, LocalDate start, LocalDate end) {
        return mapper.selectProfitCloseBalances(ledgerBookId, asOfSequence, start, end);
    }
}
