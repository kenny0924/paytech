package com.paytech.channel.connector.protocol.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelConfigChangeService;
import com.paytech.channel.connector.protocol.core.enums.ConfigurationField;
import com.paytech.channel.connector.protocol.domain.ConfigurationFieldChange;
import com.paytech.channel.connector.protocol.core.configuration.ExternalApiConfigurationProperties;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelScript;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiMutation;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.UpdateIntegrationScriptContext;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelScriptRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelScriptManagementService;
import com.paytech.channel.connector.protocol.domainservice.validator.IntegrationScriptValidator;
import com.paytech.channel.connector.protocol.service.processor.request.UpdateIntegrationScriptRequest;

import lombok.AllArgsConstructor;

/**
 * E20 修改公共脚本：全模块配置锁内锁定渠道与原行、核对版本、校验参数与源码，并用新签名重校验所有引用它的原子 API。
 *
 * <p>修改阶段或参数会让引用方失效时拒绝并列出引用方；编码不可修改。
 */
@Component
@AllArgsConstructor
public class UpdateIntegrationScriptProcessor
    extends AbstractLockProcessor<UpdateIntegrationScriptRequest, ExternalApiMutation> {

    private final ChannelConfigChangeService changeService;
    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final IntegrationScriptValidator validator;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final ChannelScriptRepository channelScripts;
    private final ChannelScriptManagementService service;
    private final ExternalApiConfigurationProperties properties;

    @Override
    protected LockCommand lockCommand(UpdateIntegrationScriptRequest request) {
        return LockCommand.tryLock(ExternalApiLocks.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(UpdateIntegrationScriptRequest request) {
        return transactions.write(() -> {
            Channel integration = children.lockChannel(request.getIntegrationCode());
            ChannelScript row = support.requireFound(channelScripts.lock(integration.getChannelId(),
                request.getCode()));
            support.requireVersion(row.getVersion(), request.getExpectedVersion());
            support.requireValid(validator.validate(request.getParameters(), request.getSourceCode()));
            requireUsersStillValid(integration, request);
            var context = new UpdateIntegrationScriptContext(row, request.getName(), request.getStage(),
                codec.parametersJson(request.getParameters()), request.getSourceCode(), request.getDescription(),
                request.getActorId(), support.now());
            var changes = changes(row, context);
            int beforeVersion = row.getVersion();
            service.update(context);
            String objectCode = integration.getChannelCode() + "/" + row.getScriptCode();
            changeService.record(support.changed(ChangeTargetType.CHANNEL_SCRIPT, row.getChannelScriptId(), ChangeType.UPDATE,
                request.getActorId(), objectCode, beforeVersion, row.getVersion(), changes,
                request.getReason(), context.getUpdateTime()));
            return new ExternalApiMutation("INTEGRATION_SCRIPT", objectCode, row.getVersion());
        });
    }

    /** 修改阶段或参数会让引用方原子 API 失效时拒绝并列出编码。 */
    void requireUsersStillValid(Channel integration, UpdateIntegrationScriptRequest request) {
        var broken = children.brokenScriptUsers(integration, request.getCode(), request.getStage(),
            request.getParameters());
        if (!broken.isEmpty()) {
            throw new BizCheckFailException("修改后以下原子 API 的脚本引用不再有效：" + String.join("、", broken));
        }
    }

    /** 在更新前冻结名称、说明、阶段、参数与源码摘要的前后值。 */
    List<ConfigurationFieldChange> changes(ChannelScript row, UpdateIntegrationScriptContext context) {
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
