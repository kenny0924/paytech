package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccountSummary;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsAccountView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsDirectoryRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ListOperationsAccountsRequest;

import lombok.AllArgsConstructor;

/** ListOperationsAccounts 用例：授权、事实校验及本地事务编排。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListOperationsAccountsProcessor
    extends AbstractProcessor<ListOperationsAccountsRequest, PagingResponseBody<OperationsAccountView>> {
    private final OperationsManagementSupport support;
    private final OperationsDirectoryRepository directory;
    private final OperationsManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;

    /** 授权及响应事实共享原有只读一致性快照。 */
    @Override
    protected PagingResponseBody<OperationsAccountView> processRequest(ListOperationsAccountsRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), "OPERATIONS_ACCOUNT_READ");
            int offset = new PagingRequestBody(request.getPage(), request.getSize()).offset();
            var rows = directory.accountSummaries(offset, request.getSize());
            if (rows.stream()
                .anyMatch(row -> row.memberId() == null || row.memberVersion() == null || row.forceChange() == null)) {
                throw new ErrorException("Operations人员当前事实不完整");
            }
            var roles = directory.memberRoles(rows.stream().map(OperationsAccountSummary::memberId).toList());
            var items = rows.stream()
                .map(row -> new OperationsAccountView(Long.toString(row.accountId()), row.emailMasked(),
                    row.status().getCode(), row.forceChange(), row.lockedUntil(), row.accountVersion(),
                    row.memberId().toString(), row.memberVersion(),
                    roles.get(row.memberId()).stream().map(support::roleSummary).toList()))
                .toList();
            return new PagingResponseBody<>(items, request.getPage(), request.getSize(), directory.accountCount());
        });
    }
}
