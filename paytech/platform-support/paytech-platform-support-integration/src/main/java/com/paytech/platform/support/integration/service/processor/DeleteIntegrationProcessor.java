package com.paytech.platform.support.integration.service.processor;

import java.util.ArrayList;
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
import com.paytech.platform.support.integration.domain.ExternalApiMutation;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationApiRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationImplementationRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationScriptRepository;
import com.paytech.platform.support.integration.domainservice.service.IntegrationManagementService;
import com.paytech.platform.support.integration.service.processor.request.DeleteExternalApiConfigRequest;

import lombok.AllArgsConstructor;

/**
 * E10 删除接入方：全模块配置锁内锁定原行、核对版本；仍有原子 API、脚本或标准 API 实现时拒绝，否则物理删除。
 *
 * <p>下级对象的新增同样持有该配置锁，锁内的数量检查在提交前不会被绕过。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class DeleteIntegrationProcessor
    extends AbstractLockProcessor<DeleteExternalApiConfigRequest, ExternalApiMutation> {

    private final OperationsAuditService audit;
    private final IntegrationRepository integrations;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final IntegrationApiRepository integrationApis;
    private final IntegrationManagementService service;
    private final OperationsManagementPolicyService policy;
    private final IntegrationScriptRepository integrationScripts;
    private final ExternalApiConfigurationProperties properties;
    private final IntegrationImplementationRepository implementations;

    @Override
    protected LockCommand lockCommand(DeleteExternalApiConfigRequest request) {
        return LockCommand.tryLock(ExternalApiPermissions.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(DeleteExternalApiConfigRequest request) {
        return transactions.write(() -> {
            var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(), null,
                null, false, ExternalApiPermissions.MANAGE);
            Integration row = support.requireFound(integrations.lock(request.getCode()));
            support.requireVersion(row.getVersion(), request.getExpectedVersion());
            requireNoChildren(row);
            int version = row.getVersion();
            service.delete(row);
            var changes = List.of(
                new ConfigurationFieldChange(ConfigurationField.CODE, row.getIntegrationCode(), null),
                new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, row.getIntegrationName(), null));
            audit.record(() -> support.changed(request.getActor(), actor.member().tenantId(), "INTEGRATION",
                row.getIntegrationCode(), version, version, changes, request.getReason(), support.now()));
            return new ExternalApiMutation("INTEGRATION", row.getIntegrationCode(), version);
        });
    }

    /** 仍有下级配置时拒绝，提示中列出各类数量。 */
    void requireNoChildren(Integration row) {
        long id = row.getIntegrationId();
        var remaining = new ArrayList<String>();
        long apis = integrationApis.countByIntegrations(List.of(id)).getOrDefault(id, 0L);
        long scripts = integrationScripts.countByIntegrations(List.of(id)).getOrDefault(id, 0L);
        int implementationCount = implementations.allByIntegration(id).size();
        if (apis > 0) {
            remaining.add("原子 API " + apis + " 个");
        }
        if (scripts > 0) {
            remaining.add("公共脚本 " + scripts + " 个");
        }
        if (implementationCount > 0) {
            remaining.add("标准 API 实现 " + implementationCount + " 个");
        }
        if (!remaining.isEmpty()) {
            throw new BizCheckFailException("接入方仍有下级配置，不能删除：" + String.join("、", remaining));
        }
    }
}
