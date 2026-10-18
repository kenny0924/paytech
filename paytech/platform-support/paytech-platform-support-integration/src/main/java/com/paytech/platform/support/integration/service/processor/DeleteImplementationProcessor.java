package com.paytech.platform.support.integration.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationField;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationFieldChange;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.integration.core.configuration.ExternalApiConfigurationProperties;
import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationImplementation;
import com.paytech.platform.support.integration.domain.ExternalApiConfigurationCodec;
import com.paytech.platform.support.integration.domain.ExternalApiMutation;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationImplementationRepository;
import com.paytech.platform.support.integration.domainservice.service.IntegrationImplementationManagementService;
import com.paytech.platform.support.integration.service.processor.request.DeleteIntegrationChildRequest;

import lombok.AllArgsConstructor;

/**
 * E27 删除渠道标准 API 实现：全模块配置锁内依次锁定接入方、标准 API 与原行、核对版本后物理删除。
 *
 * <p>实现没有下游配置引用；运行时调用方在实现缺失时按内部接口返回 Problem。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class DeleteImplementationProcessor
    extends AbstractLockProcessor<DeleteIntegrationChildRequest, ExternalApiMutation> {

    private final OperationsAuditService audit;
    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final ImplementationSupport flows;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final OperationsManagementPolicyService policy;
    private final ExternalApiConfigurationProperties properties;
    private final IntegrationImplementationRepository implementations;
    private final IntegrationImplementationManagementService service;

    @Override
    protected LockCommand lockCommand(DeleteIntegrationChildRequest request) {
        return LockCommand.tryLock(ExternalApiPermissions.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(DeleteIntegrationChildRequest request) {
        return transactions.write(() -> {
            var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(), null,
                null, false, ExternalApiPermissions.MANAGE);
            Integration integration = children.lockIntegration(request.getIntegrationCode());
            var standardApi = flows.lockStandardApi(request.getCode());
            IntegrationImplementation row = support.requireFound(implementations.lock(integration.getIntegrationId(),
                standardApi.getStandardApiId()));
            support.requireVersion(row.getVersion(), request.getExpectedVersion());
            int version = row.getVersion();
            service.delete(row);
            String objectCode = integration.getIntegrationCode() + "/" + standardApi.getStandardApiCode();
            var changes = List.of(new ConfigurationFieldChange(ConfigurationField.CODE,
                standardApi.getStandardApiCode(), null), new ConfigurationFieldChange(
                ConfigurationField.DEFINITION_DIGEST, codec.sha256(row.getFlowDefinitionJson()), null));
            audit.record(() -> support.changed(request.getActor(), actor.member().tenantId(),
                CreateImplementationProcessor.OBJECT_TYPE, objectCode, version, version, changes, request.getReason(),
                support.now()));
            return new ExternalApiMutation(CreateImplementationProcessor.OBJECT_TYPE, objectCode, version);
        });
    }
}
