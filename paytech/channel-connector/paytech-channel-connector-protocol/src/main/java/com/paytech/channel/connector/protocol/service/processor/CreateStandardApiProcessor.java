package com.paytech.channel.connector.protocol.service.processor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.channel.connector.api.enums.ApiFieldLocation;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelConfigChangeService;
import com.paytech.channel.connector.protocol.core.enums.ConfigurationField;
import com.paytech.channel.connector.protocol.domain.ConfigurationFieldChange;
import com.paytech.channel.connector.protocol.core.configuration.ExternalApiConfigurationProperties;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ApiField;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.core.enums.ApiFieldDirection;
import com.paytech.channel.connector.protocol.domain.CreateStandardApiContext;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiMutation;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.ValidationIssue;
import com.paytech.channel.connector.protocol.domainservice.repository.StandardApiRepository;
import com.paytech.channel.connector.protocol.domainservice.service.StandardApiManagementService;
import com.paytech.channel.connector.protocol.domainservice.validator.ApiFieldValidator;
import com.paytech.channel.connector.protocol.service.processor.request.CreateStandardApiRequest;

import lombok.AllArgsConstructor;

/**
 * E03 创建标准 API：全模块配置锁内，在一个本地事务中完成字段静态校验、编码查重与保存。
 *
 * <p>标准 API 字段只使用 BODY 位置（逻辑设计 3.1）；编码唯一索引是最终保护。
 */
@Component
@AllArgsConstructor
public class CreateStandardApiProcessor extends AbstractLockProcessor<CreateStandardApiRequest, ExternalApiMutation> {

    /** 标准 API 两个方向都只允许 BODY 字段。 */
    static final Set<ApiFieldLocation> BODY_ONLY = Set.of(ApiFieldLocation.BODY);

    private final ApiFieldValidator validator;
    private final ChannelConfigChangeService changeService;
    private final ExternalApiConfigurationCodec codec;
    private final StandardApiRepository standardApis;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final StandardApiManagementService service;
    private final ExternalApiConfigurationProperties properties;

    @Override
    protected LockCommand lockCommand(CreateStandardApiRequest request) {
        return LockCommand.tryLock(ExternalApiLocks.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(CreateStandardApiRequest request) {
        return transactions.write(() -> {
            support.requireValid(fieldIssues(request));
            if (standardApis.byCode(request.getCode()) != null) {
                throw new BizCheckFailException("标准 API 编码已经存在：" + request.getCode());
            }
            CreateStandardApiContext context = buildContext(request, request.getActorId());
            service.create(context);
            changeService.record(support.changed(ChangeTargetType.STANDARD_API,
                context.getStandardApi().getStandardApiId(), ChangeType.CREATE, request.getActorId(), request.getCode(), 0,
                1, changes(request), request.getReason(), context.getStandardApi().getCreateTime()));
            return new ExternalApiMutation("STANDARD_API", request.getCode(), 1);
        });
    }

    /** 两个方向的字段规则，问题定位使用请求体数组名。 */
    List<ValidationIssue> fieldIssues(CreateStandardApiRequest request) {
        var issues = new ArrayList<ValidationIssue>(validator.validate(request.getRequestFields(), BODY_ONLY,
            "requestFields"));
        issues.addAll(validator.validate(request.getResponseFields(), BODY_ONLY, "responseFields"));
        return issues;
    }

    /** 插入前准备标识、初始版本 1 与两个方向的字段行。 */
    CreateStandardApiContext buildContext(CreateStandardApiRequest request, long actorId) {
        Instant now = support.now();
        long id = support.nextId();
        var row = new StandardApi(id, request.getCode(), request.getName(), request.getDescription(), 1, actorId,
            actorId, now, now);
        var rows = new ArrayList<ApiField>(support.apiFields(id, ApiFieldDirection.STANDARD_API_REQUEST,
            request.getRequestFields(), now));
        rows.addAll(support.apiFields(id, ApiFieldDirection.STANDARD_API_RESPONSE, request.getResponseFields(), now));
        return new CreateStandardApiContext(row, rows);
    }

    /** 新建时各字段的前值为空。 */
    List<ConfigurationFieldChange> changes(CreateStandardApiRequest request) {
        return List.of(new ConfigurationFieldChange(ConfigurationField.CODE, null, request.getCode()),
            new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, null, request.getName()),
            new ConfigurationFieldChange(ConfigurationField.DESCRIPTION, null, request.getDescription()),
            new ConfigurationFieldChange(ConfigurationField.REQUEST_FIELDS_DIGEST, null,
                codec.fieldsDigest(request.getRequestFields())),
            new ConfigurationFieldChange(ConfigurationField.RESPONSE_FIELDS_DIGEST, null,
                codec.fieldsDigest(request.getResponseFields())));
    }
}
