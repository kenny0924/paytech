package com.paytech.channel.connector.domainservice.repository;

import java.util.List;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCostComponent;

/** t_channel_cost_component：成本组成写入与查询；写入后不可变。 */
public interface ChannelCostComponentRepository {

    /** 批量写入一个版本的全部成本组成。 */
    void insertAll(List<ChannelCostComponent> components);

    /** 一个适用范围的全部组成，按组成代码升序。 */
    List<ChannelCostComponent> findByScope(long scopeId);

    /** 多个适用范围的全部组成，按范围主键、组成代码升序。 */
    List<ChannelCostComponent> findByScopes(List<Long> scopeIds);
}
