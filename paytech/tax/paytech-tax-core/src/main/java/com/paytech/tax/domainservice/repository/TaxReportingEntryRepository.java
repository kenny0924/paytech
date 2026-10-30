package com.paytech.tax.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.tax.core.dal.dataobject.TaxReportingEntry;

/** t_tax_reporting_entry 仓储：申报底稿写入与读取。 */
public interface TaxReportingEntryRepository {
    /** 一次插入全部行；空列表不发出 SQL，行数不符为系统异常。 */
    void insertAll(List<TaxReportingEntry> rows);

    /** 一组交易的全部底稿。 */
    List<TaxReportingEntry> findByTransactionIds(Collection<Long> transactionIds);
}
