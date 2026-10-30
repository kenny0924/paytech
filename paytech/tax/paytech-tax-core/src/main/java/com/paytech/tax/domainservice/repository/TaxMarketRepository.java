package com.paytech.tax.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.tax.core.dal.dataobject.TaxMarket;

/** t_tax_market 仓储：准入读取。 */
public interface TaxMarketRepository {
    /** 纳税主体的全部准入记录（任意地区、任意状态）。 */
    List<TaxMarket> findBySubject(long subjectPartyId);

    /** 按 ID 批量读取。 */
    List<TaxMarket> findByIds(Collection<Long> ids);
}
