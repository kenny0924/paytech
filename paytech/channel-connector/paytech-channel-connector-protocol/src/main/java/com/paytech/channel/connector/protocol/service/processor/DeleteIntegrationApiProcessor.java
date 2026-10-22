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
import com.paytech.channel.connector.protocol.domain.ApiReference;
import com.paytech.channel.connector.protocol.domain.ExternalApiMutation;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelApiRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelApiManagementService;
import com.paytech.channel.connector.protocol.service.processor.request.DeleteIntegrationChildRequest;

import lombok.AllArgsConstructor;

/**
 * E15 删除原子 API：全模块配置锁内锁定渠道与原行、核对版本；仍被流程节点引用时拒绝并列出实现，否则物理删除主表与字段。
 *
 * <p>流程保存同样持有该配置锁，锁内扫描到的引用在提交前不会变化。
 */
@Component
@AllArgsConstructor
public class DeleteIntegrationApiProcessor
    extends AbstractLockProcessor<DeleteIntegrationChildRequest, ExternalApiMutation> {

    private final IntegrationChildSupport children;
    private final ExternalApiTransactions transactions;
    private final ChannelConfigChangeService changeService;
    private final ExternalApiProcessorSupport support;
    private final ChannelApiRepository channelApis;
    private final ChannelApiManagementService service;
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
            ChannelApi row = support.requireFound(channelApis.lock(integration.getChannelId(),
                request.getCode()));
            support.requireVersion(row.getVersion(), request.getExpectedVersion());
            var references = children.apiReferences(integration.getChannelId(), row.getApiCode());
            if (!references.isEmpty()) {
                throw new BizCheckFailException("原子 API 仍被标准 API 实现引用，不能删除："
                    + String.join("、", references.stream().map(ApiReference::getStandardApiCode).toList()));
            }
            int version = row.getVersion();
            service.delete(row);
            String objectCode = integration.getChannelCode() + "/" + row.getApiCode();
            var changes = List.of(new ConfigurationFieldChange(ConfigurationField.CODE, row.getApiCode(), null),
                new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, row.getApiName(), null));
            changeService.record(support.changed(ChangeTargetType.CHANNEL_API, row.getChannelApiId(), ChangeType.DELETE,
                request.getActorId(), objectCode, version, version, changes, request.getReason(), support.now()));
            return new ExternalApiMutation("INTEGRATION_API", objectCode, version);
        });
    }
}
