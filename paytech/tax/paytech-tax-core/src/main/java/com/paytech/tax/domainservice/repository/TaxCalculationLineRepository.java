package com.paytech.tax.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.tax.core.dal.dataobject.TaxCalculationLine;

/** t_tax_calculation_line 仓储：计算行写入与读取。 */
public interface TaxCalculationLineRepository {
    /** 一次插入全部行；空列表不发出 SQL，行数不符为系统异常。 */
    void insertAll(List<TaxCalculationLine> rows);

    /** 一组计算的全部行，按计算与行号升序。 */
    List<TaxCalculationLine> findByCalculationIds(Collection<Long> calculationIds);
}
