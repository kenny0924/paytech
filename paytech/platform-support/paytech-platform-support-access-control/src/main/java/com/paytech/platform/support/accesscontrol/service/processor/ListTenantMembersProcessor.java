package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementInputs;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.MemberFilter;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantMemberView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantAccessMemberRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantAccessRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ListTenantMembersRequest;

import lombok.AllArgsConstructor;

/** 查询精确租户下的成员，筛选不能隐藏损坏账号关联。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListTenantMembersProcessor
    extends AbstractProcessor<ListTenantMembersRequest, PagingResponseBody<TenantMemberView>> {
    private final TenantAccessRepository tenants;
    private final TenantAccessMemberRepository members;
    private final TenantAccessManagementSupport support;
    private final OperationsManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;

    /** 授权、内容与计数全部来自本次 RR 只读快照。 */
    @Override
    protected PagingResponseBody<TenantMemberView> processRequest(ListTenantMembersRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), "TENANT_MEMBER_READ");
            long tenantId = request.getTenantId();
            support.requireTenant(tenants.tenant(tenantId));
            if (members.invalidMembers(tenantId) != 0)
                throw new ErrorException("租户成员授权记录不一致，请联系管理员检查");
            var paging = request.getPaging();
            var filter = new MemberFilter(request.getAccountId(),
                ClientApiManagementInputs.contains(request.getEmailMasked(), 255, "emailMasked"),
                request.getMemberType(), request.getMemberStatus());
            var items = members.members(tenantId, filter, paging.offset(), paging.size()).stream()
                .map(support::requireMember).toList();
            return new PagingResponseBody<>(items, paging.page(), paging.size(), members.memberCount(tenantId, filter));
        });
    }
}
