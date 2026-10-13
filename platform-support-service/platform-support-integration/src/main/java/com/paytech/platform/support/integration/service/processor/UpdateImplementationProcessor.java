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
import com.paytech.platform.support.integration.domain.UpdateImplementationContext;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationImplementationRepository;
import com.paytech.platform.support.integration.domainservice.service.IntegrationImplementationManagementService;
import com.paytech.platform.support.integration.service.processor.request.UpdateImplementationRequest;

import lombok.AllArgsConstructor;

/**
 * E26 修改渠道标准 API 实现：全模块配置锁内依次锁定接入方、标准 API 与原行、核对版本，流程静态校验后按原版本替换定义。
 *
 * <p>标准 API 编码来自路径，不可修改。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateImplementationProcessor
    extends AbstractLockProcessor<UpdateImplementationRequest, ExternalApiMutation> {

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
    protected LockCommand lockCommand(UpdateImplementationRequest request) {
        return LockCommand.tryLock(ExternalApiPermissions.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(UpdateImplementationRequest request) {
        return transactions.write(() -> {
            var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(), null,
                null, false, ExternalApiPermissions.MANAGE);
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
            var context = new UpdateImplementationContext(row, json, policy.parseId(request.getActor()), support.now());
            service.update(context);
            String objectCode = integration.getIntegrationCode() + "/" + standardApi.getStandardApiCode();
            audit.record(() -> support.changed(request.getActor(), actor.member().tenantId(),
                CreateImplementationProcessor.OBJECT_TYPE, objectCode, beforeVersion, row.getVersion(), changes,
                request.getReason(), context.getUpdateTime()));
            return new ExternalApiMutation(CreateImplementationProcessor.OBJECT_TYPE, objectCode, row.getVersion());
        });
    }
}
