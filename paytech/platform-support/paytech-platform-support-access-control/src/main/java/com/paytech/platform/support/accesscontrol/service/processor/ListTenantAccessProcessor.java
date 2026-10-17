package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementInputs;
import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantAccessSummary;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantFilter;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantAccessRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ListTenantAccessRequest;

import lombok.AllArgsConstructor;

/** 分页读取客户与合作伙伴租户，失效状态保持可见。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListTenantAccessProcessor
    extends AbstractProcessor<ListTenantAccessRequest, PagingResponseBody<TenantAccessSummary>> {
    private final TenantAccessRepository tenants;
    private final TenantAccessManagementSupport support;
    private final OperationsManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;

    /** 授权、内容与计数全部来自本次 RR 只读快照。 */
    @Override
    protected PagingResponseBody<TenantAccessSummary> processRequest(ListTenantAccessRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), "TENANT_ACCESS_READ");
            if (request.getTenantType() == AccessTenantType.INTERNAL)
                throw new InvalidParameterException("只支持 CLIENT 或 PARTNER 租户");
            var paging = request.getPaging();
            var filter = new TenantFilter(request.getTenantType(), request.getTenantId(), request.getOwnerRefId(),
                ClientApiManagementInputs.contains(request.getTenantName(), 255, "tenantName"),
                request.getOwnerStatus(), request.getSecurityStatus());
            var items = tenants.tenants(filter, paging.offset(), paging.size()).stream().map(support::requireTenant)
                .map(TenantAccessSummary::new).toList();
            return new PagingResponseBody<>(items, paging.page(), paging.size(), tenants.tenantCount(filter));
        });
    }
}
