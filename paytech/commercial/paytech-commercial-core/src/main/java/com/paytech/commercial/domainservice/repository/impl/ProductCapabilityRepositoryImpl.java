package com.paytech.commercial.domainservice.repository.impl;

import static com.paytech.commercial.domainservice.repository.impl.PersistenceTimes.micros;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;
import com.paytech.commercial.core.dal.mapper.ProductCapabilityMapper;
import com.paytech.commercial.domainservice.repository.ProductCapabilityRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 ProductCapabilityMapper 访问 t_product_capability。 */
@AllArgsConstructor
public class ProductCapabilityRepositoryImpl implements ProductCapabilityRepository {
    private final ProductCapabilityMapper mapper;

    /** 截断时间精度后插入并核对影响行数。 */
    @Override
    public void insert(ProductCapability capability) {
        capability.setCreateTime(micros(capability.getCreateTime()));
        capability.setUpdateTime(micros(capability.getUpdateTime()));
        if (mapper.insert(capability) != 1) {
            throw new ErrorException("可开通项目录插入行数不符: " + capability.getCapabilityCode());
        }
    }

    /** 按主键读取。 */
    @Override
    public ProductCapability findById(long productCapabilityId) {
        return mapper.selectById(productCapabilityId);
    }

    /** 按可开通项代码读取。 */
    @Override
    public ProductCapability findByCode(String capabilityCode) {
        return mapper.selectByCode(capabilityCode);
    }

    /** 空集合不访问数据库。 */
    @Override
    public List<ProductCapability> findByCodes(Collection<String> capabilityCodes) {
        if (capabilityCodes.isEmpty()) {
            return List.of();
        }
        return List.copyOf(mapper.selectByCodes(capabilityCodes));
    }

    /** 读取某产品的全部可开通项。 */
    @Override
    public List<ProductCapability> findByProductCode(ProductCode productCode) {
        return List.copyOf(mapper.selectByProductCode(productCode));
    }

    /** 条件迁移，成功后同步原对象。 */
    @Override
    public void updateStatus(ProductCapability capability, ProductStatus status, Instant now) {
        Instant time = micros(now);
        if (mapper.updateStatus(capability, status, time) != 1) {
            throw new ConcurrentRequestException("可开通项状态已变化: " + capability.getCapabilityCode());
        }
        capability.setStatus(status);
        capability.setUpdateTime(time);
    }
}
