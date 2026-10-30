package com.paytech.tax.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.tax.core.dal.dataobject.TaxPolicy;

/** t_tax_policy 仓储：地区包读取。 */
public interface TaxPolicyRepository {
    /** 按 ID 读取；不存在返回 null。 */
    TaxPolicy findById(long id);

    /** 按 ID 批量读取。 */
    List<TaxPolicy> findByIds(Collection<Long> ids);
}
