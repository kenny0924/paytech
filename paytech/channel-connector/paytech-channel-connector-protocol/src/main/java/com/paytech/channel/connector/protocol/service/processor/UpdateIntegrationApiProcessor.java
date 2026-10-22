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
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelApi;
import com.paytech.channel.connector.protocol.core.enums.ApiFieldDirection;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiMutation;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.IntegrationApiDefinition;
import com.paytech.channel.connector.protocol.domain.UpdateIntegrationApiContext;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelApiRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelApiManagementService;
import com.paytech.channel.connector.protocol.service.processor.request.UpdateIntegrationApiRequest;

import lombok.AllArgsConstructor;

/**
 * E14 修改原子 API：全模块配置锁内锁定渠道与原行、核对版本、整体静态校验，按原版本更新主表并整组替换字段。
 *
 * <p>编码不可修改；修改会使引用本 API 的任一流程失效时拒绝，流程保存同样持有该配置锁。
 */
@Component
@AllArgsConstructor
public class UpdateIntegrationApiProcessor
    extends AbstractLockProcessor<UpdateIntegrationApiRequest, ExternalApiMutation> {

    private final ChannelConfigChangeService changeService;
    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final ImplementationSupport flows;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final ChannelApiRepository channelApis;
    private final ChannelApiManagementService service;
    private final ExternalApiConfigurationProperties properties;

    @Override
    protected LockCommand lockCommand(UpdateIntegrationApiRequest request) {
        return LockCommand.tryLock(ExternalApiLocks.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(UpdateIntegrationApiRequest request) {
        return transactions.write(() -> {
            Channel integration = children.lockChannel(request.getIntegrationCode());
            ChannelApi row = support.requireFound(channelApis.lock(integration.getChannelId(),
                request.getCode()));
            support.requireVersion(row.getVersion(), request.getExpectedVersion());
            var result = children.check(integration, request.getRequestFields(), request.getResponseFields(),
                request.getDefinition(), children.scriptSignatures(integration.getChannelId()));
            support.requireValid(result.getIssues());
            requireFlowsIntact(integration, row, request, result.getDefinition());
            var changes = changes(row, request, result.getDefinition());
            int beforeVersion = row.getVersion();
            var context = buildContext(row, request, result.getDefinition(), request.getActorId());
            service.update(context);
            String objectCode = integration.getChannelCode() + "/" + row.getApiCode();
            changeService.record(support.changed(ChangeTargetType.CHANNEL_API, row.getChannelApiId(), ChangeType.UPDATE,
                request.getActorId(), objectCode, beforeVersion, row.getVersion(), changes,
                request.getReason(), context.getUpdateTime()));
            return new ExternalApiMutation("INTEGRATION_API", objectCode, row.getVersion());
        });
    }

    /** 用修改后的合同复核引用本原子 API 的流程（请求覆盖、固定值冲突、参数与输出引用），会使任一流程失效时拒绝。 */
    void requireFlowsIntact(Channel integration, ChannelApi row, UpdateIntegrationApiRequest request,
        IntegrationApiDefinition definition) {
        var contract = codec.apiContract(row.getApiCode(), request.getName(), request.getRequestFields(),
            definition.getNormalizedJson());
        var broken = flows.brokenByIntegrationApi(integration.getChannelId(), contract);
        if (!broken.isEmpty()) {
            throw new BizCheckFailException("修改会使以下标准 API 实现失效，请先调整流程：" + String.join("、", broken));
        }
    }

    /** 新字段行沿用原 API 标识，旧行由 Service 整组删除。 */
    UpdateIntegrationApiContext buildContext(ChannelApi row, UpdateIntegrationApiRequest request,
        IntegrationApiDefinition definition, long actorId) {
        Instant now = support.now();
        var rows = new ArrayList<ApiField>(support.apiFields(row.getChannelApiId(),
            ApiFieldDirection.INTEGRATION_API_REQUEST, request.getRequestFields(), now));
        rows.addAll(support.apiFields(row.getChannelApiId(), ApiFieldDirection.INTEGRATION_API_RESPONSE,
            request.getResponseFields(), now));
        return new UpdateIntegrationApiContext(row, request.getName(), request.getDescription(),
            definition.getNormalizedJson(), rows, actorId, now);
    }

    /** 在更新前冻结名称、说明、字段与定义摘要的前后值。 */
    List<ConfigurationFieldChange> changes(ChannelApi row, UpdateIntegrationApiRequest request,
        IntegrationApiDefinition definition) {
        var before = children.savedFields(row);
        return List.of(new ConfigurationFieldChange(ConfigurationField.CODE, row.getApiCode(), row.getApiCode()),
            new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, row.getApiName(), request.getName()),
            new ConfigurationFieldChange(ConfigurationField.DESCRIPTION, row.getDescription(), request.getDescription()),
            new ConfigurationFieldChange(ConfigurationField.REQUEST_FIELDS_DIGEST,
                codec.fieldsDigest(before.get(ApiFieldDirection.INTEGRATION_API_REQUEST)),
                codec.fieldsDigest(request.getRequestFields())),
            new ConfigurationFieldChange(ConfigurationField.RESPONSE_FIELDS_DIGEST,
                codec.fieldsDigest(before.get(ApiFieldDirection.INTEGRATION_API_RESPONSE)),
                codec.fieldsDigest(request.getResponseFields())),
            new ConfigurationFieldChange(ConfigurationField.DEFINITION_DIGEST, codec.sha256(row.getDefinitionJson()),
                codec.sha256(definition.getNormalizedJson())));
    }
}
