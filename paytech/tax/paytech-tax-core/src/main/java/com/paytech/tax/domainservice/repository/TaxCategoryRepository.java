package com.paytech.tax.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.tax.core.dal.dataobject.TaxCategory;

/** t_tax_category 仓储：税类目录读取。 */
public interface TaxCategoryRepository {
    /** 按税类代码批量读取（不过滤状态）。 */
    List<TaxCategory> findByCodes(Collection<String> codes);
}
