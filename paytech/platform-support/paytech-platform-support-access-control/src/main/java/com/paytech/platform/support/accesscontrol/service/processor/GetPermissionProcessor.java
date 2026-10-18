package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.ReferenceCounts;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.PermissionDetailView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ConfigurationCatalogRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.GetPermissionRequest;

import lombok.AllArgsConstructor;

/** 读取权限详情、维护边界与当前引用计数。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetPermissionProcessor extends AbstractProcessor<GetPermissionRequest, PermissionDetailView> {
    private final PermissionManagementSupport support;
    private final ConfigurationCatalogRepository catalog;
    private final OperationsManagementPolicyService policy;

    /** 普通配置查询沿用无显式事务边界。 */
    @Override
    protected PermissionDetailView processRequest(GetPermissionRequest request) {
        policy.requirePermission(request.getActor(), request.getSession(), "PERMISSION_READ");
        var permission = support.requirePermission(request.getPermissionCode());
        long id = permission.permissionId();
        return new PermissionDetailView(permission, support.protection(id), new ReferenceCounts(
            catalog.resourceReferenceCount(id), catalog.routeReferenceCount(id), catalog.roleReferenceCount(id)));
    }
}
