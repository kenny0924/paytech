package com.paytech.channel.connector.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCostComponent;

/** t_channel_cost_component：渠道成本组成 SQL 参数绑定，只插入与读取。 */
public interface ChannelCostComponentMapper {

    /** 批量插入成本组成。 */
    int insertBatch(@Param("components") List<ChannelCostComponent> components);

    /** 一个适用范围的全部组成，按组成代码升序。 */
    List<ChannelCostComponent> selectByScope(@Param("scopeId") long scopeId);

    /** 多个适用范围的全部组成，按范围主键、组成代码升序；调用方保证列表非空。 */
    List<ChannelCostComponent> selectByScopes(@Param("scopeIds") List<Long> scopeIds);
}
