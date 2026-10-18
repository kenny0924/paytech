package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.PermissionManagementProperties;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.Permission;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationMutation;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.CreatePermissionContext;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.PermissionAuditFacts;
import com.paytech.platform.support.accesscontrol.domain.enums.PermissionStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.PermissionRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.domainservice.service.PermissionManagementService;
import com.paytech.platform.support.accesscontrol.service.processor.request.CreatePermissionRequest;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 创建停用权限，不创建任何角色或成员授权。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreatePermissionProcessor extends AbstractLockProcessor<CreatePermissionRequest, ConfigurationMutation> {
    private final Clock clock;
    private final IdGenerator ids;
    private final PermissionRepository permissions;
    private final OperationsAuditService audit;
    private final PermissionManagementService service;
    private final PermissionManagementProperties properties;
    private final PermissionManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;

    /** 创建、编辑和启停按同一不可变权限编码协调，数据库保护继续保留。 */
    @Override
    protected LockCommand lockCommand(CreatePermissionRequest request) {
        return LockCommand.tryLock("permission:" + request.getPermissionCode(),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 锁内开启一次本地事务，不访问外部能力。 */
    @Override
    protected ConfigurationMutation processWithinLock(CreatePermissionRequest request) {
        return transactions.write(() -> execute(request));
    }

    /** 当前授权、原事实与版本校验通过后构造 Context，保存成功才登记审计。 */
    ConfigurationMutation execute(CreatePermissionRequest request) {
        var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(), null, null,
            false, "PERMISSION_MANAGE");
        if (permissions.permission(request.getPermissionCode()) != null)
            throw new BizCheckFailException("权限编码已经存在");
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var permission = new Permission(ids.nextId(), request.getPermissionCode(), request.getResourceType(),
            request.getActionCode(), request.getRiskLevel(), PermissionStatus.INACTIVE, 1, now, now);
        var context = new CreatePermissionContext(permission);
        service.create(context);
        var fact = PermissionAuditFacts.created(request.getActor(), actor.member().tenantId(), permission,
            request.getReason());
        audit.record(() -> fact);
        return permission.mutation();
    }
}
