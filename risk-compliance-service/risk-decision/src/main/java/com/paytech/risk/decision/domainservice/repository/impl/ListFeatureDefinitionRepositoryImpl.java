package com.paytech.risk.decision.domainservice.repository.impl;

import com.paytech.risk.decision.core.dal.dataobject.ListFeatureDefinition;
import com.paytech.risk.decision.core.dal.mapper.ListFeatureDefinitionMapper;
import com.paytech.risk.decision.domainservice.repository.ListFeatureDefinitionRepository;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

/** 名单命中 Feature 参数持久化。 */
@Repository
@AllArgsConstructor
public class ListFeatureDefinitionRepositoryImpl implements ListFeatureDefinitionRepository {
    private final ListFeatureDefinitionMapper mapper;

    /** 插入参数。 */
    @Override
    public void insert(ListFeatureDefinition row) {
        mapper.insert(row);
    }

    /** 按 Feature Version 批量读取；空集合返回空列表。 */
    @Override
    public List<ListFeatureDefinition> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectByIds(ids);
    }
}
