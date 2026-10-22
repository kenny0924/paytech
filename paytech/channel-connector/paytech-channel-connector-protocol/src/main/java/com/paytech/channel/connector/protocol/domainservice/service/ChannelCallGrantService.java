package com.paytech.channel.connector.protocol.domainservice.service;

import com.paytech.channel.connector.api.enums.ChannelCallGrantStatus;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelCallGrant;
import com.paytech.channel.connector.protocol.domain.ChangeChannelCallGrantStatusContext;
import com.paytech.channel.connector.protocol.domain.ChannelCallGrantView;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

/**
 * 服务渠道调用授权（t_channel_call_grant）的本地查询与保存；只操作本库
 * <li>{@link #hasActiveGrant} 发布给支付扩展（core）的通用执行入口使用。</li>
 * <li>写方法加入调用方 Processor 的事务，变更记录由 Processor 在同一事务中写入。</li>
 */
public interface ChannelCallGrantService {

    /**
     * 调用方对渠道该标准 API 是否有 ACTIVE 授权
     *
     * @param callerService 已确认的调用方服务名
     * @param channelId 服务渠道 ID
     * @param standardApiCode 标准 API 编码
     * @return 有 ACTIVE 授权返回 true
     */
    boolean hasActiveGrant(String callerService, long channelId, String standardApiCode);

    /** 按筛选条件分页查询授权及标准 API 编码、名称。 */
    PagingResponseBody<ChannelCallGrantView> listGrants(Long channelId, String callerService, String standardApiCode,
        ChannelCallGrantStatus grantStatus, PagingRequestBody paging);

    /** 插入新授权；须在调用方事务内执行，同一组合由唯一索引兜底。 */
    void createGrant(ChannelCallGrant grant);

    /** 以原版本与原状态为条件启停授权；须在调用方事务内执行。 */
    void changeGrantStatus(ChangeChannelCallGrantStatusContext context);
}
