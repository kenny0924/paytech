package com.paytech.channel.connector.protocol.service.processor;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationApiEndpoint;
import com.paytech.channel.connector.protocol.domain.EndpointDefinition;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiMutation;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.UpdateIntegrationContext;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationApiRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationRepository;
import com.paytech.channel.connector.protocol.domainservice.service.IntegrationManagementService;
import com.paytech.channel.connector.protocol.domainservice.validator.IntegrationEndpointValidator;
import com.paytech.channel.connector.protocol.service.processor.request.UpdateIntegrationRequest;

import lombok.AllArgsConstructor;

/**
 * E09 修改接入方：全模块配置锁内锁定原行、核对版本、校验地址；仍被原子 API 引用的地址代码不能移除。
 *
 * <p>原子 API 的写入同样持有该配置锁，锁内读取的地址引用在提交前不会变化。
 */
@Component
@AllArgsConstructor
public class UpdateIntegrationProcessor extends AbstractLockProcessor<UpdateIntegrationRequest, ExternalApiMutation> {

    private final ChannelConfigChangeService changeService;
    private final ExternalApiConfigurationCodec codec;
    private final IntegrationRepository integrations;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final IntegrationEndpointValidator validator;
    private final IntegrationApiRepository integrationApis;
    private final IntegrationManagementService service;
    private final ExternalApiConfigurationProperties properties;

    @Override
    protected LockCommand lockCommand(UpdateIntegrationRequest request) {
        return LockCommand.tryLock(ExternalApiLocks.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(UpdateIntegrationRequest request) {
        return transactions.write(() -> {
            Integration row = support.requireFound(integrations.lock(request.getCode()));
            support.requireVersion(row.getVersion(), request.getExpectedVersion());
            support.requireValid(validator.validate(request.getEndpoints()));
            requireReferencedEndpointsKept(row, request.getEndpoints());
            var context = new UpdateIntegrationContext(row, request.getName(), request.getDescription(),
                codec.endpointsJson(request.getEndpoints()), request.getActorId(), support.now());
            var changes = changes(row, context);
            int beforeVersion = row.getVersion();
            service.update(context);
            changeService.record(support.changed(ChangeTargetType.INTEGRATION, row.getIntegrationId(), ChangeType.UPDATE,
                request.getActorId(), row.getIntegrationCode(), beforeVersion, row.getVersion(), changes,
                request.getReason(), context.getUpdateTime()));
            return new ExternalApiMutation("INTEGRATION", row.getIntegrationCode(), row.getVersion());
        });
    }

    /** 新地址列表缺少仍被原子 API 引用的代码时拒绝，提示中列出引用方。 */
    void requireReferencedEndpointsKept(Integration row, List<EndpointDefinition> endpoints) {
        Set<String> kept = endpoints.stream().map(EndpointDefinition::getEndpointCode).collect(Collectors.toSet());
        var broken = integrationApis.endpointReferences(row.getIntegrationId()).stream()
            .filter(reference -> reference.getEndpointCode() != null && !kept.contains(reference.getEndpointCode()))
            .map(IntegrationApiEndpoint::getApiCode).toList();
        if (!broken.isEmpty()) {
            throw new BizCheckFailException("出站地址仍被原子 API 引用，不能移除：" + String.join("、", broken));
        }
    }

    /** 在更新前冻结名称、说明与地址摘要的前后值。 */
    List<ConfigurationFieldChange> changes(Integration row, UpdateIntegrationContext context) {
        return List.of(
            new ConfigurationFieldChange(ConfigurationField.CODE, row.getIntegrationCode(), row.getIntegrationCode()),
            new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, row.getIntegrationName(), context.getName()),
            new ConfigurationFieldChange(ConfigurationField.DESCRIPTION, row.getDescription(), context.getDescription()),
            new ConfigurationFieldChange(ConfigurationField.ENDPOINTS_DIGEST, codec.sha256(row.getEndpoints()),
                codec.sha256(context.getEndpointsJson())));
    }
}
