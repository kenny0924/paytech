package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.accesscontrol.core.dal.mapper.TenantAccessManagementMapper;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantMemberPermissionView;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantPermissionSourceView;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantRoleGrantView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantAccessGrantRepository;

import lombok.AllArgsConstructor;

/** 以成员角色授予表为入口的授权链查询，不修改角色或权限。 */
@Repository
@AllArgsConstructor
public class TenantAccessGrantRepositoryImpl implements TenantAccessGrantRepository {
    private final TenantAccessManagementMapper mapper;

    @Override
    public long invalidGrants(long tenantId, long memberId) { return mapper.invalidGrants(tenantId, memberId); }

    @Override
    public long invalidPermissions(long memberId) { return mapper.invalidPermissions(memberId); }

    @Override
    public List<TenantRoleGrantView> roles(long memberId, Instant asOf, long offset, int size) { return mapper.roles(memberId, asOf, offset, size); }

    @Override
    public long roleCount(long memberId) { return mapper.roleCount(memberId); }

    @Override
    public List<TenantMemberPermissionView> permissions(long memberId, Instant asOf, long offset, int size) { return mapper.permissions(memberId, asOf, offset, size); }

    @Override
    public long permissionCount(long memberId) { return mapper.permissionCount(memberId); }

    @Override
    public List<TenantPermissionSourceView> sources(long memberId, long permissionId, Instant asOf, long offset, int size) { return mapper.sources(memberId, permissionId, asOf, offset, size); }

    @Override
    public long sourceCount(long memberId, long permissionId) { return mapper.sourceCount(memberId, permissionId); }
}
