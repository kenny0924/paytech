package com.paytech.channel.connector.protocol.core.dal.mapper;

import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelImplementation;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 渠道标准 API 实现持久化，业务 SQL 位于对应 Mapper XML。 */
@Mapper
public interface ChannelImplementationMapper {

    /** 按渠道与标准 API 读取当前实现。 */
    ChannelImplementation byKey(@Param("channelId") long channelId, @Param("standardApiId") long standardApiId);

    /** 在调用方事务中锁定实现行，供版本校验及条件更新。 */
    ChannelImplementation lock(@Param("channelId") long channelId, @Param("standardApiId") long standardApiId);

    /** 读取渠道的全部实现。 */
    List<ChannelImplementation> allByChannel(@Param("channelId") long channelId);

    /** 读取实现某个标准 API 的全部渠道实现，供引用保护与影响校验。 */
    List<ChannelImplementation> allByStandardApi(@Param("standardApiId") long standardApiId);

    /** 按渠道批量读取实现，供列表展示已实现的标准 API。 */
    List<ChannelImplementation> allByChannels(@Param("channelIds") Collection<Long> channelIds);

    /** 按标准 API 批量读取实现，供列表展示实现方。 */
    List<ChannelImplementation> allByStandardApis(@Param("standardApiIds") Collection<Long> standardApiIds);

    /** 插入新实现，同一渠道与标准 API 由唯一索引保护。 */
    int insert(@Param("row") ChannelImplementation row);

    /** 按原版本更新流程定义，并递增版本。 */
    int update(@Param("row") ChannelImplementation row, @Param("flowDefinitionJson") String flowDefinitionJson, @Param("updatedBy") long updatedBy, @Param("updateTime") Instant updateTime);

    /** 按原版本删除实现。 */
    int delete(@Param("row") ChannelImplementation row);
}
