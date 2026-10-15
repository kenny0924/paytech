package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantProjectionTenantRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientTenantQueryService;

import lombok.AllArgsConstructor;

/** 只读本库 t_access_tenant；不判断来源或安全状态，由调用方按自身业务决定。 */
@Service
@AllArgsConstructor
public class ClientTenantQueryServiceImpl implements ClientTenantQueryService {

    private final TenantProjectionTenantRepository tenants;

    /** Client 未开通时返回空。 */
    @Override
    public Long findClientTenantId(long clientId) {
        var tenant = tenants.find(clientId);
        return tenant == null ? null : tenant.tenantId();
    }
}
