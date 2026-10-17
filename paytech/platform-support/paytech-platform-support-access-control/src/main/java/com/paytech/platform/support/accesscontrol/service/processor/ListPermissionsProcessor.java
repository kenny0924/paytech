package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.PermissionFilter;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.PermissionSummary;
import com.paytech.platform.support.accesscontrol.domainservice.repository.PermissionRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ListPermissionsRequest;

import lombok.AllArgsConstructor;

/** 读取全局权限目录，列表与总数使用同一筛选。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListPermissionsProcessor
    extends AbstractProcessor<ListPermissionsRequest, PagingResponseBody<PermissionSummary>> {
    private final PermissionRepository permissions;
    private final OperationsManagementPolicyService policy;

    /** 普通配置查询沿用无显式事务边界。 */
    @Override
    protected PagingResponseBody<PermissionSummary> processRequest(ListPermissionsRequest request) {
        policy.requirePermission(request.getActor(), request.getSession(), "PERMISSION_READ");
        var filter = new PermissionFilter(request.getCode(), request.getResourceType(), request.getStatus(),
            request.getRiskLevel(), null);
        var paging = request.getPaging();
        return new PagingResponseBody<>(permissions.permissions(filter, paging.offset(), paging.size()).stream()
            .map(PermissionSummary::new).toList(), paging.page(), paging.size(), permissions.count(filter));
    }
}
