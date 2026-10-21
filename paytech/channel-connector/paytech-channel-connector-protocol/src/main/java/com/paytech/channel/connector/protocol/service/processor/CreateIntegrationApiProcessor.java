package com.paytech.channel.connector.protocol.service.processor;

import java.time.Instant;
import java.util.ArrayList;
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
import com.paytech.channel.connector.protocol.core.dal.dataobject.ApiField;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Integration;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationApi;
import com.paytech.channel.connector.protocol.core.enums.ApiFieldDirection;
import com.paytech.channel.connector.protocol.domain.CreateIntegrationApiContext;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiMutation;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.IntegrationApiDefinition;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationApiRepository;
import com.paytech.channel.connector.protocol.domainservice.service.IntegrationApiManagementService;
import com.paytech.channel.connector.protocol.service.processor.request.CreateIntegrationApiRequest;

import lombok.AllArgsConstructor;

/**
 * E13 创建原子 API：全模块配置锁内锁定接入方，在一个本地事务中完成字段与定义静态校验、数量与编码检查和保存。
 *
 * <p>每个接入方最多 200 个原子 API（物理设计 P7）；接入方内编码唯一索引是最终保护。
 */
@Component
@AllArgsConstructor
public class CreateIntegrationApiProcessor
    extends AbstractLockProcessor<CreateIntegrationApiRequest, ExternalApiMutation> {

    /** 每个接入方的原子 API 上限。 */
    static final long MAX_APIS = 200;

    private final ChannelConfigChangeService changeService;
    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final IntegrationApiRepository integrationApis;
    private final IntegrationApiManagementService service;
    private final ExternalApiConfigurationProperties properties;

    @Override
    protected LockCommand lockCommand(CreateIntegrationApiRequest request) {
        return LockCommand.tryLock(ExternalApiLocks.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(CreateIntegrationApiRequest request) {
        return transactions.write(() -> {
            Integration integration = children.lockIntegration(request.getIntegrationCode());
            var result = children.check(integration, request.getRequestFields(), request.getResponseFields(),
                request.getDefinition(), children.scriptSignatures(integration.getIntegrationId()));
            support.requireValid(result.getIssues());
            requireCreatable(integration, request.getCode());
            var context = buildContext(integration, request, result.getDefinition(), request.getActorId());
            service.create(context);
            String objectCode = integration.getIntegrationCode() + "/" + request.getCode();
            changeService.record(support.changed(ChangeTargetType.INTEGRATION_API,
                context.getIntegrationApi().getIntegrationApiId(), ChangeType.CREATE, request.getActorId(), objectCode, 0, 1,
                changes(request, result.getDefinition()), request.getReason(), context.getIntegrationApi().getCreateTime()));
            return new ExternalApiMutation("INTEGRATION_API", objectCode, 1);
        });
    }

    /** 数量上限与接入方内编码唯一。 */
    void requireCreatable(Integration integration, String code) {
        long id = integration.getIntegrationId();
        if (integrationApis.countByIntegrations(List.of(id)).getOrDefault(id, 0L) >= MAX_APIS) {
            throw new BizCheckFailException("每个接入方最多 " + MAX_APIS + " 个原子 API");
        }
        if (integrationApis.byCode(id, code) != null) {
            throw new BizCheckFailException("原子 API 编码已经存在：" + code);
        }
    }

    /** 插入前准备标识、初始版本 1、规范化定义与两个方向的字段行。 */
    CreateIntegrationApiContext buildContext(Integration integration, CreateIntegrationApiRequest request,
        IntegrationApiDefinition definition, long actorId) {
        Instant now = support.now();
        long id = support.nextId();
        var row = new IntegrationApi(id, integration.getIntegrationId(), request.getCode(), request.getName(),
            request.getDescription(), definition.getNormalizedJson(), 1, actorId, actorId, now, now);
        var rows = new ArrayList<ApiField>(support.apiFields(id, ApiFieldDirection.INTEGRATION_API_REQUEST,
            request.getRequestFields(), now));
        rows.addAll(support.apiFields(id, ApiFieldDirection.INTEGRATION_API_RESPONSE, request.getResponseFields(), now));
        return new CreateIntegrationApiContext(row, rows);
    }

    /** 新建时各字段前值为空，字段与定义只记录摘要。 */
    List<ConfigurationFieldChange> changes(CreateIntegrationApiRequest request, IntegrationApiDefinition definition) {
        return List.of(new ConfigurationFieldChange(ConfigurationField.CODE, null, request.getCode()),
            new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, null, request.getName()),
            new ConfigurationFieldChange(ConfigurationField.DESCRIPTION, null, request.getDescription()),
            new ConfigurationFieldChange(ConfigurationField.REQUEST_FIELDS_DIGEST, null,
                codec.fieldsDigest(request.getRequestFields())),
            new ConfigurationFieldChange(ConfigurationField.RESPONSE_FIELDS_DIGEST, null,
                codec.fieldsDigest(request.getResponseFields())),
            new ConfigurationFieldChange(ConfigurationField.DEFINITION_DIGEST, null,
                codec.sha256(definition.getNormalizedJson())));
    }
}
