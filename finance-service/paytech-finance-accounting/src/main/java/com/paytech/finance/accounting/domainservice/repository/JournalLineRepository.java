package com.paytech.finance.accounting.domainservice.repository;

import java.util.List;

import com.paytech.finance.accounting.core.dal.dataobject.JournalLine;

/** t_JournalLine：会计分录行追加。 */
public interface JournalLineRepository {

    /** 批量插入分录行。 */
    void insertBatch(List<JournalLine> lines);
}
