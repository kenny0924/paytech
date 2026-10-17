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
import com.paytech.platform.support.integration.service.processor.request.DeleteIntegrationChildRequest;

/** V05 按当前版本删除验证账号：仍被验证用例引用时拒绝。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class DeleteVerifyAccountProcessor extends AbstractLockProcessor<DeleteIntegrationChildRequest, ExternalApiMutation> {

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
            IntegrationVerifyAccount row = support.requireFound(accounts.lock(integration.getIntegrationId(),
                request.getCode()));
            support.requireVersion(row.getVersion(), request.getExpectedVersion());
            long users = cases.countByAccount(row.getVerifyAccountId());
            if (users > 0) {
                throw new BizCheckFailException("验证账号仍被 " + users + " 个验证用例引用，不能删除：" + row.getAccountCode());
            }
            int version = row.getVersion();
            service.deleteAccount(row);
            String objectCode = integration.getIntegrationCode() + "/" + row.getAccountCode();
            var changes = CreateVerifyAccountProcessor.changes(row, null, codec);
            audit.record(() -> support.changed(request.getActor(), actor.member().tenantId(),
                CreateVerifyAccountProcessor.OBJECT_TYPE, objectCode, version, version, changes, request.getReason(),
                support.now()));
            return new ExternalApiMutation(CreateVerifyAccountProcessor.OBJECT_TYPE, objectCode, version);
        });
    }
}
