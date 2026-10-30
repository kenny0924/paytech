package com.paytech.tax.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.tax.core.dal.dataobject.TaxReportingEntry;
import com.paytech.tax.core.dal.mapper.TaxReportingEntryMapper;
import com.paytech.tax.domainservice.repository.TaxReportingEntryRepository;

import lombok.AllArgsConstructor;

/** TaxReportingEntryRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class TaxReportingEntryRepositoryImpl implements TaxReportingEntryRepository {
    private final TaxReportingEntryMapper mapper;

    /** 一次插入全部行；空列表不发出 SQL，行数不符为系统异常。 */
    @Override
    public void insertAll(List<TaxReportingEntry> rows) {
        if (rows.isEmpty()) {
            return;
        }
        if (mapper.batchInsert(rows) != rows.size()) {
            throw new ErrorException("t_tax_reporting_entry 批量插入行数不符: 期望 " + rows.size());
        }
    }

    /** 一组交易的全部底稿。 */
    @Override
    public List<TaxReportingEntry> findByTransactionIds(Collection<Long> transactionIds) {
        if (transactionIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectByTransactionIds(transactionIds);
    }
}
