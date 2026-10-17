package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.EvaluatedPage;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantRoleGrantView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantAccessGrantRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ListTenantMemberRolesRequest;

import lombok.AllArgsConstructor;

/** 按数据库时刻解释成员角色授予，保留未生效及失效记录。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListTenantMemberRolesProcessor
    extends AbstractProcessor<ListTenantMemberRolesRequest, EvaluatedPage<TenantRoleGrantView>> {
    private final TenantAccessGrantRepository grants;
    private final TenantAccessManagementSupport support;
    private final OperationsManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;

    /** 授权、内容与计数全部来自本次 RR 只读快照。 */
    @Override
    protected EvaluatedPage<TenantRoleGrantView> processRequest(ListTenantMemberRolesRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), "TENANT_MEMBER_READ");
            var scope = support.scope(request.getTenantId(), request.getMemberId(), false);
            var paging = request.getPaging();
            var items = grants.roles(request.getMemberId(), scope.getAsOf(), paging.offset(), paging.size());
            items.forEach(item -> item.evaluate(scope.getReasons()));
            return new EvaluatedPage<>(
                new PagingResponseBody<>(items, paging.page(), paging.size(), grants.roleCount(request.getMemberId())),
                scope.getAsOf());
        });
    }
}
