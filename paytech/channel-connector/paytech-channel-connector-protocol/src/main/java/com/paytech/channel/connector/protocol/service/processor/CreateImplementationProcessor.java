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
import com.paytech.channel.connector.protocol.core.dal.dataobject.Integration;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationImplementation;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiMutation;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationImplementationRepository;
import com.paytech.channel.connector.protocol.domainservice.service.IntegrationImplementationManagementService;
import com.paytech.channel.connector.protocol.service.processor.request.CreateImplementationRequest;

import lombok.AllArgsConstructor;

/**
 * E25 创建渠道标准 API 实现：全模块配置锁内依次锁定接入方与标准 API，在一个本地事务中完成流程静态校验与保存。
 *
 * <p>同一接入方对同一标准 API 只有一份实现，唯一索引是最终保护。配置锁失效时，接入方行锁与原子 API 修改串行、标准 API 行锁与标准 API 修改及删除串行；加锁顺序固定为接入方在前、标准 API 在后。
 */
@Component
@AllArgsConstructor
public class CreateImplementationProcessor
    extends AbstractLockProcessor<CreateImplementationRequest, ExternalApiMutation> {

    /** 写结果中的对象类型。 */
    static final String OBJECT_TYPE = "INTEGRATION_IMPLEMENTATION";

    private final ChannelConfigChangeService changeService;
    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final ImplementationSupport flows;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final ExternalApiConfigurationProperties properties;
    private final IntegrationImplementationRepository implementations;
    private final IntegrationImplementationManagementService service;

    @Override
    protected LockCommand lockCommand(CreateImplementationRequest request) {
        return LockCommand.tryLock(ExternalApiLocks.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(CreateImplementationRequest request) {
        return transactions.write(() -> {
            Integration integration = children.lockIntegration(request.getIntegrationCode());
            StandardApi standardApi = flows.referencedStandardApi(request.getStandardApiCode());
            if (implementations.byKey(integration.getIntegrationId(), standardApi.getStandardApiId()) != null) {
                throw new BizCheckFailException("该接入方已实现此标准 API：" + standardApi.getStandardApiCode());
            }
            var result = flows.check(standardApi, integration.getIntegrationId(), request.getFlowDefinition());
            support.requireValid(result.getIssues());
            String json = result.getSummary().getNormalizedJson();
            var row = buildRow(integration, standardApi, json, request.getActorId());
            service.create(row);
            String objectCode = integration.getIntegrationCode() + "/" + standardApi.getStandardApiCode();
            var changes = List.of(
                new ConfigurationFieldChange(ConfigurationField.CODE, null, standardApi.getStandardApiCode()),
                new ConfigurationFieldChange(ConfigurationField.DEFINITION_DIGEST, null, codec.sha256(json)));
            changeService.record(support.changed(ChangeTargetType.INTEGRATION_IMPLEMENTATION,
                row.getIntegrationStandardApiId(), ChangeType.CREATE, request.getActorId(), objectCode, 0, 1, changes,
                request.getReason(), row.getCreateTime()));
            return new ExternalApiMutation(OBJECT_TYPE, objectCode, 1);
        });
    }

    /** 新实现的标识、初始版本 1 与规范化流程。 */
    IntegrationImplementation buildRow(Integration integration, StandardApi standardApi, String json, long actorId) {
        var now = support.now();
        return new IntegrationImplementation(support.nextId(), integration.getIntegrationId(),
            standardApi.getStandardApiId(), json, 1, actorId, actorId, now, now);
    }
}
