package com.paytech.risk.decision.domainservice.repository.impl;

import com.paytech.risk.decision.core.dal.dataobject.VelocityFeatureDefinition;
import com.paytech.risk.decision.core.dal.mapper.VelocityFeatureDefinitionMapper;
import com.paytech.risk.decision.domainservice.repository.VelocityFeatureDefinitionRepository;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

/** 实时累计 Feature 参数持久化。 */
@Repository
@AllArgsConstructor
public class VelocityFeatureDefinitionRepositoryImpl implements VelocityFeatureDefinitionRepository {
    private final VelocityFeatureDefinitionMapper mapper;

    /** 插入参数。 */
    @Override
    public void insert(VelocityFeatureDefinition row) {
        mapper.insert(row);
    }

    /** 按 Feature Version 批量读取；空集合返回空列表。 */
    @Override
    public List<VelocityFeatureDefinition> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectByIds(ids);
    }
}
