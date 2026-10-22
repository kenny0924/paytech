package com.paytech.channel.connector.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.core.constant.ChannelConnectorLocks;
import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.core.dal.dataobject.MidCapability;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.domain.ChannelConfigChanges;
import com.paytech.channel.connector.domain.MidCapabilityView;
import com.paytech.channel.connector.domain.MidCapabilityWriteContext;
import com.paytech.channel.connector.domainservice.repository.MidCapabilityRepository;
import com.paytech.channel.connector.domainservice.service.ChannelMidService;
import com.paytech.channel.connector.domainservice.service.MidCapabilityService;
import com.paytech.channel.connector.service.processor.request.ReplaceMidCapabilityDetailsRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 整组替换能力明细
 * <li>锁能力组所属 MID channel-mid:{midId}（能力组的 MID 不可变，锁前读取）；MID 须未关闭，新明细按能力目录校验。</li>
 * <li>同一事务内删除原明细、写入全部新明细、刷新能力组修改时间并写入 UPDATE 变更记录。</li>
 */
@Component
@AllArgsConstructor
public class ReplaceMidCapabilityDetailsProcessor
    extends AbstractLockProcessor<ReplaceMidCapabilityDetailsRequest, MidCapabilityView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final ChannelMidService channelMidService;
    private final MidCapabilityService midCapabilityService;
    private final MidCapabilityRepository midCapabilityRepository;
    private final ChannelConfigTransactions channelConfigTransactions;

    /** 能力组所属 MID 的资源锁；能力组不存在时锁定能力组主键对应的空资源，由锁内读取业务拒绝。 */
    @Override
    protected LockCommand lockCommand(ReplaceMidCapabilityDetailsRequest request) {
        MidCapability capability = midCapabilityRepository.findById(request.getMidCapabilityId());
        long midId = capability == null ? request.getMidCapabilityId() : capability.getMidId();
        return LockCommand.tryLocks(List.of(ChannelConnectorLocks.channelMid(midId)),
            ChannelConnectorLocks.CHANNEL_CONFIG_WAIT, ChannelConnectorLocks.CHANNEL_CONFIG_LEASE);
    }

    @Override
    protected MidCapabilityView processWithinLock(ReplaceMidCapabilityDetailsRequest request) {
        // 业务验证
        MidCapability capability = midCapabilityService.loadCapability(request.getMidCapabilityId());
        ChannelMid mid = channelMidService.loadMid(capability.getMidId());
        if (mid.getMidStatus() == ChannelStatus.CLOSED) {
            throw new BizCheckFailException("MID 已关闭，不能修改能力组: " + mid.getMidId());
        }

        // 构建上下文
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        MidCapabilityWriteContext context = new MidCapabilityWriteContext(mid, capability,
            MidCapabilityDetailBuilder.build(capability.getMidCapabilityId(), request.getDetails(),
                midCapabilityService.capabilityDefinitions(), idGenerator, now),
            ChannelConfigChanges.of(idGenerator.nextId(), ChangeTargetType.MID_CAPABILITY,
                capability.getMidCapabilityId(), ChangeType.UPDATE, request.getActorId(), now), now);

        // 本地保存
        channelConfigTransactions.write(context.getChange(), () -> midCapabilityService.replaceDetails(context));
        return context.toView();
    }
}
