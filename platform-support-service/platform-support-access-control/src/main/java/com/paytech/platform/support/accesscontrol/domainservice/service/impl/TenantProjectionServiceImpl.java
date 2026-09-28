package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.paytech.platform.support.accesscontrol.domain.tenantaccess.ProjectTenantStatusContext;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.ProvisionTenantContext;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantProjectionGrantRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantProjectionMemberRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantProjectionRoleRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantProjectionTenantRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.TenantProjectionService;

import lombok.AllArgsConstructor;

/** 只操作本库四张表，复用 Processor 已确认的完整 Context。 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "tenant.projection.enabled", havingValue = "true")
public class TenantProjectionServiceImpl implements TenantProjectionService {
    private final TenantProjectionRoleRepository roles;
    private final TenantProjectionGrantRepository grants;
    private final TenantProjectionMemberRepository members;
    private final TenantProjectionTenantRepository tenants;

    @Override
    public void provision(ProvisionTenantContext context) {
        tenants.insert(context.getTenant(), context.getCreateTime());
        members.insert(context.getMember(), context.getTenant().tenantId(), context.getCreateTime());
        roles.insert(context.getRole(), context.getCreateTime());
        grants.insert(context.getGrant(), context.getCreateTime());
    }

    @Override
    public void projectStatus(ProjectTenantStatusContext context) {
        if (context.activatesOwner()) {
            grants.revokeOnboarding(context.getTenant().tenantId(), context.getMember().memberId(), context.getUpdateTime());
            roles.insert(context.getOwnerRole(), context.getUpdateTime());
            grants.insert(context.getOwnerGrant(), context.getUpdateTime());
        }
        tenants.update(context.getTenant(), context.getSourceVersion(), context.getPayloadHash(),
            context.getOwnerStatus(), context.getUpdateTime());
    }
}
