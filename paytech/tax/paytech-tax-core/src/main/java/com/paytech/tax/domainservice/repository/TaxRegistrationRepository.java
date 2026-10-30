package com.paytech.tax.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.tax.core.dal.dataobject.TaxRegistration;

/** t_tax_registration 仓储：注册读取。 */
public interface TaxRegistrationRepository {
    /** 按 ID 读取；不存在返回 null。 */
    TaxRegistration findById(long id);

    /** 按 ID 批量读取。 */
    List<TaxRegistration> findByIds(Collection<Long> ids);
}
