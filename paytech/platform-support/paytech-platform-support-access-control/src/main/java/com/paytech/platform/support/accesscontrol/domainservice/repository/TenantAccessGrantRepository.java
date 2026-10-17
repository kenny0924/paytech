package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantMemberPermissionView;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantPermissionSourceView;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantRoleGrantView;

/** 以成员角色授予表为入口的授权链查询，不修改角色或权限。 */
public interface TenantAccessGrantRepository {

    /** 检测成员角色授予的归属和区间完整性。 */
    long invalidGrants(long tenantId, long memberId);

    /** 检测权限关联缺失及未知状态。 */
    long invalidPermissions(long memberId);

    /** 分页读取角色授予及本次失效标记。 */
    List<TenantRoleGrantView> roles(long memberId, Instant asOf, long offset, int size);

    /** 统计成员全部角色授予。 */
    long roleCount(long memberId);

    /** 先分页权限再在数据库聚合本页来源。 */
    List<TenantMemberPermissionView> permissions(long memberId, Instant asOf, long offset, int size);

    /** 统计完整授权链中的去重权限数。 */
    long permissionCount(long memberId);

    /** 分页读取指定权限的完整来源。 */
    List<TenantPermissionSourceView> sources(long memberId, long permissionId, Instant asOf, long offset, int size);

    /** 统计指定权限全部来源。 */
    long sourceCount(long memberId, long permissionId);
}
