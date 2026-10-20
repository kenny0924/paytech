package com.paytech.channel.connector.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.core.dal.dataobject.PaymentChannel;
import com.paytech.channel.connector.core.enums.ChangeTargetType;
import com.paytech.channel.connector.core.enums.ChangeType;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.ChannelConfigChanges;
import com.paytech.channel.connector.domain.ChannelContext;
import com.paytech.channel.connector.domainservice.service.PaymentChannelService;
import com.paytech.channel.connector.service.processor.request.CreateChannelRequest;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 创建 DRAFT 支付渠道
 * <li>渠道尚不存在，没有可锁的资源；渠道编码唯一索引是唯一防线，重复按 409 拒绝。</li>
 * <li>与渠道同事务写入 CREATE 变更记录；不调用外部服务，Platform 接入方只在激活时核对。</li>
 */
@Component
@AllArgsConstructor
public class CreateChannelProcessor extends AbstractProcessor<CreateChannelRequest, PaymentChannel> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final PaymentChannelService paymentChannelService;

    @Override
    protected PaymentChannel processRequest(CreateChannelRequest request) {
        // 构建上下文
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        PaymentChannel channel = buildChannel(request, now);
        ChannelContext context = new ChannelContext(channel, ChannelConfigChanges.of(idGenerator.nextId(),
            ChangeTargetType.CHANNEL, channel.getChannelId(), ChangeType.CREATE, request.getActorId(), now), now);

        // 本地保存
        paymentChannelService.createChannel(context);
        return channel;
    }

    /** 构建 DRAFT 渠道。 */
    PaymentChannel buildChannel(CreateChannelRequest request, Instant now) {
        PaymentChannel channel = new PaymentChannel();
        channel.setChannelId(idGenerator.nextId());
        channel.setChannelCode(request.getChannelCode());
        channel.setChannelName(request.getChannelName());
        channel.setChannelStatus(ChannelStatus.DRAFT);
        channel.setCreateTime(now);
        channel.setUpdateTime(now);
        return channel;
    }
}
