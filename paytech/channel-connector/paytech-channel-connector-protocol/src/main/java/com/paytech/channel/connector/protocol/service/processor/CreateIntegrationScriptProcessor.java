package com.paytech.channel.connector.protocol.service.processor;

import java.time.Instant;
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
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelScriptRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelScriptManagementService;
import com.paytech.channel.connector.protocol.domainservice.validator.IntegrationScriptValidator;
import com.paytech.channel.connector.protocol.service.processor.request.CreateIntegrationScriptRequest;

import lombok.AllArgsConstructor;

/**
 * E19 创建公共脚本：全模块配置锁内锁定渠道，在一个本地事务中完成参数与源码静态校验、数量与编码检查和保存。
 *
 * <p>每个渠道最多 100 个脚本（物理设计 P7）；渠道内编码唯一索引是最终保护。
 */
@Component
@AllArgsConstructor
public class CreateIntegrationScriptProcessor
    extends AbstractLockProcessor<CreateIntegrationScriptRequest, ExternalApiMutation> {

    /** 每个渠道的脚本上限。 */
    static final long MAX_SCRIPTS = 100;

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
    protected LockCommand lockCommand(CreateIntegrationScriptRequest request) {
        return LockCommand.tryLock(ExternalApiLocks.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(CreateIntegrationScriptRequest request) {
        return transactions.write(() -> {
            Channel integration = children.lockChannel(request.getIntegrationCode());
            support.requireValid(validator.validate(request.getParameters(), request.getSourceCode()));
            requireCreatable(integration, request.getCode());
            ChannelScript row = buildScript(integration, request, request.getActorId());
            service.create(row);
            String objectCode = integration.getChannelCode() + "/" + request.getCode();
            changeService.record(support.changed(ChangeTargetType.CHANNEL_SCRIPT, row.getChannelScriptId(),
                ChangeType.CREATE, request.getActorId(), objectCode, 0, 1, changes(row), request.getReason(),
                row.getCreateTime()));
            return new ExternalApiMutation("INTEGRATION_SCRIPT", objectCode, 1);
        });
    }

    /** 数量上限与渠道内编码唯一。 */
    void requireCreatable(Channel integration, String code) {
        long id = integration.getChannelId();
        if (channelScripts.countByChannels(List.of(id)).getOrDefault(id, 0L) >= MAX_SCRIPTS) {
            throw new BizCheckFailException("每个渠道最多 " + MAX_SCRIPTS + " 个公共脚本");
        }
        if (channelScripts.byCode(id, code) != null) {
            throw new BizCheckFailException("公共脚本编码已经存在：" + code);
        }
    }

    /** 插入前准备标识、初始版本 1 与规范化参数声明。 */
    ChannelScript buildScript(Channel integration, CreateIntegrationScriptRequest request, long actorId) {
        Instant now = support.now();
        return new ChannelScript(support.nextId(), integration.getChannelId(), request.getCode(),
            request.getName(), request.getStage(), codec.parametersJson(request.getParameters()),
            request.getSourceCode(), request.getDescription(), 1, actorId, actorId, now, now);
    }

    /** 新建时各字段前值为空，参数与源码只记录摘要。 */
    List<ConfigurationFieldChange> changes(ChannelScript row) {
        return List.of(new ConfigurationFieldChange(ConfigurationField.CODE, null, row.getScriptCode()),
            new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, null, row.getScriptName()),
            new ConfigurationFieldChange(ConfigurationField.DESCRIPTION, null, row.getDescription()),
            new ConfigurationFieldChange(ConfigurationField.EXECUTION_STAGE, null, row.getExecutionStage().getCode()),
            new ConfigurationFieldChange(ConfigurationField.PARAMETERS_DIGEST, null,
                codec.sha256(row.getParameterDefinition())),
            new ConfigurationFieldChange(ConfigurationField.SOURCE_DIGEST, null, codec.sha256(row.getSourceCode())));
    }
}
