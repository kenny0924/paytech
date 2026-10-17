package com.paytech.platform.support.integration.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.integration.core.configuration.ExternalApiConfigurationProperties;
import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationVerifyAccount;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationVerifyCase;
import com.paytech.platform.support.integration.domain.ExternalApiConfigurationCodec;
import com.paytech.platform.support.integration.domain.ExternalApiMutation;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationVerifyAccountRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationVerifyCaseRepository;
import com.paytech.platform.support.integration.domainservice.service.IntegrationVerifyConfigService;

import lombok.AllArgsConstructor;
import com.paytech.platform.support.integration.service.processor.request.SaveVerifyCaseRequest;

/** V09 按当前版本整体修改验证用例：配置锁内锁定接入方与用例行，校验后条件更新；已有运行记录保留。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateVerifyCaseProcessor extends AbstractLockProcessor<SaveVerifyCaseRequest, ExternalApiMutation> {

    private final VerifySupport verify;
    private final VerifyCaseChecks checks;
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
    protected LockCommand lockCommand(SaveVerifyCaseRequest request) {
        return LockCommand.tryLock(ExternalApiPermissions.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(SaveVerifyCaseRequest request) {
        return transactions.write(() -> {
            var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(), null,
                null, false, ExternalApiPermissions.MANAGE);
            Integration integration = children.lockIntegration(request.getIntegrationCode());
            long integrationId = integration.getIntegrationId();
            IntegrationVerifyCase row = support.requireFound(cases.lock(integrationId, request.getCode()));
            support.requireVersion(row.getVersion(), request.getExpectedVersion());
            IntegrationVerifyAccount account = checks.account(integrationId, request.getAccountCode());
            support.requireValid(checks.issues(integration.getIntegrationCode(), account, request.getSteps()));
            var before = new IntegrationVerifyCase(row.getVerifyCaseId(), row.getIntegrationId(), row.getCaseCode(),
                row.getCaseName(), row.getDescription(), row.getVerifyAccountId(), row.getStepsJson(), row.getVersion(),
                row.getCreatedBy(), row.getUpdatedBy(), row.getCreateTime(), row.getUpdateTime());
            IntegrationVerifyAccount previous = accounts.byId(before.getVerifyAccountId());
            var now = support.now();
            service.updateCase(row, request.getName(), request.getDescription(), account.getVerifyAccountId(),
                verify.text(request.getSteps()), policy.parseId(request.getActor()), now);
            String objectCode = integration.getIntegrationCode() + "/" + row.getCaseCode();
            var changes = VerifyCaseChecks.changes(before, previous == null ? null : previous.getAccountCode(), row,
                account.getAccountCode(), codec);
            audit.record(() -> support.changed(request.getActor(), actor.member().tenantId(),
                CreateVerifyCaseProcessor.OBJECT_TYPE, objectCode, before.getVersion(), row.getVersion(), changes,
                request.getReason(), now));
            return new ExternalApiMutation(CreateVerifyCaseProcessor.OBJECT_TYPE, objectCode, row.getVersion());
        });
    }
}
