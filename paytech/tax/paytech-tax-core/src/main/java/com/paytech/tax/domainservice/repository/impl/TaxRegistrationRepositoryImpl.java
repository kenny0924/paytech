package com.paytech.tax.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.tax.core.dal.dataobject.TaxRegistration;
import com.paytech.tax.core.dal.mapper.TaxRegistrationMapper;
import com.paytech.tax.domainservice.repository.TaxRegistrationRepository;

import lombok.AllArgsConstructor;

/** TaxRegistrationRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class TaxRegistrationRepositoryImpl implements TaxRegistrationRepository {
    private final TaxRegistrationMapper mapper;

    /** 按 ID 读取；不存在返回 null。 */
    @Override
    public TaxRegistration findById(long id) {
        return mapper.selectById(id);
    }

    /** 按 ID 批量读取。 */
    @Override
    public List<TaxRegistration> findByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectByIds(ids);
    }
}
