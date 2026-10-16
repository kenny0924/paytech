package com.paytech.finance.accounting.core.dal.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.JournalLine;
import com.paytech.finance.accounting.domain.view.AccountJournalLineView;
import com.paytech.finance.accounting.domain.view.JournalEntryDetailView;
import com.paytech.finance.accounting.domain.view.ProfitCloseBalanceView;
import com.paytech.finance.accounting.domain.view.TrialBalanceAggregateView;

/** t_journal_line：会计分录行持久化（只追加）。 */
@Mapper
public interface JournalLineMapper {

    /** 批量插入分录行。 */
    int insertBatch(@Param("lines") List<JournalLine> lines);

    /**
     * 分页读取账户流水；累计余额在日期过滤与分页之前对账户全量历史计算
     *
     * @return 按分录序号、行号升序的流水
     */
    List<AccountJournalLineView> selectAccountLines(@Param("accountId") long accountId, @Param("from") LocalDate from,
        @Param("to") LocalDate to, @Param("size") int size, @Param("offset") int offset);

    /** 统计账户在日期区间内的流水行数。 */
    long countAccountLines(@Param("accountId") long accountId, @Param("from") LocalDate from,
        @Param("to") LocalDate to);

    /** 读取一条分录的全部行及其账户科目与核算对象，按行号升序。 */
    List<JournalEntryDetailView.Line> selectEntryLines(@Param("journalEntryId") long journalEntryId);

    /** 按科目汇总水位内、过账日不晚于期间结束的分录：期初有符号累计与本期借贷发生额；全零科目不返回。 */
    List<TrialBalanceAggregateView> selectTrialAggregates(@Param("ledgerBookId") long ledgerBookId,
        @Param("asOfSequence") long asOfSequence, @Param("start") LocalDate start, @Param("end") LocalDate end);

    /** 损益结转：账簿内收入/费用科目下每个账户在期间内、水位以内的借贷发生额，按账户主键升序。 */
    List<ProfitCloseBalanceView> selectProfitCloseBalances(@Param("ledgerBookId") long ledgerBookId,
        @Param("asOfSequence") long asOfSequence, @Param("start") LocalDate start, @Param("end") LocalDate end);

    /** 年末结转：账户在水位以内、过账日不晚于截止日的贷减借累计额；无流水为零。 */
    BigDecimal selectCreditNetUpTo(@Param("accountId") long accountId, @Param("asOfSequence") long asOfSequence,
        @Param("end") LocalDate end);
}
