package com.paytech.channel.connector.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.core.constant.ChannelConnectorLocks;
import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.domain.ChannelConfigChanges;
import com.paytech.channel.connector.domain.ChannelMidContext;
import com.paytech.channel.connector.domain.ChannelMidDetailView;
import com.paytech.channel.connector.domainservice.service.ChannelAccountService;
import com.paytech.channel.connector.domainservice.service.ChannelMidService;
import com.paytech.channel.connector.domainservice.service.PaymentChannelService;
import com.paytech.channel.connector.domainservice.validator.channel.ChannelStatusTransitionValidator;
import com.paytech.channel.connector.service.processor.request.ChangeChannelStatusRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 变更 MID 状态
 * <li>锁 channel-mid:{midId}；激活要求渠道与当前账号均 ACTIVE，首次激活写入激活时间，关闭写入关闭时间。</li>
 * <li>状态写入以原状态为条件，同事务写入 STATUS 变更记录。</li>
 */
@Component
@AllArgsConstructor
public class ChangeChannelMidStatusProcessor
    extends AbstractLockProcessor<ChangeChannelStatusRequest, ChannelMidDetailView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final ChannelMidService channelMidService;
    private final ChannelAccountService channelAccountService;
    private final PaymentChannelService paymentChannelService;
    private final ChannelConfigTransactions channelConfigTransactions;

    /** MID 资源锁。 */
    @Override
    protected LockCommand lockCommand(ChangeChannelStatusRequest request) {
        return LockCommand.tryLocks(List.of(ChannelConnectorLocks.channelMid(request.getTargetId())),
            ChannelConnectorLocks.CHANNEL_CONFIG_WAIT, ChannelConnectorLocks.CHANNEL_CONFIG_LEASE);
    }

    @Override
    protected ChannelMidDetailView processWithinLock(ChangeChannelStatusRequest request) {
        // 构建上下文
        ChannelMidDetailView view = channelMidService.getMid(request.getTargetId());
        ChannelMid mid = view.getMid();
        ChannelStatus target = request.getTargetStatus();
        ChannelStatusTransitionValidator.checkTransition("MID " + mid.getMidId(), mid.getMidStatus(), target);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ChannelMidContext context = new ChannelMidContext(mid, paymentChannelService.loadChannel(mid.getChannelId()),
            channelAccountService.loadAccount(mid.getChannelAccountId()), ChannelConfigChanges.status(
                idGenerator.nextId(), ChangeTargetType.CHANNEL_MID, mid.getMidId(), mid.getMidStatus(), target,
                request.getActorId(), request.getReason(), now), now);

        // 业务验证
        if (target == ChannelStatus.ACTIVE && !context.canActivate()) {
            throw new BizCheckFailException("MID 激活前置不满足（渠道与当前技术账号须均为 ACTIVE）: MID " + mid.getMidId()
                + ", 渠道状态 " + context.getChannel().getChannelStatus().getCode() + ", 账号状态 "
                + context.getAccount().getAccountStatus().getCode());
        }

        // 本地保存
        channelConfigTransactions.write(context.getChange(), () -> channelMidService.changeMidStatus(context, target));
        return view;
    }
}
