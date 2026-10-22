package com.paytech.channel.connector.protocol.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelConfigChangeService;
import com.paytech.channel.connector.protocol.core.enums.ConfigurationField;
import com.paytech.channel.connector.protocol.domain.ConfigurationFieldChange;
import com.paytech.channel.connector.protocol.core.configuration.ExternalApiConfigurationProperties;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelImplementation;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiMutation;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelImplementationRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelImplementationManagementService;
import com.paytech.channel.connector.protocol.service.processor.request.DeleteIntegrationChildRequest;

import com.paytech.channel.connector.protocol.domainservice.repository.ChannelCallGrantRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/**
 * E27 删除渠道标准 API 实现：全模块配置锁内依次锁定渠道、标准 API 与原行、核对版本后物理删除。
 *
 * <p>实现没有下游配置引用；运行时调用方在实现缺失时按内部接口返回 Problem。
 *
 * <p>服务渠道的该标准 API 仍有 ACTIVE 调用授权时返回 409，须先停用授权；授权写入使用渠道级授权锁，二者并发时的残余窗口只会留下
 * 指向已删除实现的授权，执行时按实现不存在未发出处理。
 */
@Component
@AllArgsConstructor
public class DeleteImplementationProcessor
    extends AbstractLockProcessor<DeleteIntegrationChildRequest, ExternalApiMutation> {

    private final ChannelConfigChangeService changeService;
    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final ImplementationSupport flows;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final ExternalApiConfigurationProperties properties;
    private final ChannelImplementationRepository implementations;
    private final ChannelImplementationManagementService service;
    private final ChannelCallGrantRepository callGrants;

    @Override
    protected LockCommand lockCommand(DeleteIntegrationChildRequest request) {
        return LockCommand.tryLock(ExternalApiLocks.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(DeleteIntegrationChildRequest request) {
        return transactions.write(() -> {
            Channel integration = children.lockChannel(request.getIntegrationCode());
            var standardApi = flows.lockStandardApi(request.getCode());
            ChannelImplementation row = support.requireFound(implementations.lock(integration.getChannelId(),
                standardApi.getStandardApiId()));
            support.requireVersion(row.getVersion(), request.getExpectedVersion());
            requireNoActiveGrant(integration, standardApi.getStandardApiId(), standardApi.getStandardApiCode());
            int version = row.getVersion();
            service.delete(row);
            String objectCode = integration.getChannelCode() + "/" + standardApi.getStandardApiCode();
            var changes = List.of(new ConfigurationFieldChange(ConfigurationField.CODE,
                standardApi.getStandardApiCode(), null), new ConfigurationFieldChange(
                ConfigurationField.DEFINITION_DIGEST, codec.sha256(row.getFlowDefinitionJson()), null));
            changeService.record(support.changed(ChangeTargetType.CHANNEL_IMPLEMENTATION, row.getChannelStandardApiId(), ChangeType.DELETE,
                request.getActorId(), objectCode, version, version, changes, request.getReason(), support.now()));
            return new ExternalApiMutation(CreateImplementationProcessor.OBJECT_TYPE, objectCode, version);
        });
    }

    /** 仍有 ACTIVE 调用授权时拒绝删除，提示先停用授权。 */
    void requireNoActiveGrant(Channel channel, long standardApiId, String standardApiCode) {
        long active = callGrants.countActiveByStandardApi(channel.getChannelId(), standardApiId);
        if (active > 0) {
            throw new ConcurrentRequestException("渠道该标准 API 仍有 " + active + " 个 ACTIVE 调用授权，请先停用后再删除实现: "
                + channel.getChannelCode() + ", 标准 API: " + standardApiCode);
        }
    }
}
