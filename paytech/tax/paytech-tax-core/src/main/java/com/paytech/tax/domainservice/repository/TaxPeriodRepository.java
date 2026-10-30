package com.paytech.tax.domainservice.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import com.paytech.tax.core.dal.dataobject.TaxPeriod;

/** t_tax_period 仓储：申报期间生成与读取。 */
public interface TaxPeriodRepository {
    /** 期间不存在时插入；已存在（含并发生成）时不变，调用方随后按业务身份重读。 */
    void insertIfAbsent(TaxPeriod row);

    /** 按注册与起止日读取（uk_tax_period）；不存在返回 null。 */
    TaxPeriod findByRange(long registrationId, LocalDate periodStart, LocalDate periodEnd);

    /** 按 ID 批量读取。 */
    List<TaxPeriod> findByIds(Collection<Long> ids);
}
