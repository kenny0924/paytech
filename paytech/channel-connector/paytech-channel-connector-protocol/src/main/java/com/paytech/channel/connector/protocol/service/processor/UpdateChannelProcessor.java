package com.paytech.channel.connector.protocol.service.processor;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.protocol.core.constant.ChannelLocks;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.domain.ChannelConfigChanges;
import com.paytech.channel.connector.protocol.domain.ChannelIdentity;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.UpdateChannelContext;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelConfigChangeService;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelService;
import com.paytech.channel.connector.protocol.service.processor.request.UpdateChannelRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 修改渠道名称与说明（整体替换）；类型、编码与出站地址不在此修改
 * <li>锁 channel:{channelId}，与 core 的渠道状态变更、账号与 MID 写入同一互斥域；已关闭渠道拒绝。</li>
 * <li>写入以未关闭为条件并递增渠道版本，同事务写入 UPDATE 变更记录。</li>
 */
@Component
@AllArgsConstructor
public class UpdateChannelProcessor extends AbstractLockProcessor<UpdateChannelRequest, ChannelIdentity> {
    private final ChannelService channelService;
    private final ExternalApiConfigurationCodec codec;
    private final ChannelRepository channelRepository;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final ChannelConfigChangeService changeService;

    /** 渠道资源锁。 */
    @Override
    protected LockCommand lockCommand(UpdateChannelRequest request) {
        return LockCommand.tryLocks(List.of(ChannelLocks.channel(request.getChannelId())),
            ChannelLocks.CHANNEL_CONFIG_WAIT, ChannelLocks.CHANNEL_CONFIG_LEASE);
    }

    /** 修改名称与说明并写入变更记录。 */
    @Override
    protected ChannelIdentity processWithinLock(UpdateChannelRequest request) {
        // 业务验证
        Channel channel = loadOpenChannel(request.getChannelId());
        Instant now = support.now();
        UpdateChannelContext context = new UpdateChannelContext(channel, request.getChannelName(),
            request.getDescription(), request.getActorId(), ChannelConfigChanges.of(support.nextId(),
                ChangeTargetType.CHANNEL, channel.getChannelId(), ChangeType.UPDATE, request.getActorId(), now),
            now);

        // 本地保存
        transactions.write(() -> {
            channelService.updateChannel(context);
            changeService.record(context.getChange());
            return null;
        });
        return ChannelIdentity.of(channel, codec.endpoints(channel.getEndpoints()));
    }

    /** 读取未关闭渠道；不存在或已关闭业务拒绝。 */
    Channel loadOpenChannel(long channelId) {
        Channel channel = channelRepository.byId(channelId);
        if (channel == null) {
            throw new BizCheckFailException("渠道不存在: " + channelId);
        }
        if (channel.getChannelStatus() == ChannelStatus.CLOSED) {
            throw new BizCheckFailException("渠道已关闭，不能修改: " + channelId);
        }
        return channel;
    }
}
