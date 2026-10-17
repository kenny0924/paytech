package com.paytech.platform.support.integration.service.processor;

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
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationApi;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationScript;
import com.paytech.platform.support.integration.domain.ExternalApiMutation;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationScriptRepository;
import com.paytech.platform.support.integration.domainservice.service.IntegrationScriptManagementService;
import com.paytech.platform.support.integration.service.processor.request.DeleteIntegrationChildRequest;

import lombok.AllArgsConstructor;

/**
 * E21 删除公共脚本：全模块配置锁内锁定接入方与原行、核对版本；仍被原子 API 引用时拒绝并列出引用方，否则物理删除。
 *
 * <p>原子 API 保存同样持有该配置锁，锁内扫描到的引用在提交前不会变化。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class DeleteIntegrationScriptProcessor
    extends AbstractLockProcessor<DeleteIntegrationChildRequest, ExternalApiMutation> {

    private final OperationsAuditService audit;
    private final IntegrationChildSupport children;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final IntegrationScriptRepository integrationScripts;
    private final OperationsManagementPolicyService policy;
    private final IntegrationScriptManagementService service;
    private final ExternalApiConfigurationProperties properties;

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
            IntegrationScript row = support.requireFound(integrationScripts.lock(integration.getIntegrationId(),
                request.getCode()));
            support.requireVersion(row.getVersion(), request.getExpectedVersion());
            var users = children.scriptUsers(integration.getIntegrationId(), row.getScriptCode());
            if (!users.isEmpty()) {
                throw new BizCheckFailException("公共脚本仍被原子 API 引用，不能删除："
                    + String.join("、", users.stream().map(IntegrationApi::getApiCode).sorted().toList()));
            }
            int version = row.getVersion();
            service.delete(row);
            String objectCode = integration.getIntegrationCode() + "/" + row.getScriptCode();
            var changes = List.of(new ConfigurationFieldChange(ConfigurationField.CODE, row.getScriptCode(), null),
                new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, row.getScriptName(), null));
            audit.record(() -> support.changed(request.getActor(), actor.member().tenantId(), "INTEGRATION_SCRIPT",
                objectCode, version, version, changes, request.getReason(), support.now()));
            return new ExternalApiMutation("INTEGRATION_SCRIPT", objectCode, version);
        });
    }
}
