package com.paytech.channel.connector.protocol.domainservice.service.impl;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;

import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.domain.ChannelIdentity;
import com.paytech.channel.connector.protocol.domain.ChannelSummary;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.UpdateChannelContext;
import com.paytech.channel.connector.protocol.domain.UpdateChannelEndpointsContext;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelService;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 渠道身份的本地查询与保存；写入方法加入调用方 Processor 的事务。 */
@Service
@AllArgsConstructor
public class ChannelServiceImpl implements ChannelService {
    private final ExternalApiConfigurationCodec codec;
    private final ChannelRepository channelRepository;

    /** 读取渠道；不存在时业务拒绝。 */
    @Override
    public ChannelIdentity loadChannel(long channelId) {
        ChannelIdentity channel = findChannel(channelId);
        if (channel == null) {
            throw new BizCheckFailException("渠道不存在: " + channelId);
        }
        return channel;
    }

    /** 读取渠道；不存在返回 null。 */
    @Override
    public ChannelIdentity findChannel(long channelId) {
        Channel row = channelRepository.byId(channelId);
        return row == null ? null : ChannelIdentity.of(row, codec.endpoints(row.getEndpoints()));
    }

    /** 按编码读取渠道；不存在返回 null。 */
    @Override
    public ChannelIdentity findChannelByCode(String channelCode) {
        Channel row = channelRepository.byCode(channelCode);
        return row == null ? null : ChannelIdentity.of(row, codec.endpoints(row.getEndpoints()));
    }

    /** 按主键批量读取渠道。 */
    @Override
    public List<ChannelIdentity> findChannels(Collection<Long> channelIds) {
        return channelRepository.byIds(channelIds).stream()
            .map(row -> ChannelIdentity.of(row, codec.endpoints(row.getEndpoints()))).toList();
    }

    /** 分页查询渠道列表项。 */
    @Override
    public PagingResponseBody<ChannelSummary> listChannels(ChannelType channelType, ChannelStatus status,
        String keyword, PagingRequestBody paging) {
        return channelRepository.findSummaries(channelType, status, keyword, paging);
    }

    /** 以原状态为条件变更状态，成功后同步传入的渠道；须在调用方事务内执行。 */
    @Override
    public void changeChannelStatus(ChannelIdentity channel, ChannelStatus targetStatus, long actorId, Instant now) {
        channelRepository.updateStatus(channel.getChannelId(), channel.getChannelStatus(), targetStatus, actorId, now);
        channel.statusChanged(targetStatus, now);
    }

    /** 写入新渠道；须在调用方事务内执行。 */
    @Override
    public void createChannel(Channel channel) {
        channelRepository.insert(channel);
    }

    /** 修改名称与说明；须在调用方事务内执行。 */
    @Override
    public void updateChannel(UpdateChannelContext context) {
        channelRepository.updateProfile(context.getChannel(), context.getChannelName(), context.getDescription(),
            context.getActorId(), context.getNow());
    }

    /** 替换出站地址；须在调用方事务内执行。 */
    @Override
    public void updateChannelEndpoints(UpdateChannelEndpointsContext context) {
        channelRepository.updateEndpoints(context.getChannel(), context.getEndpointsJson(), context.getActorId(),
            context.getNow());
    }
}
