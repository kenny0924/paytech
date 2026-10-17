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

/**
 * V08 创建验证用例（沙箱验证技术方案第 4、5 节）：全模块配置锁内锁定接入方，在一个本地事务中复核权限、
 * 确认账号存在、静态校验步骤（标准 API 已由本接入方实现、模板变量、期望）并保存。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateVerifyCaseProcessor extends AbstractLockProcessor<SaveVerifyCaseRequest, ExternalApiMutation> {

    /** 每个接入方的验证用例上限。 */
    static final long MAX_CASES = 200;

    static final String OBJECT_TYPE = "INTEGRATION_VERIFY_CASE";

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
            IntegrationVerifyAccount account = checks.account(integrationId, request.getAccountCode());
            support.requireValid(checks.issues(integration.getIntegrationCode(), account, request.getSteps()));
            if (cases.count(integrationId, null) >= MAX_CASES) {
                throw new BizCheckFailException("每个接入方最多 " + MAX_CASES + " 个验证用例");
            }
            if (cases.byCode(integrationId, request.getCode()) != null) {
                throw new BizCheckFailException("验证用例编码已经存在：" + request.getCode());
            }
            long actorId = policy.parseId(request.getActor());
            var now = support.now();
            var row = new IntegrationVerifyCase(support.nextId(), integrationId, request.getCode(), request.getName(),
                request.getDescription(), account.getVerifyAccountId(), verify.text(request.getSteps()), 1, actorId,
                actorId, now, now);
            service.createCase(row);
            String objectCode = integration.getIntegrationCode() + "/" + row.getCaseCode();
            var changes = VerifyCaseChecks.changes(null, null, row, account.getAccountCode(), codec);
            audit.record(() -> support.changed(request.getActor(), actor.member().tenantId(), OBJECT_TYPE, objectCode,
                0, 1, changes, request.getReason(), now));
            return new ExternalApiMutation(OBJECT_TYPE, objectCode, 1);
        });
    }
}
