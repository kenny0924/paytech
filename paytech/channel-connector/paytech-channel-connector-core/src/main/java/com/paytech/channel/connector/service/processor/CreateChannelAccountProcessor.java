package com.paytech.channel.connector.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.core.constant.ChannelConnectorLocks;
import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.protocol.domain.ChannelIdentity;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.ChannelAccountContext;
import com.paytech.channel.connector.domain.ChannelAccountDetailView;
import com.paytech.channel.connector.protocol.domain.ChannelConfigChanges;
import com.paytech.channel.connector.domainservice.service.ChannelAccountService;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelService;
import com.paytech.channel.connector.service.processor.request.CreateChannelAccountRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 创建 DRAFT 技术账号，尚无配置
 * <li>锁所属渠道 channel:{channelId}，与渠道关闭互斥；渠道须存在且未关闭。OE 只校验格式，不向 CCT 核对。</li>
 * <li>与账号同事务写入 CREATE 变更记录。</li>
 */
@Component
@AllArgsConstructor
public class CreateChannelAccountProcessor
    extends AbstractLockProcessor<CreateChannelAccountRequest, ChannelAccountDetailView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final ChannelAccountService channelAccountService;
    private final ChannelService channelService;
    private final ChannelConfigTransactions channelConfigTransactions;

    /** 所属渠道资源锁。 */
    @Override
    protected LockCommand lockCommand(CreateChannelAccountRequest request) {
        return LockCommand.tryLocks(List.of(ChannelConnectorLocks.channel(request.getChannelId())),
            ChannelConnectorLocks.CHANNEL_CONFIG_WAIT, ChannelConnectorLocks.CHANNEL_CONFIG_LEASE);
    }

    @Override
    protected ChannelAccountDetailView processWithinLock(CreateChannelAccountRequest request) {
        // 业务验证
        ChannelIdentity channel = channelService.loadChannel(request.getChannelId());
        if (channel.getChannelStatus() == ChannelStatus.CLOSED) {
            throw new BizCheckFailException("渠道已关闭，不能创建技术账号: " + channel.getChannelId());
        }
        checkEntityBinding(channel, request.getEntityNo());

        // 构建上下文
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ChannelAccount account = buildAccount(request, now);
        ChannelAccountContext context = new ChannelAccountContext(account, channel, ChannelConfigChanges.of(
            idGenerator.nextId(), ChangeTargetType.CHANNEL_ACCOUNT, account.getChannelAccountId(), ChangeType.CREATE,
            request.getActorId(), now), now);

        // 本地保存
        channelConfigTransactions.write(context.getChange(), () -> channelAccountService.createAccount(context));
        return new ChannelAccountDetailView(account, 0);
    }

    /** 支付渠道的账号必须绑定 OE；服务渠道的账号（凭据）不绑定 OE。 */
    void checkEntityBinding(ChannelIdentity channel, String entityNo) {
        if (!channel.service() && entityNo == null) {
            throw new BizCheckFailException("支付渠道的技术账号必须指定 OE: 渠道 " + channel.getChannelCode());
        }
        if (channel.service() && entityNo != null) {
            throw new BizCheckFailException("服务渠道的凭据不绑定 OE: 渠道 " + channel.getChannelCode() + ", OE " + entityNo);
        }
    }

    /** 构建 DRAFT 账号，无 Vault Token。 */
    ChannelAccount buildAccount(CreateChannelAccountRequest request, Instant now) {
        ChannelAccount account = new ChannelAccount();
        account.setChannelAccountId(idGenerator.nextId());
        account.setChannelId(request.getChannelId());
        account.setEntityNo(request.getEntityNo());
        account.setAccountName(request.getAccountName());
        account.setAccountStatus(ChannelStatus.DRAFT);
        account.setRemark(request.getRemark());
        account.setCreateTime(now);
        account.setUpdateTime(now);
        return account;
    }
}
