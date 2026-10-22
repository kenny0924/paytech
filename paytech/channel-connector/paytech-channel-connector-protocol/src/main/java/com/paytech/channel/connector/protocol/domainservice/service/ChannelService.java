package com.paytech.channel.connector.protocol.domainservice.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.domain.ChannelIdentity;
import com.paytech.channel.connector.protocol.domain.ChannelSummary;
import com.paytech.channel.connector.protocol.domain.UpdateChannelContext;
import com.paytech.channel.connector.protocol.domain.UpdateChannelEndpointsContext;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

/**
 * 渠道身份（t_channel）的本地查询与保存；只操作本库，不调用外部依赖
 * <li>读取方法与 {@link #changeChannelStatus} 以领域结果 {@link ChannelIdentity} 发布给支付扩展（core）。</li>
 * <li>写方法加入调用方 Processor 的事务，变更记录由 Processor 在同一事务中写入。</li>
 */
public interface ChannelService {

    /** 读取渠道；不存在时业务拒绝（400）。 */
    ChannelIdentity loadChannel(long channelId);

    /** 读取渠道；不存在返回 null，由调用方按入口语义处理。 */
    ChannelIdentity findChannel(long channelId);

    /** 按编码读取渠道；不存在返回 null。 */
    ChannelIdentity findChannelByCode(String channelCode);

    /** 按主键批量读取渠道；空集合返回空列表。 */
    List<ChannelIdentity> findChannels(Collection<Long> channelIds);

    /** 按类型、状态与编码 / 名称前缀分页查询渠道列表项（含出站地址数与 ACTIVE 授权数）。 */
    PagingResponseBody<ChannelSummary> listChannels(ChannelType channelType, ChannelStatus status, String keyword,
        PagingRequestBody paging);

    /**
     * 以原状态为条件变更渠道状态并同步传入的渠道；须在调用方事务内执行
     *
     * @param channel 已加载的渠道，SQL 成功后其状态与更新时间被同步
     * @param targetStatus 目标状态，迁移合法性与前置条件已由调用方确认
     * @param actorId 操作者 Account ID
     * @param now 本次写入时间
     */
    void changeChannelStatus(ChannelIdentity channel, ChannelStatus targetStatus, long actorId, Instant now);

    /** 写入新渠道；须在调用方事务内执行，编码重复按并发冲突拒绝。 */
    void createChannel(Channel channel);

    /** 修改未关闭渠道的名称与说明并递增版本；须在调用方事务内执行。 */
    void updateChannel(UpdateChannelContext context);

    /** 按原版本整组替换出站地址并递增版本；须在调用方事务内执行。 */
    void updateChannelEndpoints(UpdateChannelEndpointsContext context);
}
