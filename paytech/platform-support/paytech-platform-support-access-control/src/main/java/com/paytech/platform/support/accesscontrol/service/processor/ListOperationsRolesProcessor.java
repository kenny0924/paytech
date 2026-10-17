package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsManagementModels.RoleSummary;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsDirectoryRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ListOperationsRolesRequest;

import lombok.AllArgsConstructor;

/** ListOperationsRoles 用例：授权、事实校验及本地事务编排。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListOperationsRolesProcessor
    extends AbstractProcessor<ListOperationsRolesRequest, PagingResponseBody<RoleSummary>> {
    private final OperationsManagementSupport support;
    private final OperationsDirectoryRepository directory;
    private final OperationsManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;

    /** 授权及响应事实共享原有只读一致性快照。 */
    @Override
    protected PagingResponseBody<RoleSummary> processRequest(ListOperationsRolesRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), "OPERATIONS_ROLE_READ");
            int offset = new PagingRequestBody(request.getPage(), request.getSize()).offset();
            return new PagingResponseBody<>(
                directory.roles(offset, request.getSize()).stream().map(support::roleSummary).toList(),
                request.getPage(), request.getSize(), directory.roleCount());
        });
    }
}
