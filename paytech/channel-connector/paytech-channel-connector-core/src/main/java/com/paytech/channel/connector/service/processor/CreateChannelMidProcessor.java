package com.paytech.channel.connector.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.core.constant.ChannelConnectorLocks;
import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.core.dal.dataobject.PaymentChannel;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.domain.ChannelConfigChanges;
import com.paytech.channel.connector.domain.ChannelMidContext;
import com.paytech.channel.connector.domain.ChannelMidDetailView;
import com.paytech.channel.connector.domainservice.service.ChannelAccountService;
import com.paytech.channel.connector.domainservice.service.ChannelMidService;
import com.paytech.channel.connector.domainservice.service.PaymentChannelService;
import com.paytech.channel.connector.service.processor.request.CreateChannelMidRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 创建 DRAFT MID
 * <li>锁所属渠道 channel:{channelId}，与渠道关闭、账号关闭互斥；渠道未关闭，账号属于该渠道、未关闭且 OE 与请求一致。</li>
 * <li>同渠道外部 MID 由唯一索引保护，重复按 409 拒绝；与 MID 同事务写入 CREATE 变更记录。</li>
 */
@Component
@AllArgsConstructor
public class CreateChannelMidProcessor extends AbstractLockProcessor<CreateChannelMidRequest, ChannelMidDetailView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final ChannelMidService channelMidService;
    private final ChannelAccountService channelAccountService;
    private final PaymentChannelService paymentChannelService;
    private final ChannelConfigTransactions channelConfigTransactions;

    /** 所属渠道资源锁。 */
    @Override
    protected LockCommand lockCommand(CreateChannelMidRequest request) {
        return LockCommand.tryLocks(List.of(ChannelConnectorLocks.channel(request.getChannelId())),
            ChannelConnectorLocks.CHANNEL_CONFIG_WAIT, ChannelConnectorLocks.CHANNEL_CONFIG_LEASE);
    }

    @Override
    protected ChannelMidDetailView processWithinLock(CreateChannelMidRequest request) {
        // 业务验证
        PaymentChannel channel = paymentChannelService.loadChannel(request.getChannelId());
        if (channel.getChannelStatus() == ChannelStatus.CLOSED) {
            throw new BizCheckFailException("渠道已关闭，不能创建 MID: " + channel.getChannelId());
        }
        ChannelAccount account = channelAccountService.loadAccount(request.getChannelAccountId());

        // 构建上下文
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ChannelMid mid = buildMid(request, now);
        ChannelMidContext context = new ChannelMidContext(mid, channel, account, ChannelConfigChanges.of(
            idGenerator.nextId(), ChangeTargetType.CHANNEL_MID, mid.getMidId(), ChangeType.CREATE, request.getActorId(),
            now), now);
        if (!context.accountBindable()) {
            throw new BizCheckFailException("技术账号不属于该渠道、已关闭或 OE 不一致: 账号 " + account.getChannelAccountId()
                + ", 渠道 " + channel.getChannelId() + ", OE " + request.getEntityNo());
        }

        // 本地保存
        channelConfigTransactions.write(context.getChange(), () -> channelMidService.createMid(context));
        return new ChannelMidDetailView(mid, List.of());
    }

    /** 构建 DRAFT MID。 */
    ChannelMid buildMid(CreateChannelMidRequest request, Instant now) {
        ChannelMid mid = new ChannelMid();
        mid.setMidId(idGenerator.nextId());
        mid.setChannelId(request.getChannelId());
        mid.setChannelAccountId(request.getChannelAccountId());
        mid.setExternalMidCode(request.getExternalMidCode());
        mid.setEntityNo(request.getEntityNo());
        mid.setMidStatus(ChannelStatus.DRAFT);
        mid.setCreateTime(now);
        mid.setUpdateTime(now);
        return mid;
    }
}
