package com.paytech.channel.connector.protocol.core.dal.mapper;

import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelApiEndpoint;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelChildCount;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelApi;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 渠道原子 API持久化，业务 SQL 位于对应 Mapper XML。 */
@Mapper
public interface ChannelApiMapper {

    /** 按渠道与不可变编码读取原子 API。 */
    ChannelApi byCode(@Param("channelId") long channelId, @Param("code") String code);

    /** 在调用方事务中锁定原子 API 行，供版本校验及条件更新。 */
    ChannelApi lock(@Param("channelId") long channelId, @Param("code") String code);

    /** 读取渠道全部原子 API，按编码排序；每个渠道最多 200 个。 */
    List<ChannelApi> allByChannel(@Param("channelId") long channelId);

    /** 分页查询，按编码与标识稳定排序。 */
    List<ChannelApi> list(@Param("channelId") long channelId, @Param("keyword") String keyword, @Param("offset") long offset, @Param("size") int size);

    /** 使用与分页相同的筛选统计总数。 */
    long count(@Param("channelId") long channelId, @Param("keyword") String keyword);

    /** 按渠道统计原子 API 数量，没有原子 API 的渠道不返回。 */
    List<ChannelChildCount> countByChannels(@Param("channelIds") Collection<Long> channelIds);

    /** 读取渠道全部原子 API 引用的出站地址代码，按编码排序。 */
    List<ChannelApiEndpoint> endpointReferences(@Param("channelId") long channelId);

    /** 插入新原子 API，渠道内编码由唯一索引保护。 */
    int insert(@Param("row") ChannelApi row);

    /** 按原版本更新名称、说明与执行定义，并递增版本。 */
    int update(@Param("row") ChannelApi row, @Param("apiName") String apiName, @Param("description") String description, @Param("definitionJson") String definitionJson, @Param("updatedBy") long updatedBy, @Param("updateTime") Instant updateTime);

    /** 按原版本删除原子 API。 */
    int delete(@Param("row") ChannelApi row);
}
