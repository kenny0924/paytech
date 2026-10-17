package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsManagementModels.PermissionSummary;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.QueryOperationsPermissionsRequest;

import lombok.AllArgsConstructor;

/** QueryOperationsPermissions 用例：授权、事实校验及本地事务编排。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class QueryOperationsPermissionsProcessor
    extends AbstractProcessor<QueryOperationsPermissionsRequest, List<PermissionSummary>> {
    private final OperationsManagementSupport support;
    private final OperationsManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;

    /** 授权及响应事实共享原有只读一致性快照。 */
    @Override
    protected List<PermissionSummary> processRequest(QueryOperationsPermissionsRequest request) {
        return transactions.query(() -> {
            var actor = policy.requirePermission(request.getActor(), request.getSession(), "OPERATIONS_ROLE_CREATE",
                "OPERATIONS_ROLE_UPDATE");
            return policy.grantablePermissions(actor.member().id()).stream()
                .map(permission -> new PermissionSummary(Long.toString(permission.permissionId()),
                    permission.permissionCode(), permission.resourceType(), permission.actionCode(),
                    permission.riskLevel().getCode()))
                .toList();
        });
    }
}
