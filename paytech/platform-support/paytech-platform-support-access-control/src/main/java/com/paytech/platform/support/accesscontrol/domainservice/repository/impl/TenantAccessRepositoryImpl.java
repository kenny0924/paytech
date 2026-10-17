package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantAccess;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.TenantAccessManagementMapper;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantFilter;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantAccessRepository;

import lombok.AllArgsConstructor;

/** 租户表的安全状态读取与条件更新边界。 */
@Repository
@AllArgsConstructor
public class TenantAccessRepositoryImpl implements TenantAccessRepository {
    private final TenantAccessManagementMapper mapper;

    @Override
    public TenantAccess tenant(long tenantId) { return mapper.tenant(tenantId); }

    @Override
    public TenantAccess lock(long tenantId) { return mapper.lock(tenantId); }

    @Override
    public List<TenantAccess> tenants(TenantFilter filter, long offset, int size) { return mapper.tenants(filter, offset, size); }

    @Override
    public long tenantCount(TenantFilter filter) { return mapper.tenantCount(filter); }

    @Override
    public Instant currentTime() { return mapper.currentTime(); }

    /** 数据库条件和行数是最终保护，失败不修改内存对象。 */
    @Override
    public void changeSecurityStatus(TenantAccess tenant, OperationsTenantSecurityStatus status, Instant now) {
        if (tenant.getVersion() == Integer.MAX_VALUE
            || mapper.changeSecurityStatus(tenant.getTenantId(), status, tenant.getVersion(), now) != 1)
            throw new ConcurrentRequestException("租户已被其他操作修改，请刷新后重试");
        tenant.setSecurityStatus(status);
        tenant.setVersion(tenant.getVersion() + 1);
        tenant.setUpdatedAt(now);
    }
}
