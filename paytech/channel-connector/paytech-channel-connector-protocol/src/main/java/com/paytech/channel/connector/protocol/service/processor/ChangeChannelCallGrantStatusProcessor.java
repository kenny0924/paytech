package com.paytech.channel.connector.protocol.service.processor;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.enums.ChannelCallGrantStatus;
import com.paytech.channel.connector.protocol.core.configuration.ExternalApiConfigurationProperties;
import com.paytech.channel.connector.protocol.core.constant.ChannelLocks;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelCallGrant;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.domain.ChangeChannelCallGrantStatusContext;
import com.paytech.channel.connector.protocol.domain.ChannelCallGrantView;
import com.paytech.channel.connector.protocol.domain.ChannelConfigChanges;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelCallGrantRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelImplementationRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.StandardApiRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelCallGrantService;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelConfigChangeService;
import com.paytech.channel.connector.protocol.service.processor.request.ChangeChannelCallGrantStatusRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 启用或停用调用授权：授权只停用、不删除，再启用沿用同一行并递增版本
 * <li>TRY_LOCK 锁 channel-call-grant:{channelId}（channelId 取自已读取的授权，同一渠道的新建与启停互斥）。</li>
 * <li>版本不一致 409；目标状态与当前相同 400；再启用要求渠道仍实现该标准 API。写入以原版本与原状态为条件。</li>
 * <li>同事务写入 CHANNEL_CALL_GRANT / STATUS 变更记录（含原因）。</li>
 */
@Component
@AllArgsConstructor
public class ChangeChannelCallGrantStatusProcessor
    extends AbstractLockProcessor<ChangeChannelCallGrantStatusRequest, ChannelCallGrantView> {
    private final ChannelRepository channelRepository;
    private final StandardApiRepository standardApis;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final ChannelCallGrantService grantService;
    private final ChannelConfigChangeService changeService;
    private final ChannelCallGrantRepository callGrantRepository;
    private final ExternalApiConfigurationProperties properties;
    private final ChannelImplementationRepository implementations;

    /** 按授权所属渠道加锁；授权不存在时按 0 号渠道加锁后在锁内拒绝。 */
    @Override
    protected LockCommand lockCommand(ChangeChannelCallGrantStatusRequest request) {
        ChannelCallGrant grant = callGrantRepository.byId(request.getCallGrantId());
        long channelId = grant == null ? 0 : grant.getChannelId();
        return LockCommand.tryLock(ChannelLocks.channelCallGrant(channelId),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 校验版本、目标状态与再启用前置后条件更新并写入变更记录。 */
    @Override
    protected ChannelCallGrantView processWithinLock(ChangeChannelCallGrantStatusRequest request) {
        // 业务验证
        ChannelCallGrant grant = loadGrant(request.getCallGrantId());
        support.requireVersion(grant.getVersion(), request.getExpectedVersion());
        if (grant.getGrantStatus() == request.getTargetStatus()) {
            throw new BizCheckFailException("调用授权已是目标状态: " + grant.getCallGrantId() + ", "
                + request.getTargetStatus().getCode());
        }
        StandardApi standardApi = loadStandardApi(grant);
        if (request.getTargetStatus() == ChannelCallGrantStatus.ACTIVE) {
            requireImplemented(grant, standardApi);
        }

        // 本地保存
        Instant now = support.now();
        var context = new ChangeChannelCallGrantStatusContext(grant, request.getTargetStatus(), request.getActorId(),
            ChannelConfigChanges.status(support.nextId(), ChangeTargetType.CHANNEL_CALL_GRANT, grant.getCallGrantId(),
                grant.getGrantStatus(), request.getTargetStatus(), request.getActorId(), request.getReason(), now),
            now);
        transactions.write(() -> {
            grantService.changeGrantStatus(context);
            changeService.record(context.getChange());
            return null;
        });
        return ChannelCallGrants.view(grant, standardApi);
    }

    /** 读取授权；不存在业务拒绝。 */
    ChannelCallGrant loadGrant(long callGrantId) {
        ChannelCallGrant grant = callGrantRepository.byId(callGrantId);
        if (grant == null) {
            throw new BizCheckFailException("调用授权不存在: " + callGrantId);
        }
        return grant;
    }

    /** 授权引用的标准 API 必须存在；缺失说明数据被绕过应用删除，按系统错误处理。 */
    StandardApi loadStandardApi(ChannelCallGrant grant) {
        List<StandardApi> rows = standardApis.byIds(List.of(grant.getStandardApiId()));
        if (rows.isEmpty()) {
            throw new ErrorException("调用授权引用的标准 API 不存在: 授权 " + grant.getCallGrantId());
        }
        return rows.get(0);
    }

    /** 再启用要求渠道存在、未关闭且仍实现该标准 API。 */
    void requireImplemented(ChannelCallGrant grant, StandardApi standardApi) {
        Channel channel = channelRepository.byId(grant.getChannelId());
        if (channel != null && channel.getChannelStatus() == ChannelStatus.CLOSED) {
            throw new BizCheckFailException("渠道已关闭，不能再启用授权: 授权 " + grant.getCallGrantId());
        }
        if (channel == null || implementations.byKey(grant.getChannelId(), standardApi.getStandardApiId()) == null) {
            throw new BizCheckFailException("渠道没有实现该标准 API，不能再启用授权: 授权 " + grant.getCallGrantId()
                + ", 标准 API: " + standardApi.getStandardApiCode());
        }
    }
}
