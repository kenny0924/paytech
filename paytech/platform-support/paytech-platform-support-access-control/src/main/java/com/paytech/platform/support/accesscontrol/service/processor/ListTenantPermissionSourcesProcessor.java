package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.EvaluatedPage;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantPermissionSourceView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantAccessGrantRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ListTenantPermissionSourcesRequest;

import lombok.AllArgsConstructor;

/** 查询指定权限的全部来源，无来源返回 404，越界页保持为空。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListTenantPermissionSourcesProcessor
    extends AbstractProcessor<ListTenantPermissionSourcesRequest, EvaluatedPage<TenantPermissionSourceView>> {
    private final TenantAccessGrantRepository grants;
    private final TenantAccessManagementSupport support;
    private final OperationsManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;

    /** 授权、内容与计数全部来自本次 RR 只读快照。 */
    @Override
    protected EvaluatedPage<TenantPermissionSourceView> processRequest(ListTenantPermissionSourcesRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), "TENANT_MEMBER_READ");
            var scope = support.scope(request.getTenantId(), request.getMemberId(), true);
            var paging = request.getPaging();
            long total = grants.sourceCount(request.getMemberId(), request.getPermissionId());
            if (total == 0)
                throw new FailException(CommonErrorCode.NOT_FOUND);
            var items = grants.sources(request.getMemberId(), request.getPermissionId(), scope.getAsOf(),
                paging.offset(), paging.size());
            items.forEach(item -> item.evaluate(scope.getReasons()));
            return new EvaluatedPage<>(new PagingResponseBody<>(items, paging.page(), paging.size(), total),
                scope.getAsOf());
        });
    }
}
