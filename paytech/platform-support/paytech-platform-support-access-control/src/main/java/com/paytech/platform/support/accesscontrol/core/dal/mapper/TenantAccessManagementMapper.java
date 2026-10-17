package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantAccess;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.MemberFilter;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantFilter;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantMemberPermissionView;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantMemberView;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantPermissionSourceView;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantRoleGrantView;

/** 租户访问持久化边界；所有业务 SQL 集中在 Mapper XML。 */
@Mapper
public interface TenantAccessManagementMapper {

    /** 读取指定租户安全字段。 */
    TenantAccess tenant(@Param("tenantId") long tenantId);

    /** 在当前事务锁定目标租户行。 */
    TenantAccess lock(@Param("tenantId") long tenantId);

    /** 按相同筛选稳定分页读取租户。 */
    List<TenantAccess> tenants(@Param("filter") TenantFilter filter, @Param("offset") long offset, @Param("size") int size);

    /** 按相同筛选统计租户数。 */
    long tenantCount(@Param("filter") TenantFilter filter);

    /** 按租户及成员精确读取安全字段。 */
    TenantMemberView member(@Param("tenantId") long tenantId, @Param("memberId") long memberId);

    /** 分页读取指定租户的成员。 */
    List<TenantMemberView> members(@Param("tenantId") long tenantId, @Param("filter") MemberFilter filter, @Param("offset") long offset, @Param("size") int size);

    /** 按相同筛选统计成员数。 */
    long memberCount(@Param("tenantId") long tenantId, @Param("filter") MemberFilter filter);

    /** 检测本租户中缺失或非法账号关联。 */
    long invalidMembers(@Param("tenantId") long tenantId);

    /** 检测成员角色授予的归属和区间完整性。 */
    long invalidGrants(@Param("tenantId") long tenantId, @Param("memberId") long memberId);

    /** 检测权限关联缺失及未知状态。 */
    long invalidPermissions(@Param("memberId") long memberId);

    /** 分页读取角色授予及本次失效标记。 */
    List<TenantRoleGrantView> roles(@Param("memberId") long memberId, @Param("asOf") Instant asOf, @Param("offset") long offset, @Param("size") int size);

    /** 统计成员全部角色授予。 */
    long roleCount(@Param("memberId") long memberId);

    /** 先分页权限再在数据库聚合本页来源。 */
    List<TenantMemberPermissionView> permissions(@Param("memberId") long memberId, @Param("asOf") Instant asOf, @Param("offset") long offset, @Param("size") int size);

    /** 统计完整授权链中的去重权限数。 */
    long permissionCount(@Param("memberId") long memberId);

    /** 分页读取指定权限的完整来源。 */
    List<TenantPermissionSourceView> sources(@Param("memberId") long memberId, @Param("permissionId") long permissionId, @Param("asOf") Instant asOf, @Param("offset") long offset, @Param("size") int size);

    /** 统计指定权限全部来源。 */
    long sourceCount(@Param("memberId") long memberId, @Param("permissionId") long permissionId);

    /** 读取本次数据库 UTC 微秒时刻。 */
    Instant currentTime();

    /** CAS 更新目标安全状态及并发版本。 */
    int changeSecurityStatus(@Param("tenantId") long tenantId, @Param("securityStatus") OperationsTenantSecurityStatus securityStatus, @Param("expectedVersion") int expectedVersion, @Param("now") Instant now);
}
