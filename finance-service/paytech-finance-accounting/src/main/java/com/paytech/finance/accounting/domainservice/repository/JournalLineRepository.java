package com.paytech.finance.accounting.domainservice.repository;

import java.time.LocalDate;
import java.util.List;

import com.paytech.finance.accounting.core.dal.dataobject.JournalLine;
import com.paytech.finance.accounting.domain.view.AccountJournalLineView;
import com.paytech.finance.accounting.domain.view.JournalEntryDetailView;
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
}
