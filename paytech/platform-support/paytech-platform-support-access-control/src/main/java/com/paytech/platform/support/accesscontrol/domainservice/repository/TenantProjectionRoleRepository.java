package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionRole;

/** 来源投影中该表的本地持久化边界。 */
public interface TenantProjectionRoleRepository {

    /** 读取正式 OWNER 系统角色，不按角色状态筛除。 */
    Long ownerRole(long tenantId);

    /** 保存本 Tenant 的具名系统角色。 */
    void insert(TenantProjectionRole role, Instant now);

    /** 按编码读取当前 ACTIVE 的权限主键。 */
    java.util.List<Long> activePermissionIds(java.util.Collection<String> codes);

    /** 为正式 OWNER 角色写入一项 ACTIVE 权限。 */
    void insertPermission(com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionRolePermission permission,
        Instant now);
}
