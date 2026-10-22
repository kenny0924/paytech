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
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.core.enums.ApiFieldDirection;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiMutation;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.FieldDefinition;
import com.paytech.channel.connector.protocol.domain.UpdateStandardApiContext;
import com.paytech.channel.connector.protocol.domain.ValidationIssue;
import com.paytech.channel.connector.protocol.domainservice.repository.ApiFieldRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.StandardApiRepository;
import com.paytech.channel.connector.protocol.domainservice.service.StandardApiManagementService;
import com.paytech.channel.connector.protocol.domainservice.validator.ApiFieldValidator;
import com.paytech.channel.connector.protocol.service.processor.request.UpdateStandardApiRequest;

import lombok.AllArgsConstructor;

/**
 * E04 修改标准 API：全模块配置锁内，锁定原行核对版本，校验后按原版本更新主表并整组替换字段。
 *
 * <p>编码不可修改；字段静态校验与创建一致。字段变更会使任一渠道实现流程失效时拒绝，实现保存同样持有该配置锁。
 */
@Component
@AllArgsConstructor
public class UpdateStandardApiProcessor extends AbstractLockProcessor<UpdateStandardApiRequest, ExternalApiMutation> {

    private final ApiFieldRepository fields;
    private final ImplementationSupport flows;
    private final ApiFieldValidator validator;
    private final ChannelConfigChangeService changeService;
    private final ExternalApiConfigurationCodec codec;
    private final StandardApiRepository standardApis;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final StandardApiManagementService service;
    private final ExternalApiConfigurationProperties properties;

    @Override
    protected LockCommand lockCommand(UpdateStandardApiRequest request) {
        return LockCommand.tryLock(ExternalApiLocks.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(UpdateStandardApiRequest request) {
        return transactions.write(() -> {
            StandardApi row = support.requireFound(standardApis.lock(request.getCode()));
            support.requireVersion(row.getVersion(), request.getExpectedVersion());
            support.requireValid(fieldIssues(request));
            requireImplementationsIntact(row, request);
            var before = fields.byApis(List.of(row.getStandardApiId()),
                List.of(ApiFieldDirection.STANDARD_API_REQUEST, ApiFieldDirection.STANDARD_API_RESPONSE));
            var changes = changes(row, before, request);
            int beforeVersion = row.getVersion();
            UpdateStandardApiContext context = buildContext(row, request, request.getActorId());
            service.update(context);
            changeService.record(support.changed(ChangeTargetType.STANDARD_API, row.getStandardApiId(), ChangeType.UPDATE,
                request.getActorId(), row.getStandardApiCode(), beforeVersion, row.getVersion(), changes,
                request.getReason(), context.getUpdateTime()));
            return new ExternalApiMutation("STANDARD_API", row.getStandardApiCode(), row.getVersion());
        });
    }

    /** 两个方向的字段规则，标准 API 只允许 BODY。 */
    List<ValidationIssue> fieldIssues(UpdateStandardApiRequest request) {
        var issues = new ArrayList<ValidationIssue>(validator.validate(request.getRequestFields(),
            CreateStandardApiProcessor.BODY_ONLY, "requestFields"));
        issues.addAll(validator.validate(request.getResponseFields(), CreateStandardApiProcessor.BODY_ONLY,
            "responseFields"));
        return issues;
    }

    /** 用新字段复核全部渠道实现（输入引用、出口输出与必填覆盖），会使任一实现失效时拒绝。 */
    void requireImplementationsIntact(StandardApi row, UpdateStandardApiRequest request) {
        var broken = flows.brokenByStandardApi(row, request.getRequestFields(), request.getResponseFields());
        if (!broken.isEmpty()) {
            throw new BizCheckFailException("修改会使以下渠道的实现失效，请先调整流程：" + String.join("、", broken));
        }
    }

    /** 新字段行沿用原标准 API 标识，旧行由 Service 整组删除。 */
    UpdateStandardApiContext buildContext(StandardApi row, UpdateStandardApiRequest request, long actorId) {
        Instant now = support.now();
        var rows = new ArrayList<ApiField>(support.apiFields(row.getStandardApiId(),
            ApiFieldDirection.STANDARD_API_REQUEST, request.getRequestFields(), now));
        rows.addAll(support.apiFields(row.getStandardApiId(), ApiFieldDirection.STANDARD_API_RESPONSE,
            request.getResponseFields(), now));
        return new UpdateStandardApiContext(row, request.getName(), request.getDescription(), rows, actorId, now);
    }

    /** 在更新前冻结名称、说明与字段摘要的前后值。 */
    List<ConfigurationFieldChange> changes(StandardApi row, List<ApiField> before, UpdateStandardApiRequest request) {
        List<FieldDefinition> requestBefore = support.definitions(before, ApiFieldDirection.STANDARD_API_REQUEST);
        List<FieldDefinition> responseBefore = support.definitions(before, ApiFieldDirection.STANDARD_API_RESPONSE);
        return List.of(
            new ConfigurationFieldChange(ConfigurationField.CODE, row.getStandardApiCode(), row.getStandardApiCode()),
            new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, row.getStandardApiName(), request.getName()),
            new ConfigurationFieldChange(ConfigurationField.DESCRIPTION, row.getDescription(),
                request.getDescription()),
            new ConfigurationFieldChange(ConfigurationField.REQUEST_FIELDS_DIGEST, codec.fieldsDigest(requestBefore),
                codec.fieldsDigest(request.getRequestFields())),
            new ConfigurationFieldChange(ConfigurationField.RESPONSE_FIELDS_DIGEST, codec.fieldsDigest(responseBefore),
                codec.fieldsDigest(request.getResponseFields())));
    }
}
