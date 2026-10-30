package com.paytech.tax.domainservice.repository.impl;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.tax.core.dal.dataobject.TaxPeriod;
import com.paytech.tax.core.dal.mapper.TaxPeriodMapper;
import com.paytech.tax.domainservice.repository.TaxPeriodRepository;

import lombok.AllArgsConstructor;

/** TaxPeriodRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class TaxPeriodRepositoryImpl implements TaxPeriodRepository {
    private final TaxPeriodMapper mapper;

    /** 期间不存在时插入；已存在（含并发生成）时不变，调用方随后按业务身份重读。 */
    @Override
    public void insertIfAbsent(TaxPeriod row) {
        mapper.insertIfAbsent(row);
    }

    /** 按注册与起止日读取（uk_tax_period）；不存在返回 null。 */
    @Override
    public TaxPeriod findByRange(long registrationId, LocalDate periodStart, LocalDate periodEnd) {
        return mapper.selectByRange(registrationId, periodStart, periodEnd);
    }

    /** 按 ID 批量读取。 */
    @Override
    public List<TaxPeriod> findByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectByIds(ids);
    }
}
