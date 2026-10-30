package com.paytech.tax.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.tax.core.dal.dataobject.TaxEvidence;

/** t_tax_evidence 仓储：计算证据写入与读取。 */
public interface TaxEvidenceRepository {
    /** 一次插入全部行；空列表不发出 SQL，行数不符为系统异常。 */
    void insertAll(List<TaxEvidence> rows);

    /** 一组计算的全部证据。 */
    List<TaxEvidence> findByCalculationIds(Collection<Long> calculationIds);
}
