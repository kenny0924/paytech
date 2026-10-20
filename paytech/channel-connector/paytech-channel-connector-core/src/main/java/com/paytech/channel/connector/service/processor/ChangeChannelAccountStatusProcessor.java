package com.paytech.channel.connector.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.core.constant.ChannelConnectorLocks;
import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.core.enums.ChangeTargetType;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.ChannelAccountContext;
import com.paytech.channel.connector.domain.ChannelAccountDetailView;
import com.paytech.channel.connector.domain.ChannelConfigChanges;
import com.paytech.channel.connector.domainservice.repository.ChannelAccountRepository;
import com.paytech.channel.connector.domainservice.repository.ChannelMidRepository;
import com.paytech.channel.connector.domainservice.service.ChannelAccountService;
import com.paytech.channel.connector.domainservice.service.PaymentChannelService;
import com.paytech.channel.connector.domainservice.validator.channel.ChannelStatusTransitionValidator;
import com.paytech.channel.connector.service.processor.request.ChangeChannelStatusRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 变更技术账号状态
 * <li>锁 channel-account:{id}；关闭时另锁所属渠道 channel:{channelId}，与该渠道下的 MID 创建互斥，保证关闭检查不被打破。</li>
 * <li>激活要求渠道 ACTIVE 且账号已写入配置；关闭要求没有未关闭的 MID 绑定本账号。状态写入以原状态为条件，同事务写入 STATUS 变更记录。</li>
 */
@Component
@AllArgsConstructor
public class ChangeChannelAccountStatusProcessor
    extends AbstractLockProcessor<ChangeChannelStatusRequest, ChannelAccountDetailView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final ChannelMidRepository channelMidRepository;
    private final ChannelAccountService channelAccountService;
    private final PaymentChannelService paymentChannelService;
    private final ChannelAccountRepository channelAccountRepository;

    /** 账号资源锁；关闭时读取不可变的所属渠道并一并加锁。 */
    @Override
    protected LockCommand lockCommand(ChangeChannelStatusRequest request) {
        List<String> keys = new ArrayList<>(List.of(ChannelConnectorLocks.channelAccount(request.getTargetId())));
        ChannelAccount account = channelAccountRepository.findById(request.getTargetId());
        if (request.getTargetStatus() == ChannelStatus.CLOSED && account != null) {
            keys.add(ChannelConnectorLocks.channel(account.getChannelId()));
        }
        return LockCommand.tryLocks(keys, ChannelConnectorLocks.CHANNEL_CONFIG_WAIT,
            ChannelConnectorLocks.CHANNEL_CONFIG_LEASE);
    }

    @Override
    protected ChannelAccountDetailView processWithinLock(ChangeChannelStatusRequest request) {
        // 构建上下文
        ChannelAccount account = channelAccountService.loadAccount(request.getTargetId());
        ChannelStatus target = request.getTargetStatus();
        ChannelStatusTransitionValidator.checkTransition("渠道技术账号 " + account.getChannelAccountId(),
            account.getAccountStatus(), target);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ChannelAccountContext context = new ChannelAccountContext(account,
            paymentChannelService.loadChannel(account.getChannelId()), ChannelConfigChanges.status(idGenerator.nextId(),
                ChangeTargetType.CHANNEL_ACCOUNT, account.getChannelAccountId(), account.getAccountStatus(), target,
                request.getActorId(), request.getReason(), now), now);

        // 业务验证
        long boundMids = channelMidRepository.countOpenByAccount(account.getChannelAccountId());
        checkPrerequisite(context, target, boundMids);

        // 本地保存
        channelAccountService.changeAccountStatus(context, target);
        return new ChannelAccountDetailView(account, boundMids);
    }

    /** 激活核对渠道与配置，关闭核对绑定 MID。 */
    void checkPrerequisite(ChannelAccountContext context, ChannelStatus target, long boundMids) {
        ChannelAccount account = context.getAccount();
        if (target == ChannelStatus.ACTIVE && !context.canActivate()) {
            throw new BizCheckFailException("技术账号激活前置不满足（渠道须为 ACTIVE 且账号已写入配置）: 账号 "
                + account.getChannelAccountId() + ", 渠道状态 " + context.getChannel().getChannelStatus().getCode()
                + ", 已配置 " + context.configured());
        }
        if (target == ChannelStatus.CLOSED && boundMids > 0) {
            throw new BizCheckFailException("仍有未关闭的 MID 绑定该技术账号，不能关闭: 账号 " + account.getChannelAccountId()
                + ", 未关闭 MID " + boundMids);
        }
    }
}
