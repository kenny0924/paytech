package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.OperationsAuthenticationProperties;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRole;
import com.paytech.platform.support.accesscontrol.domain.operations.CreateOperationsRoleContext;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsRoleView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsDirectoryRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsTenantRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsRoleService;
import com.paytech.platform.support.accesscontrol.service.processor.request.CreateOperationsRoleRequest;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.RoleMutation;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** CreateOperationsRole 用例：授权、事实校验及本地事务编排。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateOperationsRoleProcessor
    extends AbstractLockProcessor<CreateOperationsRoleRequest, OperationsRoleView> {
    private final Clock clock;
    private final IdGenerator ids;
    private final OperationsRoleService roles;
    private final OperationsAuditService audit;
    private final OperationsTenantRepository tenants;
    private final OperationsManagementSupport support;
    private final OperationsDirectoryRepository directory;
    private final OperationsManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;
    private final OperationsAuthenticationProperties properties;

    /** 同资源命令共用 Key，模板统一负责锁及公共 Redis 异常。 */
    @Override
    protected LockCommand lockCommand(CreateOperationsRoleRequest request) {
        return LockCommand.tryLock("operations:role-code:" + request.getRoleCode(),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 本地写入在同一事务内完成；外部准备已在事务前结束。 */
    @Override
    protected OperationsRoleView processWithinLock(CreateOperationsRoleRequest request) {
        try {
            return transactions.write(() -> {
                var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(), null,
                    null, false, "OPERATIONS_ROLE_CREATE");
                var permissions = support.permissionIds(request.getPermissionIds());
                policy.requireGrantablePermissions(permissions, policy.grantablePermissionIds(actor));
                policy.requireActiveNewPermissions(permissions, List.of());
                long tenant = actor.member().tenantId();
                if (directory.existingRoleCode(tenant, request.getRoleCode()) != 0) {
                    throw new BizCheckFailException("角色代码已存在或属于系统保留角色");
                }
                var role = new OperationsRole(ids.nextId(), request.getRoleCode(),
                    support.roleName(request.getRoleName()), false, 1, false);
                var context = new CreateOperationsRoleContext(role, tenant, permissions,
                    clock.instant().truncatedTo(ChronoUnit.MICROS));
                roles.createRole(context);
                var result = support.roleDetails(role, permissions);
                audit.record(() -> RoleMutation.changed(request.getActor(), tenant, null, support.auditRole(result),
                    request.getReason(), context.getNow()));
                return result;
            });
        } catch (org.springframework.dao.DuplicateKeyException failure) {
            Long tenant = tenants.internalTenant();
            if (String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_role_scope") && tenant != null
                && directory.existingRoleCode(tenant, request.getRoleCode()) != 0) {
                throw new BizCheckFailException("角色代码已存在或属于系统保留角色", failure);
            }
            throw failure;
        }
    }
}
