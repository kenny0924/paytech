package com.paytech.channel.connector.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.core.constant.ChannelConnectorLocks;
import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.core.dal.dataobject.MidCapability;
import com.paytech.channel.connector.core.enums.CapabilityStatus;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.domain.ChannelConfigChanges;
import com.paytech.channel.connector.domain.MidCapabilityView;
import com.paytech.channel.connector.domain.MidCapabilityWriteContext;
import com.paytech.channel.connector.domainservice.service.ChannelMidService;
import com.paytech.channel.connector.domainservice.service.MidCapabilityService;
import com.paytech.channel.connector.service.processor.request.CreateMidCapabilityRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import com.paytech.channel.connector.protocol.domain.ChannelIdentity;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelService;

import lombok.AllArgsConstructor;

/**
 * 创建 ENABLED 能力条件组及其明细
 * <li>锁 channel-mid:{midId}，与 MID 状态变更互斥；MID 须存在且未关闭。明细按能力目录校验（与候选查询的读取规则一致）。</li>
 * <li>六维组合在 MID 内由唯一索引保护，重复按 409 拒绝；能力组、明细与 CREATE 变更记录同事务写入。</li>
 */
@Component
@AllArgsConstructor
public class CreateMidCapabilityProcessor extends AbstractLockProcessor<CreateMidCapabilityRequest, MidCapabilityView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final ChannelService channelService;
    private final ChannelMidService channelMidService;
    private final MidCapabilityService midCapabilityService;
    private final ChannelConfigTransactions channelConfigTransactions;

    /** MID 资源锁。 */
    @Override
    protected LockCommand lockCommand(CreateMidCapabilityRequest request) {
        return LockCommand.tryLocks(List.of(ChannelConnectorLocks.channelMid(request.getMidId())),
            ChannelConnectorLocks.CHANNEL_CONFIG_WAIT, ChannelConnectorLocks.CHANNEL_CONFIG_LEASE);
    }

    @Override
    protected MidCapabilityView processWithinLock(CreateMidCapabilityRequest request) {
        // 业务验证
        ChannelMid mid = channelMidService.loadMid(request.getMidId());
        if (mid.getMidStatus() == ChannelStatus.CLOSED) {
            throw new BizCheckFailException("MID 已关闭，不能新增能力组: " + mid.getMidId());
        }
        ChannelIdentity channel = channelService.loadChannel(mid.getChannelId());
        if (channel.service()) {
            throw new BizCheckFailException("服务渠道没有 MID 能力: " + channel.getChannelCode());
        }

        // 构建上下文
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        MidCapability capability = buildCapability(request, now);
        MidCapabilityWriteContext context = new MidCapabilityWriteContext(mid, capability,
            MidCapabilityDetailBuilder.build(capability.getMidCapabilityId(), request.getDetails(),
                midCapabilityService.capabilityDefinitions(), idGenerator, now),
            ChannelConfigChanges.of(idGenerator.nextId(), ChangeTargetType.MID_CAPABILITY,
                capability.getMidCapabilityId(), ChangeType.CREATE, request.getActorId(), now), now);

        // 本地保存
        channelConfigTransactions.write(context.getChange(), () -> midCapabilityService.createCapability(context));
        return context.toView();
    }

    /** 构建 ENABLED 能力组。 */
    MidCapability buildCapability(CreateMidCapabilityRequest request, Instant now) {
        MidCapability capability = new MidCapability();
        capability.setMidCapabilityId(idGenerator.nextId());
        capability.setMidId(request.getMidId());
        capability.setPaymentMethodCode(request.getPaymentMethodCode());
        capability.setSchemeCode(request.getSchemeCode());
        capability.setCountryCode(request.getCountryCode());
        capability.setCurrency(request.getCurrency());
        capability.setMccCode(request.getMccCode());
        capability.setCapabilityStatus(CapabilityStatus.ENABLED);
        capability.setCreateTime(now);
        capability.setUpdateTime(now);
        return capability;
    }
}
