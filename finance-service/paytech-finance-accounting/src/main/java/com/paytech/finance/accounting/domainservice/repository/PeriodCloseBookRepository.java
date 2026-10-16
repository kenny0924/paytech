package com.paytech.finance.accounting.domainservice.repository;

import java.util.List;

import com.paytech.finance.accounting.core.dal.dataobject.PeriodCloseBook;

/** t_period_close_book：关账流程逐账簿证据读写。 */
public interface PeriodCloseBookRepository {

    /** 批量插入账簿证据。 */
    void insertBatch(List<PeriodCloseBook> books);

    /** 按账簿主键升序读取流程的账簿证据。 */
    List<PeriodCloseBook> findByClose(long periodCloseId);

    /** 记录结转分录水位与指令，成功后同步原对象。 */
    void recordPosting(PeriodCloseBook book, long asOfSequence, long instructionId);

    /** 冻结最终试算并置为 FINALIZED，成功后同步原对象。 */
    void finalizeBook(PeriodCloseBook book, long finalTrialRunId);

    /** 统计不满足最终冻结条件的账簿数。 */
    long countInvalidFinalBooks(long periodCloseId);
}
