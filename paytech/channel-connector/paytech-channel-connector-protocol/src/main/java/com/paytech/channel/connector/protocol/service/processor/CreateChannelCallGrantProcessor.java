package com.paytech.channel.connector.protocol.service.processor;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.enums.ChannelCallGrantStatus;
import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.protocol.core.configuration.ExternalApiConfigurationProperties;
import com.paytech.channel.connector.protocol.core.constant.ChannelLocks;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelCallGrant;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.domain.ChannelCallGrantView;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelCallGrantRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelImplementationRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.StandardApiRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelCallGrantService;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelConfigChangeService;
import com.paytech.channel.connector.protocol.service.processor.request.CreateChannelCallGrantRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 新建服务渠道调用授权（调用方服务 × 渠道 × 标准 API），初始 ACTIVE
 * <li>TRY_LOCK 锁 channel-call-grant:{channelId}，与同一渠道的授权启停互斥；唯一索引是最终保护。</li>
 * <li>渠道须存在、未关闭且为服务渠道，标准 API 须已被该渠道实现，否则 400；同一组合已存在 409（含现有授权 ID 与状态，停用的应再启用）。</li>
 * <li>同事务写入 CHANNEL_CALL_GRANT / CREATE 变更记录。</li>
 */
@Component
@AllArgsConstructor
public class CreateChannelCallGrantProcessor
    extends AbstractLockProcessor<CreateChannelCallGrantRequest, ChannelCallGrantView> {
    private final ChannelRepository channelRepository;
    private final StandardApiRepository standardApis;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final ChannelCallGrantService grantService;
    private final ChannelConfigChangeService changeService;
    private final ChannelCallGrantRepository callGrantRepository;
    private final ExternalApiConfigurationProperties properties;
    private final ChannelImplementationRepository implementations;

    /** 渠道级授权锁。 */
    @Override
    protected LockCommand lockCommand(CreateChannelCallGrantRequest request) {
        return LockCommand.tryLock(ChannelLocks.channelCallGrant(request.getChannelId()),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 校验渠道与标准 API 后插入授权并写入变更记录。 */
    @Override
    protected ChannelCallGrantView processWithinLock(CreateChannelCallGrantRequest request) {
        // 业务验证
        Channel channel = loadServiceChannel(request.getChannelId());
        StandardApi standardApi = loadImplementedStandardApi(channel, request.getStandardApiCode());
        requireNewCombination(request.getCallerService(), channel, standardApi);

        // 本地保存
        Instant now = support.now();
        ChannelCallGrant grant = buildGrant(request, standardApi, now);
        String objectCode = channel.getChannelCode() + "/" + standardApi.getStandardApiCode() + "/"
            + request.getCallerService();
        transactions.write(() -> {
            grantService.createGrant(grant);
            changeService.record(support.changed(ChangeTargetType.CHANNEL_CALL_GRANT, grant.getCallGrantId(),
                ChangeType.CREATE, request.getActorId(), objectCode, 0, grant.getVersion(), List.of(), null, now));
            return null;
        });
        return ChannelCallGrants.view(grant, standardApi);
    }

    /** 渠道须存在、未关闭且为服务渠道。 */
    Channel loadServiceChannel(long channelId) {
        Channel channel = channelRepository.byId(channelId);
        if (channel == null) {
            throw new BizCheckFailException("渠道不存在: " + channelId);
        }
        if (channel.getChannelType() != ChannelType.SERVICE) {
            throw new BizCheckFailException("只有服务渠道可以设置调用授权: " + channel.getChannelCode());
        }
        if (channel.getChannelStatus() == ChannelStatus.CLOSED) {
            throw new BizCheckFailException("渠道已关闭，不能新建调用授权: " + channel.getChannelCode());
        }
        return channel;
    }

    /** 标准 API 须存在且已被该渠道实现。 */
    StandardApi loadImplementedStandardApi(Channel channel, String standardApiCode) {
        StandardApi standardApi = standardApis.byCode(standardApiCode);
        if (standardApi == null || implementations.byKey(channel.getChannelId(),
            standardApi.getStandardApiId()) == null) {
            throw new BizCheckFailException("渠道没有实现该标准 API: " + channel.getChannelCode() + ", 标准 API: "
                + standardApiCode);
        }
        return standardApi;
    }

    /** 同一组合只有一行：已存在时 409，提示现有授权 ID 与状态。 */
    void requireNewCombination(String callerService, Channel channel, StandardApi standardApi) {
        ChannelCallGrant existing = callGrantRepository.byKey(callerService, channel.getChannelId(),
            standardApi.getStandardApiId());
        if (existing != null) {
            throw new ConcurrentRequestException("调用授权已存在: 授权 ID " + existing.getCallGrantId() + ", 状态 "
                + existing.getGrantStatus().getCode() + "；停用的授权请改为再启用");
        }
    }

    /** 构建 ACTIVE 授权，初始版本 1。 */
    ChannelCallGrant buildGrant(CreateChannelCallGrantRequest request, StandardApi standardApi, Instant now) {
        ChannelCallGrant grant = new ChannelCallGrant();
        grant.setCallGrantId(support.nextId());
        grant.setCallerService(request.getCallerService());
        grant.setChannelId(request.getChannelId());
        grant.setStandardApiId(standardApi.getStandardApiId());
        grant.setGrantStatus(ChannelCallGrantStatus.ACTIVE);
        grant.setVersion(1);
        grant.setCreatedBy(request.getActorId());
        grant.setUpdatedBy(request.getActorId());
        grant.setCreateTime(now);
        grant.setUpdateTime(now);
        return grant;
    }
}
