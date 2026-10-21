package com.paytech.channel.connector.protocol.domainservice.service;

import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.domain.ChannelConfigChangeRecord;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

/**
 * 渠道配置变更记录（t_channel_config_change，归 protocol 模块）的写入与查询
 * <li>写入只增不改，必须由用例 Processor 在业务变更的同一本地事务内调用；本服务不开启事务。</li>
 * <li>core 与 protocol 的配置写入共用本服务，不直接访问变更记录表。</li>
 */
public interface ChannelConfigChangeService {

    /**
     * 写入一条变更记录
     *
     * @param record 已分配主键与创建时间的记录
     * @throws com.paytech.foundation.common.error.ErrorException 写入行数不为 1
     */
    void record(ChannelConfigChangeRecord record);

    /**
     * 分页读取一个对象的变更记录，按主键降序
     *
     * @param targetType 被变更对象类型
     * @param targetId 被变更对象主键
     * @param paging 已校验的分页参数
     * @return 当前页记录与总数；数据库 LIMIT/OFFSET 与 COUNT 使用相同条件
     */
    PagingResponseBody<ChannelConfigChangeRecord> listChanges(ChangeTargetType targetType, long targetId,
        PagingRequestBody paging);
}
