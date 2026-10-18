package com.paytech.risk.decision.domainservice.repository;

import com.paytech.risk.decision.core.dal.dataobject.FeatureVersion;
import java.util.List;

/** Feature 执行版本的单表持久化边界。 */
public interface FeatureVersionRepository {
    /** 插入不可变 Feature Version。 */
    void insert(FeatureVersion row);

    /** 按 ID 批量读取；空集合返回空列表。 */
    List<FeatureVersion> findByIds(List<Long> ids);
}
