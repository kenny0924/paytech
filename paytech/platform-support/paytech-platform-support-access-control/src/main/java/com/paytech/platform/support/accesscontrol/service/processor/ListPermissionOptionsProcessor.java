package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.PermissionFilter;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.PermissionSummary;
import com.paytech.platform.support.accesscontrol.domain.enums.PermissionStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.PermissionRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ListPermissionOptionsRequest;

import lombok.AllArgsConstructor;

/** 按使用场景重新鉴权，只返回全局 ACTIVE 权限。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListPermissionOptionsProcessor
    extends AbstractProcessor<ListPermissionOptionsRequest, PagingResponseBody<PermissionSummary>> {
    private final PermissionRepository permissions;
    private final OperationsManagementPolicyService policy;

    /** 普通配置查询沿用无显式事务边界。 */
    @Override
    protected PagingResponseBody<PermissionSummary> processRequest(ListPermissionOptionsRequest request) {
        String permission = switch (request.getUsage()) {
            case "GATEWAY_ROUTE" -> "GATEWAY_CONFIG_READ";
            case "CONSOLE_RESOURCE" -> "CONSOLE_RESOURCE_READ";
            default -> throw new com.paytech.foundation.common.error.InvalidParameterException("不支持的权限选择场景");
        };
        policy.requirePermission(request.getActor(), request.getSession(), permission);
        var paging = request.getPaging();
        var filter = new PermissionFilter(null, null, PermissionStatus.ACTIVE, null, request.getKeyword());
        return new PagingResponseBody<>(permissions.permissions(filter, paging.offset(), paging.size()).stream()
            .map(PermissionSummary::new).toList(), paging.page(), paging.size(), permissions.count(filter));
    }
}
