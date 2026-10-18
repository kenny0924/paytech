package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.OperationsAuthenticationProperties;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsRoleView;
import com.paytech.platform.support.accesscontrol.domain.operations.UpdateOperationsRoleContext;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsDirectoryRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsRoleService;
import com.paytech.platform.support.accesscontrol.service.processor.request.UpdateOperationsRoleRequest;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.RoleMutation;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** UpdateOperationsRole 用例：授权、事实校验及本地事务编排。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateOperationsRoleProcessor
    extends AbstractLockProcessor<UpdateOperationsRoleRequest, OperationsRoleView> {
    private final Clock clock;
    private final OperationsRoleService roles;
    private final OperationsAuditService audit;
    private final OperationsManagementSupport support;
    private final OperationsDirectoryRepository directory;
    private final OperationsManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;
    private final OperationsAuthenticationProperties properties;

    /** 同资源命令共用 Key，模板统一负责锁及公共 Redis 异常。 */
    @Override
    protected LockCommand lockCommand(UpdateOperationsRoleRequest request) {
        return LockCommand.tryLock("operations:role:" + request.getRoleId(),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 本地写入在同一事务内完成；外部准备已在事务前结束。 */
    @Override
    protected OperationsRoleView processWithinLock(UpdateOperationsRoleRequest request) {
        return transactions.write(() -> {
            var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(),
                request.getRoleId(), null, false, "OPERATIONS_ROLE_UPDATE");
            var role = support.requireRole(request.getRoleId());
            support.requireCustomRole(role);
            var previous = directory.rolePermissionIds(role.id());
            var allowed = policy.grantablePermissionIds(actor);
            policy.requireGrantablePermissions(previous, allowed);
            var before = support.roleDetails(role, previous);
            var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            var permissions = support.permissionIds(request.getPermissionIds());
            policy.requireGrantablePermissions(permissions, allowed);
            policy.requireActiveNewPermissions(permissions, previous);
            support.requireVersion(role.version(), request.getExpectedVersion());
            var context = new UpdateOperationsRoleContext(role, actor.member().tenantId(),
                support.roleName(request.getRoleName()), previous, permissions, now);
            roles.updateRole(context);
            var result = support.roleDetails(role, permissions);
            audit.record(() -> RoleMutation.changed(request.getActor(), actor.member().tenantId(),
                support.auditRole(before), support.auditRole(result), request.getReason(), now));
            return result;
        });
    }
}
