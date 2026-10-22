package com.paytech.channel.connector.protocol.service.processor;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.domain.ChannelConfigChangeRecord;
import com.paytech.channel.connector.protocol.domain.ChannelConfigChanges;
import com.paytech.channel.connector.protocol.domain.ChannelIdentity;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelConfigChangeService;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelService;
import com.paytech.channel.connector.protocol.service.processor.request.CreateChannelRequest;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 创建 DRAFT 渠道（支付渠道或服务渠道，类型创建后不可修改）
 * <li>渠道尚不存在，没有可锁的资源；渠道编码唯一索引是唯一防线，重复按 409 拒绝。</li>
 * <li>出站地址创建后另行配置（未配置不能激活）；与渠道同事务写入 CREATE 变更记录，不调用外部服务。</li>
 */
@Component
@AllArgsConstructor
public class CreateChannelProcessor extends AbstractProcessor<CreateChannelRequest, ChannelIdentity> {
    private final ChannelService channelService;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final ChannelConfigChangeService changeService;

    /** 创建渠道并写入变更记录。 */
    @Override
    protected ChannelIdentity processRequest(CreateChannelRequest request) {
        // 构建上下文
        Instant now = support.now();
        Channel channel = buildChannel(request, now);
        ChannelConfigChangeRecord change = ChannelConfigChanges.of(support.nextId(), ChangeTargetType.CHANNEL,
            channel.getChannelId(), ChangeType.CREATE, request.getActorId(), now);

        // 本地保存
        transactions.write(() -> {
            channelService.createChannel(channel);
            changeService.record(change);
            return null;
        });
        return ChannelIdentity.of(channel, List.of());
    }

    /** 构建 DRAFT 渠道：未配置出站地址，初始版本 1。 */
    Channel buildChannel(CreateChannelRequest request, Instant now) {
        Channel channel = new Channel();
        channel.setChannelId(support.nextId());
        channel.setChannelCode(request.getChannelCode());
        channel.setChannelName(request.getChannelName());
        channel.setChannelType(request.getChannelType());
        channel.setChannelStatus(ChannelStatus.DRAFT);
        channel.setDescription(request.getDescription());
        channel.setVersion(1);
        channel.setCreatedBy(request.getActorId());
        channel.setUpdatedBy(request.getActorId());
        channel.setCreateTime(now);
        channel.setUpdateTime(now);
        return channel;
    }
}
