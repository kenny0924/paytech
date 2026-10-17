package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.PermissionManagementProperties;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ChangePermissionStatusContext;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationMutation;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.PermissionAuditFacts;
import com.paytech.platform.support.accesscontrol.domain.enums.PermissionStatus;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.domainservice.service.PermissionManagementService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ChangePermissionStatusRequest;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 启停保留关联，管理入口引用的权限不可停用。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ChangePermissionStatusProcessor
    extends AbstractLockProcessor<ChangePermissionStatusRequest, ConfigurationMutation> {
    private final Clock clock;
    private final OperationsAuditService audit;
    private final PermissionManagementService service;
    private final PermissionManagementSupport support;
    private final PermissionManagementProperties properties;
    private final PermissionManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;

    /** 创建、编辑和启停按同一不可变权限编码协调，数据库保护继续保留。 */
    @Override
    protected LockCommand lockCommand(ChangePermissionStatusRequest request) {
        return LockCommand.tryLock("permission:" + request.getPermissionCode(),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 锁内开启一次本地事务，不访问外部能力。 */
    @Override
    protected ConfigurationMutation processWithinLock(ChangePermissionStatusRequest request) {
        return transactions.write(() -> execute(request));
    }

    /** 当前授权、原事实与版本校验通过后构造 Context，保存成功才登记审计。 */
    ConfigurationMutation execute(ChangePermissionStatusRequest request) {
        var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(), null, null,
            false, "PERMISSION_MANAGE");
        var permission = support.lock(request.getPermissionCode(), request.getExpectedVersion());
        if (permission.status() == request.getStatus())
            throw new BizCheckFailException("权限已处于请求状态");
        if (request.getStatus() == PermissionStatus.INACTIVE
            && !support.protection(permission.permissionId()).statusChangeAllowed()) {
            throw new BizCheckFailException("内置管理入口引用的权限不可停用");
        }
        var context = new ChangePermissionStatusContext(permission, request.getStatus(),
            clock.instant().truncatedTo(ChronoUnit.MICROS));
        var fact = PermissionAuditFacts.changed(request.getActor(), actor.member().tenantId(), permission,
            permission.riskLevel(), context.getStatus(), request.getReason(), context.getUpdateTime());
        service.changeStatus(context);
        audit.record(() -> fact);
        return permission.mutation();
    }
}
