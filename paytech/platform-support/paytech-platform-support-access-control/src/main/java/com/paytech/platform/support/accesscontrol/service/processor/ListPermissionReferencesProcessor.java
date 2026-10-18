package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.Reference;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ConfigurationCatalogRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ListPermissionReferencesRequest;

import lombok.AllArgsConstructor;

/** 按单一引用种类分页，只读取配置关联，不展开人员。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListPermissionReferencesProcessor
    extends AbstractProcessor<ListPermissionReferencesRequest, PagingResponseBody<Reference>> {
    private final PermissionManagementSupport support;
    private final ConfigurationCatalogRepository catalog;
    private final OperationsManagementPolicyService policy;

    /** 普通配置查询沿用无显式事务边界。 */
    @Override
    protected PagingResponseBody<Reference> processRequest(ListPermissionReferencesRequest request) {
        policy.requirePermission(request.getActor(), request.getSession(), "PERMISSION_READ");
        long id = support.requirePermission(request.getPermissionCode()).permissionId();
        var paging = request.getPaging();
        List<? extends Reference> items = switch (request.getKind()) {
            case RESOURCE -> catalog.resourceReferences(id, paging.offset(), paging.size());
            case ROUTE -> catalog.routeReferences(id, paging.offset(), paging.size());
            case ROLE -> catalog.roleReferences(id, paging.offset(), paging.size());
        };
        long total = switch (request.getKind()) {
            case RESOURCE -> catalog.resourceReferenceCount(id);
            case ROUTE -> catalog.routeReferenceCount(id);
            case ROLE -> catalog.roleReferenceCount(id);
        };
        return new PagingResponseBody<>(java.util.List.copyOf(items), paging.page(), paging.size(), total);
    }
}
