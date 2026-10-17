package com.paytech.channel.connector.domainservice.repository;

import java.util.List;

import com.paytech.channel.connector.core.dal.dataobject.MidCapabilityDetail;

/** MidCapabilityDetail 单表只读仓储。 */
public interface MidCapabilityDetailRepository {
    /** 按明确归属批量读取配置；空 ID 集合返回空集合。 */
    List<MidCapabilityDetail> findByCapabilityIds(List<Long> ids);

}
