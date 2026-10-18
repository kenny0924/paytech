package com.paytech.channel.connector.domainservice.repository;

import java.util.List;

import com.paytech.channel.connector.core.dal.dataobject.MidCapability;

/** MidCapability 单表只读仓储。 */
public interface MidCapabilityRepository {
    /** 按明确归属批量读取配置；空 ID 集合返回空集合。 */
    List<MidCapability> findByMidIds(List<Long> ids);

}
