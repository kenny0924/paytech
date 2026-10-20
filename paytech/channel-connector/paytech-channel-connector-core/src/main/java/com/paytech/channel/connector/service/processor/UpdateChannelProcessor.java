package com.paytech.channel.connector.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.core.constant.ChannelConnectorLocks;
import com.paytech.channel.connector.core.dal.dataobject.PaymentChannel;
import com.paytech.channel.connector.core.enums.ChangeTargetType;
import com.paytech.channel.connector.core.enums.ChangeType;
import com.paytech.channel.connector.domain.ChannelConfigChanges;
import com.paytech.channel.connector.domain.ChannelContext;
import com.paytech.channel.connector.domainservice.service.PaymentChannelService;
import com.paytech.channel.connector.service.processor.request.UpdateChannelRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 修改渠道名称
 * <li>锁 channel:{channelId}；已关闭渠道拒绝。写入以未关闭为条件，并同事务写入 UPDATE 变更记录。</li>
 */
@Component
@AllArgsConstructor
public class UpdateChannelProcessor extends AbstractLockProcessor<UpdateChannelRequest, PaymentChannel> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final PaymentChannelService paymentChannelService;

    /** 渠道资源锁。 */
    @Override
    protected LockCommand lockCommand(UpdateChannelRequest request) {
        return LockCommand.tryLocks(List.of(ChannelConnectorLocks.channel(request.getChannelId())),
            ChannelConnectorLocks.CHANNEL_CONFIG_WAIT, ChannelConnectorLocks.CHANNEL_CONFIG_LEASE);
    }

    @Override
    protected PaymentChannel processWithinLock(UpdateChannelRequest request) {
        // 业务验证
        PaymentChannel channel = paymentChannelService.loadChannel(request.getChannelId());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ChannelContext context = new ChannelContext(channel, ChannelConfigChanges.of(idGenerator.nextId(),
            ChangeTargetType.CHANNEL, channel.getChannelId(), ChangeType.UPDATE, request.getActorId(), now), now);
        if (context.closed()) {
            throw new BizCheckFailException("渠道已关闭，不能修改: " + channel.getChannelId());
        }

        // 本地保存
        paymentChannelService.renameChannel(context, request.getChannelName());
        return channel;
    }
}
