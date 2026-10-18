package com.paytech.platform.support.integration.service.processor;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationField;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationFieldChange;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.integration.core.configuration.ExternalApiConfigurationProperties;
import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import com.paytech.platform.support.integration.domain.ExternalApiConfigurationCodec;
import com.paytech.platform.support.integration.domain.ExternalApiMutation;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationRepository;
import com.paytech.platform.support.integration.domainservice.service.IntegrationManagementService;
import com.paytech.platform.support.integration.domainservice.validator.IntegrationEndpointValidator;
import com.paytech.platform.support.integration.service.processor.request.CreateIntegrationRequest;

import lombok.AllArgsConstructor;

/** E08 创建接入方：全模块配置锁内，在一个本地事务中完成权限复核、地址静态校验、编码查重与保存。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateIntegrationProcessor extends AbstractLockProcessor<CreateIntegrationRequest, ExternalApiMutation> {

    private final OperationsAuditService audit;
    private final ExternalApiConfigurationCodec codec;
    private final IntegrationRepository integrations;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final IntegrationEndpointValidator validator;
    private final IntegrationManagementService service;
    private final OperationsManagementPolicyService policy;
    private final ExternalApiConfigurationProperties properties;

    @Override
    protected LockCommand lockCommand(CreateIntegrationRequest request) {
        return LockCommand.tryLock(ExternalApiPermissions.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(CreateIntegrationRequest request) {
        return transactions.write(() -> {
            var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(), null,
                null, false, ExternalApiPermissions.MANAGE);
            support.requireValid(validator.validate(request.getEndpoints()));
            if (integrations.byCode(request.getCode()) != null) {
                throw new BizCheckFailException("接入方编码已经存在：" + request.getCode());
            }
            Integration row = buildIntegration(request, policy.parseId(request.getActor()));
            service.create(row);
            audit.record(() -> support.changed(request.getActor(), actor.member().tenantId(), "INTEGRATION",
                request.getCode(), 0, 1, changes(row), request.getReason(), row.getCreateTime()));
            return new ExternalApiMutation("INTEGRATION", request.getCode(), 1);
        });
    }

    /** 插入前准备标识、初始版本 1 与规范化地址 JSON。 */
    Integration buildIntegration(CreateIntegrationRequest request, long actorId) {
        Instant now = support.now();
        return new Integration(support.nextId(), request.getCode(), request.getName(), request.getDescription(),
            codec.endpointsJson(request.getEndpoints()), 1, actorId, actorId, now, now);
    }

    /** 新建时各字段的前值为空，地址只记录摘要。 */
    List<ConfigurationFieldChange> changes(Integration row) {
        return List.of(new ConfigurationFieldChange(ConfigurationField.CODE, null, row.getIntegrationCode()),
            new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, null, row.getIntegrationName()),
            new ConfigurationFieldChange(ConfigurationField.DESCRIPTION, null, row.getDescription()),
            new ConfigurationFieldChange(ConfigurationField.ENDPOINTS_DIGEST, null, codec.sha256(row.getEndpoints())));
    }
}
