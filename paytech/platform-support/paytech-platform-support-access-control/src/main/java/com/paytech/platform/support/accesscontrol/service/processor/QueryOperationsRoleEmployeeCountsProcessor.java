package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsManagementModels.RoleEmployeeCount;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.QueryOperationsRoleEmployeeCountsRequest;

import lombok.AllArgsConstructor;

/** QueryOperationsRoleEmployeeCounts 用例：授权、事实校验及本地事务编排。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class QueryOperationsRoleEmployeeCountsProcessor
    extends AbstractProcessor<QueryOperationsRoleEmployeeCountsRequest, List<RoleEmployeeCount>> {
    private final OperationsManagementSupport support;
    private final OperationsManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;

    /** 授权及响应事实共享原有只读一致性快照。 */
    @Override
    protected List<RoleEmployeeCount> processRequest(QueryOperationsRoleEmployeeCountsRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), "OPERATIONS_ROLE_READ");
            var ids = request.getRoleIds().stream().map(policy::parseId).toList();
            if (java.util.Set.copyOf(ids).size() != ids.size()) {
                throw new InvalidParameterException("角色列表不能包含重复角色");
            }
            var counts = support.employeeCounts(ids);
            return ids.stream().map(id -> new RoleEmployeeCount(Long.toString(id), counts.get(id))).toList();
        });
    }
}
