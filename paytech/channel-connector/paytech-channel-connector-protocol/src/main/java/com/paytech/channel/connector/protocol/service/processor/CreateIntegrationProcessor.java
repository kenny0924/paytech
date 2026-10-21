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
import com.paytech.channel.connector.protocol.core.dal.dataobject.Integration;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiMutation;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationRepository;
import com.paytech.channel.connector.protocol.domainservice.service.IntegrationManagementService;
import com.paytech.channel.connector.protocol.domainservice.validator.IntegrationEndpointValidator;
import com.paytech.channel.connector.protocol.service.processor.request.CreateIntegrationRequest;

import lombok.AllArgsConstructor;

/** E08 创建接入方：全模块配置锁内，在一个本地事务中完成地址静态校验、编码查重与保存，并同事务写入变更记录。 */
@Component
@AllArgsConstructor
public class CreateIntegrationProcessor extends AbstractLockProcessor<CreateIntegrationRequest, ExternalApiMutation> {

    private final ChannelConfigChangeService changeService;
    private final ExternalApiConfigurationCodec codec;
    private final IntegrationRepository integrations;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final IntegrationEndpointValidator validator;
    private final IntegrationManagementService service;
    private final ExternalApiConfigurationProperties properties;

    @Override
    protected LockCommand lockCommand(CreateIntegrationRequest request) {
        return LockCommand.tryLock(ExternalApiLocks.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(CreateIntegrationRequest request) {
        return transactions.write(() -> {
            support.requireValid(validator.validate(request.getEndpoints()));
            if (integrations.byCode(request.getCode()) != null) {
                throw new BizCheckFailException("接入方编码已经存在：" + request.getCode());
            }
            Integration row = buildIntegration(request, request.getActorId());
            service.create(row);
            changeService.record(support.changed(ChangeTargetType.INTEGRATION, row.getIntegrationId(), ChangeType.CREATE,
                request.getActorId(), request.getCode(), 0, 1, changes(row), request.getReason(), row.getCreateTime()));
            return new ExternalApiMutation("INTEGRATION", request.getCode(), 1);
        });
    }

    /** 插入前准备标识、初始版本 1 与规范化地址 JSON。 */
    Integration buildIntegration(CreateIntegrationRequest request, long actorId) {
        Instant now = support.now();
        return new Integration(support.nextId(), request.getCode(), request.getName(), request.getDescription(),
            codec.endpointsJson(request.getEndpoints()), 1, actorId, actorId, now, now);
    }

    /** 新建时各字段的前值为空，地址只记录摘要。 */
    List<ConfigurationFieldChange> changes(Integration row) {
        return List.of(new ConfigurationFieldChange(ConfigurationField.CODE, null, row.getIntegrationCode()),
            new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, null, row.getIntegrationName()),
            new ConfigurationFieldChange(ConfigurationField.DESCRIPTION, null, row.getDescription()),
            new ConfigurationFieldChange(ConfigurationField.ENDPOINTS_DIGEST, null, codec.sha256(row.getEndpoints())));
    }
}
