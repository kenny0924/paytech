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
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationScript;
import com.paytech.platform.support.integration.domain.ExternalApiConfigurationCodec;
import com.paytech.platform.support.integration.domain.ExternalApiMutation;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationScriptRepository;
import com.paytech.platform.support.integration.domainservice.service.IntegrationScriptManagementService;
import com.paytech.platform.support.integration.domainservice.validator.IntegrationScriptValidator;
import com.paytech.platform.support.integration.service.processor.request.CreateIntegrationScriptRequest;

import lombok.AllArgsConstructor;

/**
 * E19 创建公共脚本：全模块配置锁内锁定接入方，在一个本地事务中完成权限复核、参数与源码静态校验、数量与编码检查和保存。
 *
 * <p>每个接入方最多 100 个脚本（物理设计 P7）；接入方内编码唯一索引是最终保护。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateIntegrationScriptProcessor
    extends AbstractLockProcessor<CreateIntegrationScriptRequest, ExternalApiMutation> {

    /** 每个接入方的脚本上限。 */
    static final long MAX_SCRIPTS = 100;

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
    protected LockCommand lockCommand(CreateIntegrationScriptRequest request) {
        return LockCommand.tryLock(ExternalApiPermissions.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(CreateIntegrationScriptRequest request) {
        return transactions.write(() -> {
            var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(), null,
                null, false, ExternalApiPermissions.MANAGE);
            Integration integration = children.lockIntegration(request.getIntegrationCode());
            support.requireValid(validator.validate(request.getParameters(), request.getSourceCode()));
            requireCreatable(integration, request.getCode());
            IntegrationScript row = buildScript(integration, request, policy.parseId(request.getActor()));
            service.create(row);
            String objectCode = integration.getIntegrationCode() + "/" + request.getCode();
            audit.record(() -> support.changed(request.getActor(), actor.member().tenantId(), "INTEGRATION_SCRIPT",
                objectCode, 0, 1, changes(row), request.getReason(), row.getCreateTime()));
            return new ExternalApiMutation("INTEGRATION_SCRIPT", objectCode, 1);
        });
    }

    /** 数量上限与接入方内编码唯一。 */
    void requireCreatable(Integration integration, String code) {
        long id = integration.getIntegrationId();
        if (integrationScripts.countByIntegrations(List.of(id)).getOrDefault(id, 0L) >= MAX_SCRIPTS) {
            throw new BizCheckFailException("每个接入方最多 " + MAX_SCRIPTS + " 个公共脚本");
        }
        if (integrationScripts.byCode(id, code) != null) {
            throw new BizCheckFailException("公共脚本编码已经存在：" + code);
        }
    }

    /** 插入前准备标识、初始版本 1 与规范化参数声明。 */
    IntegrationScript buildScript(Integration integration, CreateIntegrationScriptRequest request, long actorId) {
        Instant now = support.now();
        return new IntegrationScript(support.nextId(), integration.getIntegrationId(), request.getCode(),
            request.getName(), request.getStage(), codec.parametersJson(request.getParameters()),
            request.getSourceCode(), request.getDescription(), 1, actorId, actorId, now, now);
    }

    /** 新建时各字段前值为空，参数与源码只记录摘要。 */
    List<ConfigurationFieldChange> changes(IntegrationScript row) {
        return List.of(new ConfigurationFieldChange(ConfigurationField.CODE, null, row.getScriptCode()),
            new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, null, row.getScriptName()),
            new ConfigurationFieldChange(ConfigurationField.DESCRIPTION, null, row.getDescription()),
            new ConfigurationFieldChange(ConfigurationField.EXECUTION_STAGE, null, row.getExecutionStage().getCode()),
            new ConfigurationFieldChange(ConfigurationField.PARAMETERS_DIGEST, null,
                codec.sha256(row.getParameterDefinition())),
            new ConfigurationFieldChange(ConfigurationField.SOURCE_DIGEST, null, codec.sha256(row.getSourceCode())));
    }
}
