package com.paytech.risk.decision.domainservice.repository;

import com.paytech.risk.decision.core.dal.dataobject.ListFeatureDefinition;
import java.util.List;

/** 名单命中 Feature 参数的单表持久化边界。 */
public interface ListFeatureDefinitionRepository {
    /** 插入参数。 */
    void insert(ListFeatureDefinition row);

    /** 按 Feature Version 批量读取；空集合返回空列表。 */
    List<ListFeatureDefinition> findByIds(List<Long> ids);
}
