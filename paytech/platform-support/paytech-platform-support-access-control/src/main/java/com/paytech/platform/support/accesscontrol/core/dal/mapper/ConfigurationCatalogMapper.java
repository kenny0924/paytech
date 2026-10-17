package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.Permission;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.PermissionFilter;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.ResourceReference;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.RoleReference;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.RouteReference;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConsoleResourceView;

/** 菜单与权限配置的本库查询、CAS 和引用访问。 */
public interface ConfigurationCatalogMapper {
    List<Long> lockResourceReferences(@org.apache.ibatis.annotations.Param("permissionId") long permissionId);

    List<Long> lockRouteReferences(@org.apache.ibatis.annotations.Param("permissionId") long permissionId);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    List<ConsoleResourceView> resources(@Param("limit") int limit);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    ConsoleResourceView resource(@Param("code") String code);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    List<Permission> permissions(@Param("filter") PermissionFilter filter, @Param("offset") int offset, @Param("size") int size);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    long permissionCount(@Param("filter") PermissionFilter filter);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    Permission permission(@Param("code") String code);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    Permission lockPermission(@Param("id") long id, @Param("exclusive") boolean exclusive);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    int insertPermission(@Param("row") Permission row);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    int updatePermission(@Param("row") Permission row,
        @Param("riskLevel") com.paytech.platform.support.accesscontrol.domain.enums.OperationsPermissionRiskLevel riskLevel,
        @Param("status") com.paytech.platform.support.accesscontrol.domain.enums.PermissionStatus status,
        @Param("updateTime") Instant updateTime);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    long resourceReferenceCount(@Param("permissionId") long permissionId);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    long routeReferenceCount(@Param("permissionId") long permissionId);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    long roleReferenceCount(@Param("permissionId") long permissionId);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    List<ResourceReference> resourceReferences(@Param("permissionId") long permissionId, @Param("offset") int offset, @Param("size") int size);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    List<RouteReference> routeReferences(@Param("permissionId") long permissionId, @Param("offset") int offset, @Param("size") int size);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    List<RoleReference> roleReferences(@Param("permissionId") long permissionId, @Param("offset") int offset, @Param("size") int size);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    List<Permission> delegablePermissions(@Param("memberId") long memberId, @Param("effectiveAt") Instant effectiveAt, @Param("administrator") boolean administrator, @Param("includeInactive") boolean includeInactive);
}
