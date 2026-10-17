package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsRoleView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsDirectoryRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.QueryOperationsRoleRequest;

import lombok.AllArgsConstructor;

/** QueryOperationsRole 用例：授权、事实校验及本地事务编排。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class QueryOperationsRoleProcessor extends AbstractProcessor<QueryOperationsRoleRequest, OperationsRoleView> {
    private final OperationsManagementSupport support;
    private final OperationsDirectoryRepository directory;
    private final OperationsManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;

    /** 授权及响应事实共享原有只读一致性快照。 */
    @Override
    protected OperationsRoleView processRequest(QueryOperationsRoleRequest request) {
        return transactions.query(() -> {
            var actor = policy.requirePermission(request.getActor(), request.getSession(), "OPERATIONS_ROLE_UPDATE");
            var role = support.requireRole(request.getRoleId());
            var permissions = directory.rolePermissionIds(role.id());
            policy.requireGrantablePermissions(permissions, policy.grantablePermissionIds(actor.member().id()));
            return support.roleDetails(role, permissions);
        });
    }
}
