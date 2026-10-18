package com.paytech.risk.decision.domainservice.repository.impl;

import com.paytech.risk.decision.core.dal.dataobject.FeatureVersion;
import com.paytech.risk.decision.core.dal.mapper.FeatureVersionMapper;
import com.paytech.risk.decision.domainservice.repository.FeatureVersionRepository;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

/** Feature 执行版本持久化。 */
@Repository
@AllArgsConstructor
public class FeatureVersionRepositoryImpl implements FeatureVersionRepository {
    private final FeatureVersionMapper mapper;

    /** 插入不可变 Feature Version。 */
    @Override
    public void insert(FeatureVersion row) {
        mapper.insert(row);
    }

    /** 按 ID 批量读取；空集合返回空列表。 */
    @Override
    public List<FeatureVersion> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectByIds(ids);
    }
}
