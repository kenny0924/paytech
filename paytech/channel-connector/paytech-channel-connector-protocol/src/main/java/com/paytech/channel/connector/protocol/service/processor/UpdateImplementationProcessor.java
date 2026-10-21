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
import com.paytech.channel.connector.protocol.core.dal.dataobject.Integration;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationImplementation;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiMutation;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.UpdateImplementationContext;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationImplementationRepository;
import com.paytech.channel.connector.protocol.domainservice.service.IntegrationImplementationManagementService;
import com.paytech.channel.connector.protocol.service.processor.request.UpdateImplementationRequest;

import lombok.AllArgsConstructor;

/**
 * E26 修改渠道标准 API 实现：全模块配置锁内依次锁定接入方、标准 API 与原行、核对版本，流程静态校验后按原版本替换定义。
 *
 * <p>标准 API 编码来自路径，不可修改。
 */
@Component
@AllArgsConstructor
public class UpdateImplementationProcessor
    extends AbstractLockProcessor<UpdateImplementationRequest, ExternalApiMutation> {

    private final ChannelConfigChangeService changeService;
    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final ImplementationSupport flows;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final ExternalApiConfigurationProperties properties;
    private final IntegrationImplementationRepository implementations;
    private final IntegrationImplementationManagementService service;

    @Override
    protected LockCommand lockCommand(UpdateImplementationRequest request) {
        return LockCommand.tryLock(ExternalApiLocks.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(UpdateImplementationRequest request) {
        return transactions.write(() -> {
            Integration integration = children.lockIntegration(request.getIntegrationCode());
            var standardApi = flows.lockStandardApi(request.getStandardApiCode());
            IntegrationImplementation row = support.requireFound(implementations.lock(integration.getIntegrationId(),
                standardApi.getStandardApiId()));
            support.requireVersion(row.getVersion(), request.getExpectedVersion());
            var result = flows.check(standardApi, integration.getIntegrationId(), request.getFlowDefinition());
            support.requireValid(result.getIssues());
            String json = result.getSummary().getNormalizedJson();
            var changes = List.of(new ConfigurationFieldChange(ConfigurationField.DEFINITION_DIGEST,
                codec.sha256(row.getFlowDefinitionJson()), codec.sha256(json)));
            int beforeVersion = row.getVersion();
            var context = new UpdateImplementationContext(row, json, request.getActorId(), support.now());
            service.update(context);
            String objectCode = integration.getIntegrationCode() + "/" + standardApi.getStandardApiCode();
            changeService.record(support.changed(ChangeTargetType.INTEGRATION_IMPLEMENTATION, row.getIntegrationStandardApiId(), ChangeType.UPDATE,
                request.getActorId(), objectCode, beforeVersion, row.getVersion(), changes,
                request.getReason(), context.getUpdateTime()));
            return new ExternalApiMutation(CreateImplementationProcessor.OBJECT_TYPE, objectCode, row.getVersion());
        });
    }
}
