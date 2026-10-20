package com.paytech.channel.connector.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.channel.connector.core.dal.dataobject.ChannelConfigChange;
import com.paytech.channel.connector.core.enums.ChangeTargetType;

/** t_channel_config_change：变更留痕 SQL 参数绑定，SQL 仅位于 XML。 */
public interface ChannelConfigChangeMapper {

    /** 插入一条变更记录。 */
    int insert(ChannelConfigChange record);

    /** 按被变更对象分页读取，按主键降序。 */
    List<ChannelConfigChange> selectByTarget(@Param("targetType") ChangeTargetType targetType,
        @Param("targetId") long targetId, @Param("offset") int offset, @Param("limit") int limit);

    /** 统计被变更对象的变更记录数。 */
    long countByTarget(@Param("targetType") ChangeTargetType targetType, @Param("targetId") long targetId);
}
