package com.paytech.platform.support.integration.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationField;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationFieldChange;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.integration.core.configuration.ExternalApiConfigurationProperties;
import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationImplementation;
import com.paytech.platform.support.integration.core.dal.dataobject.StandardApi;
import com.paytech.platform.support.integration.domain.ExternalApiMutation;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationImplementationRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationRepository;
import com.paytech.platform.support.integration.domainservice.repository.StandardApiRepository;
import com.paytech.platform.support.integration.domainservice.service.StandardApiManagementService;
import com.paytech.platform.support.integration.service.processor.request.DeleteExternalApiConfigRequest;

import lombok.AllArgsConstructor;

/**
 * E05 删除标准 API：全模块配置锁内锁定原行、核对版本；仍被接入方实现时拒绝并列出实现方，否则物理删除主表与字段。
 *
 * <p>实现的新增同样持有该配置锁，锁内读取的引用关系在提交前不会变化；版本 CAS 仍是最终保护。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class DeleteStandardApiProcessor
    extends AbstractLockProcessor<DeleteExternalApiConfigRequest, ExternalApiMutation> {

    private final OperationsAuditService audit;
    private final IntegrationRepository integrations;
    private final StandardApiRepository standardApis;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final StandardApiManagementService service;
    private final OperationsManagementPolicyService policy;
    private final ExternalApiConfigurationProperties properties;
    private final IntegrationImplementationRepository implementations;

    @Override
    protected LockCommand lockCommand(DeleteExternalApiConfigRequest request) {
        return LockCommand.tryLock(ExternalApiPermissions.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(DeleteExternalApiConfigRequest request) {
        return transactions.write(() -> {
            var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(), null,
                null, false, ExternalApiPermissions.MANAGE);
            StandardApi row = support.requireFound(standardApis.lock(request.getCode()));
            support.requireVersion(row.getVersion(), request.getExpectedVersion());
            requireUnreferenced(row);
            int version = row.getVersion();
            service.delete(row);
            var changes = List.of(
                new ConfigurationFieldChange(ConfigurationField.CODE, row.getStandardApiCode(), null),
                new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, row.getStandardApiName(), null));
            audit.record(() -> support.changed(request.getActor(), actor.member().tenantId(), "STANDARD_API",
                row.getStandardApiCode(), version, version, changes, request.getReason(), support.now()));
            return new ExternalApiMutation("STANDARD_API", row.getStandardApiCode(), version);
        });
    }

    /** 被接入方实现时拒绝，提示中列出实现方编码。 */
    void requireUnreferenced(StandardApi row) {
        var ids = implementations.allByStandardApi(row.getStandardApiId()).stream()
            .map(IntegrationImplementation::getIntegrationId).toList();
        if (ids.isEmpty()) {
            return;
        }
        String codes = String.join("、", integrations.byIds(ids).stream().map(Integration::getIntegrationCode).toList());
        throw new BizCheckFailException("标准 API 仍被接入方实现，不能删除：" + codes);
    }
}
