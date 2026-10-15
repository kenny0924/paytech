package com.paytech.finance.accounting.domainservice.repository;

import com.paytech.finance.accounting.core.dal.dataobject.JournalEntry;
import com.paytech.finance.accounting.domain.view.JournalEntryDetailView;

/** t_JournalEntry：会计分录头追加与读取。 */
public interface JournalEntryRepository {

    /** 插入分录头；同一指令或序号已有分录时按并发冲突拒绝。 */
    void insert(JournalEntry entry);

    /** 按指令读取分录头，不存在返回空。 */
    JournalEntry findByInstruction(long instructionId);

    /** 读取分录头及来源，不存在返回空。 */
    JournalEntryDetailView findDetail(long journalEntryId);
}
