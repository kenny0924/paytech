package com.paytech.tax.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.tax.core.dal.dataobject.TaxCalculationComponent;

/** t_tax_calculation_component 仓储：计算组件写入与读取。 */
public interface TaxCalculationComponentRepository {
    /** 一次插入全部行；空列表不发出 SQL，行数不符为系统异常。 */
    void insertAll(List<TaxCalculationComponent> rows);

    /** 一组计算的全部组件。 */
    List<TaxCalculationComponent> findByCalculationIds(Collection<Long> calculationIds);
}
