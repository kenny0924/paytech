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
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelApi;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelScript;
import com.paytech.channel.connector.protocol.domain.ExternalApiMutation;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelScriptRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelScriptManagementService;
import com.paytech.channel.connector.protocol.service.processor.request.DeleteIntegrationChildRequest;

import lombok.AllArgsConstructor;

/**
 * E21 删除公共脚本：全模块配置锁内锁定渠道与原行、核对版本；仍被原子 API 引用时拒绝并列出引用方，否则物理删除。
 *
 * <p>原子 API 保存同样持有该配置锁，锁内扫描到的引用在提交前不会变化。
 */
@Component
@AllArgsConstructor
public class DeleteIntegrationScriptProcessor
    extends AbstractLockProcessor<DeleteIntegrationChildRequest, ExternalApiMutation> {

    private final IntegrationChildSupport children;
    private final ExternalApiTransactions transactions;
    private final ChannelConfigChangeService changeService;
    private final ExternalApiProcessorSupport support;
    private final ChannelScriptRepository channelScripts;
    private final ChannelScriptManagementService service;
    private final ExternalApiConfigurationProperties properties;

    @Override
    protected LockCommand lockCommand(DeleteIntegrationChildRequest request) {
        return LockCommand.tryLock(ExternalApiLocks.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(DeleteIntegrationChildRequest request) {
        return transactions.write(() -> {
            Channel integration = children.lockChannel(request.getIntegrationCode());
            ChannelScript row = support.requireFound(channelScripts.lock(integration.getChannelId(),
                request.getCode()));
            support.requireVersion(row.getVersion(), request.getExpectedVersion());
            var users = children.scriptUsers(integration.getChannelId(), row.getScriptCode());
            if (!users.isEmpty()) {
                throw new BizCheckFailException("公共脚本仍被原子 API 引用，不能删除："
                    + String.join("、", users.stream().map(ChannelApi::getApiCode).sorted().toList()));
            }
            int version = row.getVersion();
            service.delete(row);
            String objectCode = integration.getChannelCode() + "/" + row.getScriptCode();
            var changes = List.of(new ConfigurationFieldChange(ConfigurationField.CODE, row.getScriptCode(), null),
                new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, row.getScriptName(), null));
            changeService.record(support.changed(ChangeTargetType.CHANNEL_SCRIPT, row.getChannelScriptId(), ChangeType.DELETE,
                request.getActorId(), objectCode, version, version, changes, request.getReason(), support.now()));
            return new ExternalApiMutation("INTEGRATION_SCRIPT", objectCode, version);
        });
    }
}
