package com.paytech.channel.connector.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.core.constant.ChannelConnectorLocks;
import com.paytech.channel.connector.core.dal.dataobject.PaymentChannel;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.domain.ChannelConfigChanges;
import com.paytech.channel.connector.domain.ChannelContext;
import com.paytech.channel.connector.domainservice.repository.ChannelAccountRepository;
import com.paytech.channel.connector.domainservice.repository.ChannelMidRepository;
import com.paytech.channel.connector.domainservice.service.PaymentChannelService;
import com.paytech.channel.connector.domainservice.validator.channel.ChannelStatusTransitionValidator;
import com.paytech.channel.connector.protocol.domainservice.service.IntegrationLookupService;
import com.paytech.channel.connector.service.processor.request.ChangeChannelStatusRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 变更渠道状态
 * <li>锁 channel:{channelId}，与其下账号创建、MID 创建、账号关闭互斥，保证关闭时"无未关闭子项"的检查不被并发打破。</li>
 * <li>激活前在锁内、事务外经 protocol 公开查询核对同编码接入方：不存在业务拒绝，不写库。</li>
 * <li>关闭要求渠道下没有未关闭的技术账号与 MID；状态写入以原状态为条件，并同事务写入 STATUS 变更记录。</li>
 */
@Component
@AllArgsConstructor
public class ChangeChannelStatusProcessor extends AbstractLockProcessor<ChangeChannelStatusRequest, PaymentChannel> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final ChannelMidRepository channelMidRepository;
    private final PaymentChannelService paymentChannelService;
    private final ChannelAccountRepository channelAccountRepository;
    private final IntegrationLookupService integrationLookupService;
    private final ChannelConfigTransactions channelConfigTransactions;

    /** 渠道资源锁。 */
    @Override
    protected LockCommand lockCommand(ChangeChannelStatusRequest request) {
        return LockCommand.tryLocks(List.of(ChannelConnectorLocks.channel(request.getTargetId())),
            ChannelConnectorLocks.CHANNEL_CONFIG_WAIT, ChannelConnectorLocks.CHANNEL_CONFIG_LEASE);
    }

    @Override
    protected PaymentChannel processWithinLock(ChangeChannelStatusRequest request) {
        // 业务验证
        PaymentChannel channel = paymentChannelService.loadChannel(request.getTargetId());
        ChannelStatus target = request.getTargetStatus();
        ChannelStatusTransitionValidator.checkTransition("渠道 " + channel.getChannelId(), channel.getChannelStatus(),
            target);
        checkPrerequisite(channel, target);

        // 本地保存
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ChannelContext context = new ChannelContext(channel, ChannelConfigChanges.status(idGenerator.nextId(),
            ChangeTargetType.CHANNEL, channel.getChannelId(), channel.getChannelStatus(), target,
            request.getActorId(), request.getReason(), now), now);
        channelConfigTransactions.write(context.getChange(),
            () -> paymentChannelService.changeChannelStatus(context, target));
        return channel;
    }

    /** 激活核对同编码接入方，关闭核对子项；其他迁移无前置。 */
    void checkPrerequisite(PaymentChannel channel, ChannelStatus target) {
        if (target == ChannelStatus.ACTIVE) {
            checkIntegration(channel);
        }
        if (target == ChannelStatus.CLOSED) {
            checkClosable(channel);
        }
    }

    /** 事务外经 protocol 查询同编码接入方；不区分接入方启停状态。 */
    void checkIntegration(PaymentChannel channel) {
        if (!integrationLookupService.exists(channel.getChannelCode())) {
            throw new BizCheckFailException("不存在同编码接入方: " + channel.getChannelCode());
        }
    }

    /** 渠道下不得有未关闭的账号或 MID。 */
    void checkClosable(PaymentChannel channel) {
        long openAccounts = channelAccountRepository.countOpenByChannel(channel.getChannelId());
        long openMids = channelMidRepository.countOpenByChannel(channel.getChannelId());
        if (!ChannelContext.canClose(openAccounts, openMids)) {
            throw new BizCheckFailException("渠道下仍有未关闭的技术账号或 MID，不能关闭: 渠道 " + channel.getChannelId()
                + ", 未关闭账号 " + openAccounts + ", 未关闭 MID " + openMids);
        }
    }
}
