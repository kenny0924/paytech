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

/**
 * V03 创建验证账号（沙箱验证技术方案第 3.1、5 节）：全模块配置锁内锁定接入方，在一个本地事务中完成权限复核、
 * 参数与凭据校验、编码检查与保存。凭据是用户决定的非敏感沙箱材料，审计只记录摘要。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateVerifyAccountProcessor extends AbstractLockProcessor<SaveVerifyAccountRequest, ExternalApiMutation> {

    /** 每个接入方的验证账号上限。 */
    static final int MAX_ACCOUNTS = 50;

    static final String OBJECT_TYPE = "INTEGRATION_VERIFY_ACCOUNT";

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
            support.requireValid(verify.accountIssues(request.getParameters(), request.getCredentials()));
            long integrationId = integration.getIntegrationId();
            if (accounts.allByIntegration(integrationId).size() >= MAX_ACCOUNTS) {
                throw new BizCheckFailException("每个接入方最多 " + MAX_ACCOUNTS + " 个验证账号");
            }
            if (accounts.byCode(integrationId, request.getCode()) != null) {
                throw new BizCheckFailException("验证账号编码已经存在：" + request.getCode());
            }
            long actorId = policy.parseId(request.getActor());
            var now = support.now();
            var row = new IntegrationVerifyAccount(support.nextId(), integrationId, request.getCode(),
                request.getName(), request.getChannelCode(), request.getChannelAccountId(), request.getEntityNo(),
                verify.mapJson(request.getParameters()), verify.mapJson(request.getCredentials()), 1, actorId,
                actorId, now, now);
            service.createAccount(row);
            String objectCode = integration.getIntegrationCode() + "/" + row.getAccountCode();
            var changes = changes(null, row, codec);
            audit.record(() -> support.changed(request.getActor(), actor.member().tenantId(), OBJECT_TYPE, objectCode,
                0, 1, changes, request.getReason(), now));
            return new ExternalApiMutation(OBJECT_TYPE, objectCode, 1);
        });
    }

    /** 账号变更：编码、名称、渠道，参数含身份的摘要，凭据只记摘要。 */
    static List<ConfigurationFieldChange> changes(IntegrationVerifyAccount before, IntegrationVerifyAccount after,
        ExternalApiConfigurationCodec codec) {
        return List.of(
            new ConfigurationFieldChange(ConfigurationField.CODE, before == null ? null : before.getAccountCode(),
                after == null ? null : after.getAccountCode()),
            new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, before == null ? null : before.getAccountName(),
                after == null ? null : after.getAccountName()),
            new ConfigurationFieldChange(ConfigurationField.CHANNEL, before == null ? null : before.getChannelCode(),
                after == null ? null : after.getChannelCode()),
            new ConfigurationFieldChange(ConfigurationField.PARAMETERS_DIGEST, digest(before, false, codec),
                digest(after, false, codec)),
            new ConfigurationFieldChange(ConfigurationField.DEFINITION_DIGEST, digest(before, true, codec),
                digest(after, true, codec)));
    }

    static String digest(IntegrationVerifyAccount row, boolean credentials, ExternalApiConfigurationCodec codec) {
        if (row == null) {
            return null;
        }
        return credentials ? codec.sha256(row.getCredentials()) : codec.sha256(row.getChannelAccountId() + "\n"
            + row.getEntityNo() + "\n" + row.getParameters());
    }
}
