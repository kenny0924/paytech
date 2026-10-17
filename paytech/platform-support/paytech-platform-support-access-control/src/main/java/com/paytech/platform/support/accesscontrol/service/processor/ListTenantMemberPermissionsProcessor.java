package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.EvaluatedPage;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantMemberPermissionView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantAccessGrantRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ListTenantMemberPermissionsRequest;

import lombok.AllArgsConstructor;

/** 合并同一权限的角色来源，任一完整来源有效即可。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListTenantMemberPermissionsProcessor
    extends AbstractProcessor<ListTenantMemberPermissionsRequest, EvaluatedPage<TenantMemberPermissionView>> {
    private final TenantAccessGrantRepository grants;
    private final TenantAccessManagementSupport support;
    private final OperationsManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;

    /** 授权、内容与计数全部来自本次 RR 只读快照。 */
    @Override
    protected EvaluatedPage<TenantMemberPermissionView> processRequest(ListTenantMemberPermissionsRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), "TENANT_MEMBER_READ");
            var scope = support.scope(request.getTenantId(), request.getMemberId(), true);
            var paging = request.getPaging();
            var items = grants.permissions(request.getMemberId(), scope.getAsOf(), paging.offset(), paging.size());
            items.forEach(item -> item.evaluate(scope.getReasons()));
            return new EvaluatedPage<>(new PagingResponseBody<>(items, paging.page(), paging.size(),
                grants.permissionCount(request.getMemberId())), scope.getAsOf());
        });
    }
}
