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
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationVerifyAccount;
import com.paytech.platform.support.integration.domain.ExternalApiConfigurationCodec;
import com.paytech.platform.support.integration.domain.ExternalApiMutation;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationVerifyAccountRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationVerifyCaseRepository;
import com.paytech.platform.support.integration.domainservice.service.IntegrationVerifyConfigService;

import lombok.AllArgsConstructor;
import com.paytech.platform.support.integration.service.processor.request.SaveVerifyAccountRequest;

/** V04 按当前版本整体修改验证账号：配置锁内锁定接入方与账号行，校验后条件更新并递增版本。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateVerifyAccountProcessor extends AbstractLockProcessor<SaveVerifyAccountRequest, ExternalApiMutation> {

    private final VerifySupport verify;
    private final OperationsAuditService audit;
    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final IntegrationVerifyAccountRepository accounts;
    private final IntegrationVerifyCaseRepository cases;
    private final OperationsManagementPolicyService policy;
    private final IntegrationVerifyConfigService service;
    private final ExternalApiConfigurationProperties properties;

    @Override
    protected LockCommand lockCommand(SaveVerifyAccountRequest request) {
        return LockCommand.tryLock(ExternalApiPermissions.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(SaveVerifyAccountRequest request) {
        return transactions.write(() -> {
            var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(), null,
                null, false, ExternalApiPermissions.MANAGE);
            Integration integration = children.lockIntegration(request.getIntegrationCode());
            IntegrationVerifyAccount row = support.requireFound(accounts.lock(integration.getIntegrationId(),
                request.getCode()));
            support.requireVersion(row.getVersion(), request.getExpectedVersion());
            support.requireValid(verify.accountIssues(request.getParameters(), request.getCredentials()));
            var before = new IntegrationVerifyAccount(row.getVerifyAccountId(), row.getIntegrationId(),
                row.getAccountCode(), row.getAccountName(), row.getChannelCode(), row.getChannelAccountId(),
                row.getEntityNo(), row.getParameters(), row.getCredentials(), row.getVersion(), row.getCreatedBy(),
                row.getUpdatedBy(), row.getCreateTime(), row.getUpdateTime());
            var now = support.now();
            service.updateAccount(row, request.getName(), request.getChannelCode(), request.getChannelAccountId(),
                request.getEntityNo(), verify.mapJson(request.getParameters()), verify.mapJson(request.getCredentials()),
                policy.parseId(request.getActor()), now);
            String objectCode = integration.getIntegrationCode() + "/" + row.getAccountCode();
            var changes = CreateVerifyAccountProcessor.changes(before, row, codec);
            audit.record(() -> support.changed(request.getActor(), actor.member().tenantId(),
                CreateVerifyAccountProcessor.OBJECT_TYPE, objectCode, before.getVersion(), row.getVersion(), changes,
                request.getReason(), now));
            return new ExternalApiMutation(CreateVerifyAccountProcessor.OBJECT_TYPE, objectCode, row.getVersion());
        });
    }
}
