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
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.domain.ChannelConfigChanges;
import com.paytech.channel.connector.domain.MidCapabilityView;
import com.paytech.channel.connector.domain.MidCapabilityWriteContext;
import com.paytech.channel.connector.domainservice.repository.MidCapabilityRepository;
import com.paytech.channel.connector.domainservice.service.ChannelMidService;
import com.paytech.channel.connector.domainservice.service.MidCapabilityService;
import com.paytech.channel.connector.service.processor.request.ChangeMidCapabilityStatusRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 启用或停用能力条件组；停用的组不参与候选查询
 * <li>锁能力组所属 MID channel-mid:{midId}；MID 须未关闭，已是目标状态时业务拒绝。</li>
 * <li>状态写入以原状态为条件，同事务写入 STATUS 变更记录。</li>
 */
@Component
@AllArgsConstructor
public class ChangeMidCapabilityStatusProcessor
    extends AbstractLockProcessor<ChangeMidCapabilityStatusRequest, MidCapabilityView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final ChannelMidService channelMidService;
    private final MidCapabilityService midCapabilityService;
    private final MidCapabilityRepository midCapabilityRepository;
    private final ChannelConfigTransactions channelConfigTransactions;

    /** 能力组所属 MID 的资源锁；能力组不存在时锁定能力组主键对应的空资源，由锁内读取业务拒绝。 */
    @Override
    protected LockCommand lockCommand(ChangeMidCapabilityStatusRequest request) {
        MidCapability capability = midCapabilityRepository.findById(request.getMidCapabilityId());
        long midId = capability == null ? request.getMidCapabilityId() : capability.getMidId();
        return LockCommand.tryLocks(List.of(ChannelConnectorLocks.channelMid(midId)),
            ChannelConnectorLocks.CHANNEL_CONFIG_WAIT, ChannelConnectorLocks.CHANNEL_CONFIG_LEASE);
    }

    @Override
    protected MidCapabilityView processWithinLock(ChangeMidCapabilityStatusRequest request) {
        // 业务验证
        MidCapabilityView current = midCapabilityService.getCapability(request.getMidCapabilityId());
        MidCapability capability = current.getCapability();
        ChannelMid mid = channelMidService.loadMid(capability.getMidId());
        CapabilityStatus target = request.getTargetStatus();
        checkChangeable(mid, capability, target);

        // 本地保存
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        MidCapabilityWriteContext context = new MidCapabilityWriteContext(mid, capability, current.getDetails(),
            ChannelConfigChanges.status(idGenerator.nextId(), ChangeTargetType.MID_CAPABILITY,
                capability.getMidCapabilityId(), capability.getCapabilityStatus(), target, request.getActorId(), null,
                now), now);
        channelConfigTransactions.write(context.getChange(),
            () -> midCapabilityService.changeCapabilityStatus(context, target));
        return context.toView();
    }

    /** MID 未关闭且能力组不是目标状态。 */
    void checkChangeable(ChannelMid mid, MidCapability capability, CapabilityStatus target) {
        if (mid.getMidStatus() == ChannelStatus.CLOSED) {
            throw new BizCheckFailException("MID 已关闭，不能启停能力组: " + mid.getMidId());
        }
        if (capability.getCapabilityStatus() == target) {
            throw new BizCheckFailException("能力组已是目标状态: " + capability.getMidCapabilityId() + ", 状态 "
                + target.getCode());
        }
    }
}
