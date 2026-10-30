package com.paytech.tax.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.tax.core.dal.dataobject.TaxTransaction;

/** t_tax_transaction 仓储：正式交易写入与读取。 */
public interface TaxTransactionRepository {
    /** 插入一行；影响行数不为 1 时为系统异常。 */
    void insert(TaxTransaction row);

    /** 按源事实身份读取（uk_tax_transaction_source）；不存在返回 null。 */
    TaxTransaction findBySource(String sourceSystem, String sourceFactType, String sourceFactId, String taxScopeKey);

    /** 按 ID 读取；不存在返回 null。 */
    TaxTransaction findById(long id);

    /** 一组计算对应的正式交易。 */
    List<TaxTransaction> findByCalculationIds(Collection<Long> calculationIds);
}
