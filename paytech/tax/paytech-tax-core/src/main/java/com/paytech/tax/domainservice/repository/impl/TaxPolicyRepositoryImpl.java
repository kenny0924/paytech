package com.paytech.tax.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.tax.core.dal.dataobject.TaxPolicy;
import com.paytech.tax.core.dal.mapper.TaxPolicyMapper;
import com.paytech.tax.domainservice.repository.TaxPolicyRepository;

import lombok.AllArgsConstructor;

/** TaxPolicyRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class TaxPolicyRepositoryImpl implements TaxPolicyRepository {
    private final TaxPolicyMapper mapper;

    /** 按 ID 读取；不存在返回 null。 */
    @Override
    public TaxPolicy findById(long id) {
        return mapper.selectById(id);
    }

    /** 按 ID 批量读取。 */
    @Override
    public List<TaxPolicy> findByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectByIds(ids);
    }
}
