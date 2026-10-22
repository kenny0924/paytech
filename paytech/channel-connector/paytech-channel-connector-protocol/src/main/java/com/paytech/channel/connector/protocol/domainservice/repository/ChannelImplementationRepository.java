package com.paytech.channel.connector.protocol.domainservice.repository;

import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelImplementation;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** 渠道标准 API 实现的单表仓储；事务由调用方持有，不向其他模块发布 DataObject。 */
public interface ChannelImplementationRepository {

    /** 按渠道与标准 API 读取当前实现。 */
    ChannelImplementation byKey(long channelId, long standardApiId);

    /** 在调用方事务中锁定实现行，供版本校验及条件更新。 */
    ChannelImplementation lock(long channelId, long standardApiId);

    /** 读取渠道的全部实现。 */
    List<ChannelImplementation> allByChannel(long channelId);

    /** 读取实现某个标准 API 的全部渠道实现，供引用保护与影响校验。 */
    List<ChannelImplementation> allByStandardApi(long standardApiId);

    /** 按渠道批量读取实现，供列表展示已实现的标准 API。 */
    List<ChannelImplementation> allByChannels(Collection<Long> channelIds);

    /** 按标准 API 批量读取实现；空集合返回空列表。 */
    List<ChannelImplementation> allByStandardApis(Collection<Long> standardApiIds);

    /** 插入新实现，同一渠道与标准 API 由唯一索引保护。 */
    void insert(ChannelImplementation row);

    /** 按原版本更新流程定义，并递增版本。 */
    void update(ChannelImplementation row, String flowDefinitionJson, long updatedBy, Instant updateTime);

    /** 按原版本删除实现。 */
    void delete(ChannelImplementation row);
}
