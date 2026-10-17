package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.Permission;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.ResourceReference;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.RoleReference;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.RouteReference;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConsoleResourceView;

/** 菜单与权限配置的本库查询、CAS 和引用访问。 */
public interface ConfigurationCatalogRepository {

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    List<ConsoleResourceView> resources(int limit);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    ConsoleResourceView resource(String code);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    Permission lockPermission(long id, boolean exclusive);

    /** 冻结权限当前的 Operations 资源、路由及目标引用，供角色/人员命令重检资格。 */
    void lockOperationsReferences(long permissionId);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    long resourceReferenceCount(long permissionId);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    long routeReferenceCount(long permissionId);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    long roleReferenceCount(long permissionId);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    List<ResourceReference> resourceReferences(long permissionId, int offset, int size);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    List<RouteReference> routeReferences(long permissionId, int offset, int size);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    List<RoleReference> roleReferences(long permissionId, int offset, int size);

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    List<Permission> delegablePermissions(long memberId, Instant effectiveAt, boolean administrator, boolean includeInactive);
}
