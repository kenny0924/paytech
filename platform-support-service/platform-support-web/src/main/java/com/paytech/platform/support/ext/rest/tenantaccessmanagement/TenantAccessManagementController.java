package com.paytech.platform.support.ext.rest.tenantaccessmanagement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.service.processor.*;
import com.paytech.platform.support.api.tenantaccessmanagement.TenantAccessManagementApi;
import com.paytech.platform.support.api.tenantaccessmanagement.request.ChangeTenantSecurityStatusRequestBody;
import com.paytech.platform.support.api.tenantaccessmanagement.response.*;
import com.paytech.platform.support.ext.rest.audit.Audit;

import lombok.AllArgsConstructor;

/** 租户访问管理的可信内部协议入口。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class TenantAccessManagementController implements TenantAccessManagementApi {
    private final GetTenantAccessProcessor getTenantAccessProcessor;
    private final GetTenantMemberProcessor getTenantMemberProcessor;
    private final ListTenantAccessProcessor listTenantAccessProcessor;
    private final ListTenantMembersProcessor listTenantMembersProcessor;
    private final ListTenantMemberRolesProcessor listTenantMemberRolesProcessor;
    private final TenantAccessManagementConverter tenantAccessManagementConverter;
    private final ChangeTenantSecurityStatusProcessor changeTenantSecurityStatusProcessor;
    private final ListTenantMemberPermissionsProcessor listTenantMemberPermissionsProcessor;
    private final ListTenantPermissionSourcesProcessor listTenantPermissionSourcesProcessor;

    /** 将协议请求委托给事务入口，控制器不访问数据库。 */
    @Audit(action = "OPERATIONS_TENANT_ACCESS_TENANTS", resourceType = "ACCESS_TENANT", fallbackResourceId = "TENANT_CATALOG", recordSuccess = false)
    @Override public PagingResponseBody<TenantResponseBody> tenants(String actor, String session, Integer page, Integer size, String tenantType, String tenantId, String ownerRefId, String tenantName, String ownerStatus, String securityStatus) {
        return listTenantAccessProcessor.process(tenantAccessManagementConverter.tenants(actor, session, page, size,
            tenantType, tenantId, ownerRefId, tenantName, ownerStatus, securityStatus))
            .map(tenantAccessManagementConverter::tenant);
    }

    /** 将协议请求委托给事务入口，控制器不访问数据库。 */
    @Audit(action = "OPERATIONS_TENANT_ACCESS_TENANT", resourceType = "ACCESS_TENANT", fallbackResourceId = "TENANT_CATALOG", recordSuccess = false, resourceIdParameter = "tenantId")
    @Override public TenantResponseBody tenant(String actor, String session, String tenantId) {
        return tenantAccessManagementConverter
            .tenant(getTenantAccessProcessor.process(tenantAccessManagementConverter.tenant(actor, session, tenantId)));
    }

    /** 将协议请求委托给事务入口，控制器不访问数据库。 */
    @Audit(action = "CHANGE_TENANT_SECURITY_STATUS", resourceType = "ACCESS_TENANT", fallbackResourceId = "TENANT_CATALOG", recordSuccess = true, resourceIdParameter = "tenantId")
    @Override
    public SecurityStatusResponseBody changeSecurityStatus(String actor, String session, String tenantId,
        ChangeTenantSecurityStatusRequestBody request) {
        return tenantAccessManagementConverter.status(changeTenantSecurityStatusProcessor
            .process(tenantAccessManagementConverter.changeSecurityStatus(actor, session, tenantId, request)));
    }

    /** 将协议请求委托给事务入口，控制器不访问数据库。 */
    @Audit(action = "OPERATIONS_TENANT_ACCESS_MEMBERS", resourceType = "ACCESS_TENANT", fallbackResourceId = "TENANT_CATALOG", recordSuccess = false, resourceIdParameter = "tenantId")
    @Override public PagingResponseBody<MemberResponseBody> members(String actor, String session, String tenantId, Integer page, Integer size, String accountId, String emailMasked, String memberType, String memberStatus) {
        return listTenantMembersProcessor.process(tenantAccessManagementConverter.members(actor, session, tenantId,
            page, size, accountId, emailMasked, memberType, memberStatus)).map(tenantAccessManagementConverter::member);
    }

    /** 将协议请求委托给事务入口，控制器不访问数据库。 */
    @Audit(action = "OPERATIONS_TENANT_ACCESS_MEMBER", resourceType = "ACCESS_TENANT", fallbackResourceId = "TENANT_CATALOG", recordSuccess = false, resourceIdParameter = "tenantId")
    @Override public MemberResponseBody member(String actor, String session, String tenantId, String memberId) {
        return tenantAccessManagementConverter.member(getTenantMemberProcessor
            .process(tenantAccessManagementConverter.member(actor, session, tenantId, memberId)));
    }

    /** 将协议请求委托给事务入口，控制器不访问数据库。 */
    @Audit(action = "OPERATIONS_TENANT_ACCESS_ROLES", resourceType = "ACCESS_TENANT", fallbackResourceId = "TENANT_CATALOG", recordSuccess = false, resourceIdParameter = "tenantId")
    @Override public EvaluatedPageResponseBody<RoleGrant> roles(String actor, String session, String tenantId, String memberId, Integer page, Integer size) {
        return tenantAccessManagementConverter.evaluated(
            listTenantMemberRolesProcessor
                .process(tenantAccessManagementConverter.roles(actor, session, tenantId, memberId, page, size)),
            tenantAccessManagementConverter::role);
    }

    /** 将协议请求委托给事务入口，控制器不访问数据库。 */
    @Audit(action = "OPERATIONS_TENANT_ACCESS_PERMISSIONS", resourceType = "ACCESS_TENANT", fallbackResourceId = "TENANT_CATALOG", recordSuccess = false, resourceIdParameter = "tenantId")
    @Override public EvaluatedPageResponseBody<MemberPermission> permissions(String actor, String session, String tenantId, String memberId, Integer page, Integer size) {
        return tenantAccessManagementConverter.evaluated(
            listTenantMemberPermissionsProcessor
                .process(tenantAccessManagementConverter.permissions(actor, session, tenantId, memberId, page, size)),
            tenantAccessManagementConverter::permission);
    }

    /** 将协议请求委托给事务入口，控制器不访问数据库。 */
    @Audit(action = "OPERATIONS_TENANT_ACCESS_SOURCES", resourceType = "ACCESS_TENANT", fallbackResourceId = "TENANT_CATALOG", recordSuccess = false, resourceIdParameter = "tenantId")
    @Override public EvaluatedPageResponseBody<PermissionSource> sources(String actor, String session, String tenantId, String memberId, String permissionId, Integer page, Integer size) {
        return tenantAccessManagementConverter.evaluated(
            listTenantPermissionSourcesProcessor.process(
                tenantAccessManagementConverter.sources(actor, session, tenantId, memberId, permissionId, page, size)),
            tenantAccessManagementConverter::source);
    }
}
