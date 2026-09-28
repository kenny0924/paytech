package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantMemberView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantAccessMemberRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantAccessRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.GetTenantMemberRequest;

import lombok.AllArgsConstructor;

/** 读取租户内成员，不能通过其他租户的成员 ID 串读。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetTenantMemberProcessor extends AbstractProcessor<GetTenantMemberRequest, TenantMemberView> {
    private final TenantAccessRepository tenants;
    private final TenantAccessMemberRepository members;
    private final TenantAccessManagementSupport support;
    private final OperationsManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;

    /** 授权、内容与计数全部来自本次 RR 只读快照。 */
    @Override
    protected TenantMemberView processRequest(GetTenantMemberRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), "TENANT_MEMBER_READ");
            support.requireTenant(tenants.tenant(request.getTenantId()));
            return support.requireMember(members.member(request.getTenantId(), request.getMemberId()));
        });
    }
}
