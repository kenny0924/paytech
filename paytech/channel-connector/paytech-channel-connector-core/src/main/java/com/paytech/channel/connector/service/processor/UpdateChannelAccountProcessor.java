package com.paytech.channel.connector.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.core.constant.ChannelConnectorLocks;
import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.core.enums.ChangeTargetType;
import com.paytech.channel.connector.core.enums.ChangeType;
import com.paytech.channel.connector.domain.ChannelAccountContext;
import com.paytech.channel.connector.domain.ChannelAccountDetailView;
import com.paytech.channel.connector.domain.ChannelConfigChanges;
import com.paytech.channel.connector.domainservice.repository.ChannelMidRepository;
import com.paytech.channel.connector.domainservice.service.ChannelAccountService;
import com.paytech.channel.connector.domainservice.service.PaymentChannelService;
import com.paytech.channel.connector.service.processor.request.UpdateChannelAccountRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 修改技术账号名称与备注；OE、所属渠道与配置不变
 * <li>锁 channel-account:{id}；已关闭账号拒绝，写入以未关闭为条件并同事务写入 UPDATE 变更记录。</li>
 */
@Component
@AllArgsConstructor
public class UpdateChannelAccountProcessor
    extends AbstractLockProcessor<UpdateChannelAccountRequest, ChannelAccountDetailView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final ChannelMidRepository channelMidRepository;
    private final ChannelAccountService channelAccountService;
    private final PaymentChannelService paymentChannelService;

    /** 账号资源锁。 */
    @Override
    protected LockCommand lockCommand(UpdateChannelAccountRequest request) {
        return LockCommand.tryLocks(List.of(ChannelConnectorLocks.channelAccount(request.getChannelAccountId())),
            ChannelConnectorLocks.CHANNEL_CONFIG_WAIT, ChannelConnectorLocks.CHANNEL_CONFIG_LEASE);
    }

    @Override
    protected ChannelAccountDetailView processWithinLock(UpdateChannelAccountRequest request) {
        // 业务验证
        ChannelAccount account = channelAccountService.loadAccount(request.getChannelAccountId());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ChannelAccountContext context = new ChannelAccountContext(account,
            paymentChannelService.loadChannel(account.getChannelId()), ChannelConfigChanges.of(idGenerator.nextId(),
                ChangeTargetType.CHANNEL_ACCOUNT, account.getChannelAccountId(), ChangeType.UPDATE,
                request.getActorId(), now), now);
        if (context.closed()) {
            throw new BizCheckFailException("渠道技术账号已关闭，不能修改: " + account.getChannelAccountId());
        }

        // 本地保存
        channelAccountService.updateProfile(context, request.getAccountName(), request.getRemark());
        return new ChannelAccountDetailView(account, channelMidRepository.countOpenByAccount(account.getChannelAccountId()));
    }
}
