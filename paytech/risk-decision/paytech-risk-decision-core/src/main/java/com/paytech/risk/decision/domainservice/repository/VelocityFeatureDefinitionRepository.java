package com.paytech.risk.decision.domainservice.repository;

import com.paytech.risk.decision.core.dal.dataobject.VelocityFeatureDefinition;
import java.util.List;

/** 实时累计 Feature 参数的单表持久化边界。 */
public interface VelocityFeatureDefinitionRepository {
    /** 插入参数。 */
    void insert(VelocityFeatureDefinition row);

    /** 按 Feature Version 批量读取；空集合返回空列表。 */
    List<VelocityFeatureDefinition> findByIds(List<Long> ids);
}
