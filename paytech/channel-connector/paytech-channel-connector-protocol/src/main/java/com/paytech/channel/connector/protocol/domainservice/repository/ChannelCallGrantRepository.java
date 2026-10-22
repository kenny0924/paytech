package com.paytech.channel.connector.protocol.domainservice.repository;

import java.time.Instant;

import com.paytech.channel.connector.api.enums.ChannelCallGrantStatus;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelCallGrant;
import com.paytech.channel.connector.protocol.domain.ChannelCallGrantView;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

/** t_channel_call_grant 单表仓储；事务由调用方持有。查询结果组合同库标准 API 的编码与名称。 */
public interface ChannelCallGrantRepository {

    /** 按主键读取，未找到返回 null。 */
    ChannelCallGrant byId(long callGrantId);

    /** 按唯一组合读取，未找到返回 null。 */
    ChannelCallGrant byKey(String callerService, long channelId, long standardApiId);

    /** 插入授权；同一组合由唯一索引保护，冲突按并发冲突拒绝。 */
    void insert(ChannelCallGrant row);

    /** 以原对象版本与状态为条件变更状态并递增版本；行数不为 1 按并发冲突拒绝，成功后同步原对象。 */
    void updateStatus(ChannelCallGrant row, ChannelCallGrantStatus toStatus, long updatedBy, Instant now);

    /** 调用方对渠道该标准 API 编码是否有 ACTIVE 授权。 */
    boolean hasActive(String callerService, long channelId, String standardApiCode);

    /** 渠道该标准 API 的 ACTIVE 授权数。 */
    long countActiveByStandardApi(long channelId, long standardApiId);

    /** 按筛选条件分页读取；数据库 LIMIT/OFFSET 与 COUNT 使用相同条件。 */
    PagingResponseBody<ChannelCallGrantView> findViews(Long channelId, String callerService, String standardApiCode,
        ChannelCallGrantStatus grantStatus, PagingRequestBody paging);
}
