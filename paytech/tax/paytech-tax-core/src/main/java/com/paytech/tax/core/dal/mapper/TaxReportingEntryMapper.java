package com.paytech.tax.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.tax.core.dal.dataobject.TaxReportingEntry;

/** t_tax_reporting_entry：申报底稿写入与读取。 */
@Mapper
public interface TaxReportingEntryMapper {
    /** 一次插入多行；返回影响行数。 */
    int batchInsert(@Param("rows") List<TaxReportingEntry> rows);

    /** 一组交易的全部底稿。 */
    List<TaxReportingEntry> selectByTransactionIds(@Param("transactionIds") Collection<Long> transactionIds);
}
