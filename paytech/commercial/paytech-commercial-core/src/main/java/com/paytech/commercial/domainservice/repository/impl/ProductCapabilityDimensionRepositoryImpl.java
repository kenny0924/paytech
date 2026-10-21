package com.paytech.commercial.domainservice.repository.impl;

import static com.paytech.commercial.domainservice.repository.impl.PersistenceTimes.micros;

import java.util.Collection;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.ProductCapabilityDimension;
import com.paytech.commercial.core.dal.mapper.ProductCapabilityDimensionMapper;
import com.paytech.commercial.domainservice.repository.ProductCapabilityDimensionRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 ProductCapabilityDimensionMapper 访问 t_product_capability_dimension。 */
@AllArgsConstructor
public class ProductCapabilityDimensionRepositoryImpl implements ProductCapabilityDimensionRepository {
    private final ProductCapabilityDimensionMapper mapper;

    /** 截断时间精度后批量插入并核对影响行数。 */
    @Override
    public void insertAll(List<ProductCapabilityDimension> rows) {
        if (rows.isEmpty()) {
            return;
        }
        rows.forEach(row -> {
            row.setCreateTime(micros(row.getCreateTime()));
            row.setUpdateTime(micros(row.getUpdateTime()));
        });
        if (mapper.insertAll(rows) != rows.size()) {
            throw new ErrorException("可配维度插入行数不符: " + rows.getFirst().getCapabilityCode());
        }
    }

    /** 空集合不访问数据库。 */
    @Override
    public List<ProductCapabilityDimension> findByCapabilityCodes(Collection<String> capabilityCodes) {
        if (capabilityCodes.isEmpty()) {
            return List.of();
        }
        return List.copyOf(mapper.selectByCapabilityCodes(capabilityCodes));
    }

    /** 删除一个可开通项的全部可配维度。 */
    @Override
    public void deleteByCapabilityCode(String capabilityCode) {
        mapper.deleteByCapabilityCode(capabilityCode);
    }
}
