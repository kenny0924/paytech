package com.paytech.tax.domainservice.repository;

import java.util.List;

import com.paytech.tax.core.dal.dataobject.TaxCalculation;

/** t_tax_calculation 仓储：计算头写入与读取。 */
public interface TaxCalculationRepository {
    /** 插入一行；影响行数不为 1 时为系统异常。 */
    void insert(TaxCalculation row);

    /** 按 ID 读取；不存在返回 null。 */
    TaxCalculation findById(long id);

    /** 按计算身份读取（uk_tax_calculation_revision）；不存在返回 null。 */
    TaxCalculation findByIdentity(String sourceSystem, String sourceObjectType, long sourceObjectId, int sourceRevision, String taxScopeKey);

    /** 来源对象编号下的全部计算，按准备版本升序。 */
    List<TaxCalculation> findByObjectNo(String sourceObjectNo);
}
