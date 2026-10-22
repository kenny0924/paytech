package com.paytech.channel.connector.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.core.constant.ChannelConnectorLocks;
import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.domain.ChannelConfigChanges;
import com.paytech.channel.connector.domain.ChannelMidContext;
import com.paytech.channel.connector.domain.ChannelMidDetailView;
import com.paytech.channel.connector.domainservice.service.ChannelAccountService;
import com.paytech.channel.connector.domainservice.service.ChannelMidService;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelService;
import com.paytech.channel.connector.service.processor.request.RebindChannelMidAccountRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * MID 改绑技术账号
 * <li>锁 channel-mid:{midId} 与目标 channel-account:{id}，与目标账号关闭互斥。</li>
 * <li>MID 未关闭；目标账号与 MID 同渠道、同 OE、未关闭且不同于当前账号。写入以原账号与未关闭为条件，同事务写入 REBIND 变更记录。</li>
 * <li>已冻结到支付尝试的 Channel / 账号 / MID 三元组不受影响。</li>
 */
@Component
@AllArgsConstructor
public class RebindChannelMidAccountProcessor
    extends AbstractLockProcessor<RebindChannelMidAccountRequest, ChannelMidDetailView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final ChannelMidService channelMidService;
    private final ChannelAccountService channelAccountService;
    private final ChannelService channelService;
    private final ChannelConfigTransactions channelConfigTransactions;

    /** MID 与目标账号资源锁。 */
    @Override
    protected LockCommand lockCommand(RebindChannelMidAccountRequest request) {
        return LockCommand.tryLocks(List.of(ChannelConnectorLocks.channelMid(request.getMidId()),
            ChannelConnectorLocks.channelAccount(request.getChannelAccountId())),
            ChannelConnectorLocks.CHANNEL_CONFIG_WAIT, ChannelConnectorLocks.CHANNEL_CONFIG_LEASE);
    }

    @Override
    protected ChannelMidDetailView processWithinLock(RebindChannelMidAccountRequest request) {
        // 构建上下文
        ChannelMidDetailView view = channelMidService.getMid(request.getMidId());
        ChannelMid mid = view.getMid();
        ChannelAccount target = channelAccountService.loadAccount(request.getChannelAccountId());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ChannelMidContext context = new ChannelMidContext(mid, channelService.loadChannel(mid.getChannelId()),
            target, ChannelConfigChanges.of(idGenerator.nextId(), ChangeTargetType.CHANNEL_MID, mid.getMidId(),
                ChangeType.REBIND, request.getActorId(), now), now);

        // 业务验证
        checkRebindable(context);

        // 本地保存
        channelConfigTransactions.write(context.getChange(), () -> channelMidService.rebindAccount(context));
        return view;
    }

    /** MID 未关闭，目标账号可绑定且不同于当前账号。 */
    void checkRebindable(ChannelMidContext context) {
        ChannelMid mid = context.getMid();
        if (context.closed()) {
            throw new BizCheckFailException("MID 已关闭，不能改绑: " + mid.getMidId());
        }
        if (mid.getChannelAccountId().equals(context.getAccount().getChannelAccountId())) {
            throw new BizCheckFailException("MID 已绑定该技术账号: MID " + mid.getMidId() + ", 账号 "
                + mid.getChannelAccountId());
        }
        if (!context.accountBindable()) {
            throw new BizCheckFailException("目标技术账号不属于 MID 所在渠道、OE 不一致或已关闭: MID " + mid.getMidId()
                + ", 账号 " + context.getAccount().getChannelAccountId());
        }
    }
}
