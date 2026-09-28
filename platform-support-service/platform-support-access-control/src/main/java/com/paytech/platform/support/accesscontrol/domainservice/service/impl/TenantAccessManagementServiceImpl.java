package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.paytech.platform.support.accesscontrol.domain.tenantaccess.ChangeTenantSecurityStatusContext;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantAccessRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.TenantAccessManagementService;

import lombok.AllArgsConstructor;

/** 只保存租户表，不调用其他 Service、外部能力或请求幂等。 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class TenantAccessManagementServiceImpl implements TenantAccessManagementService {
    private final TenantAccessRepository tenants;

    @Override
    public void changeSecurityStatus(ChangeTenantSecurityStatusContext context) {
        tenants.changeSecurityStatus(context.getTenant(), context.getSecurityStatus(), context.getUpdateTime());
    }
}
