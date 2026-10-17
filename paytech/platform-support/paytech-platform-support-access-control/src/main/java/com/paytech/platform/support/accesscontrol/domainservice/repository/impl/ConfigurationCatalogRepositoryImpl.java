package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.Permission;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.ConfigurationCatalogMapper;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.ResourceReference;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.RoleReference;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.RouteReference;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConsoleResourceView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ConfigurationCatalogRepository;

/** 菜单与权限配置的本库查询、CAS 和引用访问。 */
@org.springframework.stereotype.Repository
@lombok.AllArgsConstructor
public class ConfigurationCatalogRepositoryImpl implements ConfigurationCatalogRepository {
    private final ConfigurationCatalogMapper mapper;

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    @Override
    public List<ConsoleResourceView> resources(int limit) {
        return mapper.resources(limit);
    }

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    @Override
    public ConsoleResourceView resource(String code) {
        return mapper.resource(code);
    }

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    @Override
    public Permission lockPermission(long id, boolean exclusive) {
        return mapper.lockPermission(id, exclusive);
    }

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    @Override
    public void lockOperationsReferences(long permissionId) {
        mapper.lockResourceReferences(permissionId);
        mapper.lockRouteReferences(permissionId);
    }

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    @Override
    public long resourceReferenceCount(long permissionId) {
        return mapper.resourceReferenceCount(permissionId);
    }

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    @Override
    public long routeReferenceCount(long permissionId) {
        return mapper.routeReferenceCount(permissionId);
    }

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    @Override
    public long roleReferenceCount(long permissionId) {
        return mapper.roleReferenceCount(permissionId);
    }

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    @Override
    public List<ResourceReference> resourceReferences(long permissionId, int offset, int size) {
        return mapper.resourceReferences(permissionId, offset, size);
    }

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    @Override
    public List<RouteReference> routeReferences(long permissionId, int offset, int size) {
        return mapper.routeReferences(permissionId, offset, size);
    }

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    @Override
    public List<RoleReference> roleReferences(long permissionId, int offset, int size) {
        return mapper.roleReferences(permissionId, offset, size);
    }

    /** 访问当前目录事实，调用方维护授权和本地事务边界。 */
    @Override
    public List<Permission> delegablePermissions(long memberId, Instant effectiveAt, boolean administrator, boolean includeInactive) {
        return mapper.delegablePermissions(memberId, effectiveAt, administrator, includeInactive);
    }
}
