package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantAccessSummary;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantAccessRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.GetTenantAccessRequest;

import lombok.AllArgsConstructor;

/** 读取指定客户或合作伙伴的当前租户访问条件。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetTenantAccessProcessor extends AbstractProcessor<GetTenantAccessRequest, TenantAccessSummary> {
    private final TenantAccessRepository tenants;
    private final TenantAccessManagementSupport support;
    private final OperationsManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;

    /** 授权、内容与计数全部来自本次 RR 只读快照。 */
    @Override
    protected TenantAccessSummary processRequest(GetTenantAccessRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), "TENANT_ACCESS_READ");
            return new TenantAccessSummary(support.requireTenant(tenants.tenant(request.getTenantId())));
        });
    }
}
