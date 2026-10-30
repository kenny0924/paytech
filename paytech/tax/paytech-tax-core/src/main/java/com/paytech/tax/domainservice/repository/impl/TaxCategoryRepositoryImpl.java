package com.paytech.tax.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.tax.core.dal.dataobject.TaxCategory;
import com.paytech.tax.core.dal.mapper.TaxCategoryMapper;
import com.paytech.tax.domainservice.repository.TaxCategoryRepository;

import lombok.AllArgsConstructor;

/** TaxCategoryRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class TaxCategoryRepositoryImpl implements TaxCategoryRepository {
    private final TaxCategoryMapper mapper;

    /** 按税类代码批量读取（不过滤状态）。 */
    @Override
    public List<TaxCategory> findByCodes(Collection<String> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        return mapper.selectByCodes(codes);
    }
}
