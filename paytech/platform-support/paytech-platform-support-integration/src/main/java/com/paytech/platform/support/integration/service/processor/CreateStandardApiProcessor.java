package com.paytech.platform.support.integration.service.processor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationField;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationFieldChange;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.integration.core.configuration.ExternalApiConfigurationProperties;
import com.paytech.platform.support.integration.core.dal.dataobject.ApiField;
import com.paytech.platform.support.integration.core.dal.dataobject.StandardApi;
import com.paytech.platform.support.integration.core.enums.ApiFieldDirection;
import com.paytech.platform.support.integration.domain.CreateStandardApiContext;
import com.paytech.platform.support.integration.domain.ExternalApiConfigurationCodec;
import com.paytech.platform.support.integration.domain.ExternalApiMutation;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domain.ValidationIssue;
import com.paytech.platform.support.integration.domainservice.repository.StandardApiRepository;
import com.paytech.platform.support.integration.domainservice.service.StandardApiManagementService;
import com.paytech.platform.support.integration.domainservice.validator.ApiFieldValidator;
import com.paytech.platform.support.integration.service.processor.request.CreateStandardApiRequest;

import lombok.AllArgsConstructor;

/**
 * E03 创建标准 API：全模块配置锁内，在一个本地事务中完成权限复核、字段静态校验、编码查重与保存。
 *
 * <p>标准 API 字段只使用 BODY 位置（逻辑设计 3.1）；编码唯一索引是最终保护。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateStandardApiProcessor extends AbstractLockProcessor<CreateStandardApiRequest, ExternalApiMutation> {

    /** 标准 API 两个方向都只允许 BODY 字段。 */
    static final Set<ApiFieldLocation> BODY_ONLY = Set.of(ApiFieldLocation.BODY);

    private final ApiFieldValidator validator;
    private final OperationsAuditService audit;
    private final ExternalApiConfigurationCodec codec;
    private final StandardApiRepository standardApis;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final StandardApiManagementService service;
    private final OperationsManagementPolicyService policy;
    private final ExternalApiConfigurationProperties properties;

    @Override
    protected LockCommand lockCommand(CreateStandardApiRequest request) {
        return LockCommand.tryLock(ExternalApiPermissions.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(CreateStandardApiRequest request) {
        return transactions.write(() -> {
            var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(), null,
                null, false, ExternalApiPermissions.MANAGE);
            support.requireValid(fieldIssues(request));
            if (standardApis.byCode(request.getCode()) != null) {
                throw new BizCheckFailException("标准 API 编码已经存在：" + request.getCode());
            }
            CreateStandardApiContext context = buildContext(request, policy.parseId(request.getActor()));
            service.create(context);
            audit.record(() -> support.changed(request.getActor(), actor.member().tenantId(), "STANDARD_API",
                request.getCode(), 0, 1, changes(request), request.getReason(), context.getStandardApi().getCreateTime()));
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
