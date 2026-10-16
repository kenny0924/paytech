package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionRole;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.TenantProjectionMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantProjectionRoleRepository;

import lombok.AllArgsConstructor;

/** 复用本表 SQL，不开启事务或调用外部能力。 */
@Repository
@AllArgsConstructor
public class TenantProjectionRoleRepositoryImpl implements TenantProjectionRoleRepository {
    private final TenantProjectionMapper mapper;

    /** 读取正式 OWNER 系统角色，不按角色状态筛除。 */
    @Override
    public Long ownerRole(long tenantId) {
        return mapper.ownerRole(tenantId);
    }

    /** 保存本 Tenant 的具名系统角色。 */
    @Override
    public void insert(TenantProjectionRole role, Instant now) {
        requireOne(mapper.insertRole(role.roleId(), role.tenantId(), role.roleCode(), now));
    }

    /** 按编码读取当前 ACTIVE 的权限主键。 */
    @Override
    public java.util.List<Long> activePermissionIds(java.util.Collection<String> codes) {
        return mapper.activePermissionIds(codes);
    }

    /** 为正式 OWNER 角色写入一项 ACTIVE 权限。 */
    @Override
    public void insertPermission(com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionRolePermission permission,
        Instant now) {
        requireOne(mapper.insertRolePermission(permission.rolePermissionId(), permission.roleId(), permission.permissionId(), now));
    }

    /** 写入必须准确影响一行，不将其他唯一键冲突解释为成功。 */
    private void requireOne(int rows) {
        if (rows != 1) throw new ConcurrentRequestException("租户投影已被并发更新，请重试原来源命令");
    }
}
