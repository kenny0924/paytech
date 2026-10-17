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
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationScript;
import com.paytech.platform.support.integration.domain.ExternalApiConfigurationCodec;
import com.paytech.platform.support.integration.domain.ExternalApiMutation;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domain.UpdateIntegrationScriptContext;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationScriptRepository;
import com.paytech.platform.support.integration.domainservice.service.IntegrationScriptManagementService;
import com.paytech.platform.support.integration.domainservice.validator.IntegrationScriptValidator;
import com.paytech.platform.support.integration.service.processor.request.UpdateIntegrationScriptRequest;

import lombok.AllArgsConstructor;

/**
 * E20 修改公共脚本：全模块配置锁内锁定接入方与原行、核对版本、校验参数与源码，并用新签名重校验所有引用它的原子 API。
 *
 * <p>修改阶段或参数会让引用方失效时拒绝并列出引用方；编码不可修改。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateIntegrationScriptProcessor
    extends AbstractLockProcessor<UpdateIntegrationScriptRequest, ExternalApiMutation> {

    private final OperationsAuditService audit;
    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final IntegrationScriptValidator validator;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final IntegrationScriptRepository integrationScripts;
    private final OperationsManagementPolicyService policy;
    private final IntegrationScriptManagementService service;
    private final ExternalApiConfigurationProperties properties;

    @Override
    protected LockCommand lockCommand(UpdateIntegrationScriptRequest request) {
        return LockCommand.tryLock(ExternalApiPermissions.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(UpdateIntegrationScriptRequest request) {
        return transactions.write(() -> {
            var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(), null,
                null, false, ExternalApiPermissions.MANAGE);
            Integration integration = children.lockIntegration(request.getIntegrationCode());
            IntegrationScript row = support.requireFound(integrationScripts.lock(integration.getIntegrationId(),
                request.getCode()));
            support.requireVersion(row.getVersion(), request.getExpectedVersion());
            support.requireValid(validator.validate(request.getParameters(), request.getSourceCode()));
            requireUsersStillValid(integration, request);
            var context = new UpdateIntegrationScriptContext(row, request.getName(), request.getStage(),
                codec.parametersJson(request.getParameters()), request.getSourceCode(), request.getDescription(),
                policy.parseId(request.getActor()), support.now());
            var changes = changes(row, context);
            int beforeVersion = row.getVersion();
            service.update(context);
            String objectCode = integration.getIntegrationCode() + "/" + row.getScriptCode();
            audit.record(() -> support.changed(request.getActor(), actor.member().tenantId(), "INTEGRATION_SCRIPT",
                objectCode, beforeVersion, row.getVersion(), changes, request.getReason(), context.getUpdateTime()));
            return new ExternalApiMutation("INTEGRATION_SCRIPT", objectCode, row.getVersion());
        });
    }

    /** 修改阶段或参数会让引用方原子 API 失效时拒绝并列出编码。 */
    void requireUsersStillValid(Integration integration, UpdateIntegrationScriptRequest request) {
        var broken = children.brokenScriptUsers(integration, request.getCode(), request.getStage(),
            request.getParameters());
        if (!broken.isEmpty()) {
            throw new BizCheckFailException("修改后以下原子 API 的脚本引用不再有效：" + String.join("、", broken));
        }
    }

    /** 在更新前冻结名称、说明、阶段、参数与源码摘要的前后值。 */
    List<ConfigurationFieldChange> changes(IntegrationScript row, UpdateIntegrationScriptContext context) {
        return List.of(new ConfigurationFieldChange(ConfigurationField.CODE, row.getScriptCode(), row.getScriptCode()),
            new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, row.getScriptName(), context.getName()),
            new ConfigurationFieldChange(ConfigurationField.DESCRIPTION, row.getDescription(), context.getDescription()),
            new ConfigurationFieldChange(ConfigurationField.EXECUTION_STAGE, row.getExecutionStage().getCode(),
                context.getStage().getCode()),
            new ConfigurationFieldChange(ConfigurationField.PARAMETERS_DIGEST, codec.sha256(row.getParameterDefinition()),
                codec.sha256(context.getParametersJson())),
            new ConfigurationFieldChange(ConfigurationField.SOURCE_DIGEST, codec.sha256(row.getSourceCode()),
                codec.sha256(context.getSourceCode())));
    }
}
