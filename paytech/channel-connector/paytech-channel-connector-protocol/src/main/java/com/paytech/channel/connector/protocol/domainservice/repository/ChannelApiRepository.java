package com.paytech.channel.connector.protocol.domainservice.repository;

import java.util.Collection;
import java.util.Map;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelApiEndpoint;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelApi;
import java.time.Instant;
import java.util.List;

/** 渠道原子 API的单表仓储；事务由调用方持有，不向其他模块发布 DataObject。 */
public interface ChannelApiRepository {

    /** 按渠道与不可变编码读取原子 API。 */
    ChannelApi byCode(long channelId, String code);

    /** 在调用方事务中锁定原子 API 行，供版本校验及条件更新。 */
    ChannelApi lock(long channelId, String code);

    /** 读取渠道全部原子 API，按编码排序；每个渠道最多 200 个。 */
    List<ChannelApi> allByChannel(long channelId);

    /** 分页查询，按编码与标识稳定排序。 */
    List<ChannelApi> list(long channelId, String keyword, long offset, int size);

    /** 使用与分页相同的筛选统计总数。 */
    long count(long channelId, String keyword);

    /** 按渠道统计原子 API 数量；空集合返回空映射。 */
    Map<Long, Long> countByChannels(Collection<Long> channelIds);

    /** 读取渠道全部原子 API 引用的出站地址代码，按编码排序。 */
    List<ChannelApiEndpoint> endpointReferences(long channelId);

    /** 插入新原子 API，渠道内编码由唯一索引保护。 */
    void insert(ChannelApi row);

    /** 按原版本更新名称、说明与执行定义，并递增版本。 */
    void update(ChannelApi row, String apiName, String description, String definitionJson, long updatedBy, Instant updateTime);

    /** 按原版本删除原子 API。 */
    void delete(ChannelApi row);
}
