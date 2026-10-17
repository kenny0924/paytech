package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsManagementModels.RoleResource;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.QueryOperationsRoleResourcesRequest;

import lombok.AllArgsConstructor;

/** QueryOperationsRoleResources 用例：授权、事实校验及本地事务编排。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class QueryOperationsRoleResourcesProcessor
    extends AbstractProcessor<QueryOperationsRoleResourcesRequest, List<RoleResource>> {
    private final OperationsManagementSupport support;
    private final OperationsManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;

    /** 授权及响应事实共享原有只读一致性快照。 */
    @Override
    protected List<RoleResource> processRequest(QueryOperationsRoleResourcesRequest request) {
        return transactions.query(() -> {
            var actor = policy.requirePermission(request.getActor(), request.getSession(), "OPERATIONS_ROLE_CREATE",
                "OPERATIONS_ROLE_UPDATE");
            return policy.grantableResources(actor.member().id());
        });
    }
}
