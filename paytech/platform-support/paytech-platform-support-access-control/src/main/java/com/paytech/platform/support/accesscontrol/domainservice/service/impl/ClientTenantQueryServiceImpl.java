package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementInputs;
import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.ClientTenant;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantFilter;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantAccessRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantProjectionTenantRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientTenantQueryService;

import lombok.AllArgsConstructor;

/** 只读本库 t_access_tenant；不判断来源或安全状态，由调用方按自身业务决定。 */
@Service
@AllArgsConstructor
public class ClientTenantQueryServiceImpl implements ClientTenantQueryService {

    private final TenantAccessRepository tenantAccess;
    private final TenantProjectionTenantRepository tenants;

    /** Client 未开通时返回空。 */
    @Override
    public Long findClientTenantId(long clientId) {
        var tenant = tenants.find(clientId);
        return tenant == null ? null : tenant.tenantId();
    }

    /** 数字关键字按 Client ID 精确匹配，否则按名称包含匹配；列表与计数使用同一筛选。 */
    @Override
    public PagingResponseBody<ClientTenant> searchClients(String keyword, PagingRequestBody paging) {
        TenantFilter filter = new TenantFilter();
        filter.setTenantType(AccessTenantType.CLIENT);
        String text = keyword == null || keyword.isBlank() ? null : keyword.strip();
        if (text != null && text.matches("[1-9][0-9]{0,17}")) {
            filter.setOwnerRefId(Long.parseLong(text));
        } else if (text != null) {
            filter.setTenantName(ClientApiManagementInputs.contains(text, 255, "keyword"));
        }
        var items = tenantAccess.tenants(filter, paging.offset(), paging.size()).stream()
            .map(tenant -> new ClientTenant(tenant.getTenantId(), tenant.getOwnerRefId(), tenant.getTenantName(),
                tenant.getOwnerStatus(), tenant.getSecurityStatus()))
            .toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), tenantAccess.tenantCount(filter));
    }
}
