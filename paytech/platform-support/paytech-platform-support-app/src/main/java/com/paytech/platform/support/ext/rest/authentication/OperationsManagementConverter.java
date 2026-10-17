package com.paytech.platform.support.ext.rest.authentication;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsAccountView;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsManagementModels;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsRoleView;
import com.paytech.platform.support.accesscontrol.service.processor.request.*;
import com.paytech.platform.support.api.operations.request.*;
import com.paytech.platform.support.api.operations.response.*;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditQueryModels;

/** Operations HTTP 字段转换；不查库、不鉴权、不执行请求幂等。 */
@Component
public class OperationsManagementConverter {
    /** 将 Owner 业务结果转换为稳定 HTTP 字段。 */
    public AccountResponseBody body(OperationsAccountView account) {
        return new AccountResponseBody(account.getAccountId(), account.getEmailMasked(), account.getStatus(),
            account.isPasswordChangeRequired(), account.getLockedUntil(), account.getVersion(), account.getMemberId(),
            account.getMemberVersion(), account.getRoles().stream().map(this::body).toList());
    }

    /** 将 Owner 业务结果转换为稳定 HTTP 字段。 */
    public RoleSummary body(OperationsManagementModels.RoleSummary role) {
        return new RoleSummary(role.getRoleId(), role.getRoleCode(), role.getRoleName(), role.isSystemRole(),
            role.getVersion());
    }

    /** 将 Owner 业务结果转换为稳定 HTTP 字段。 */
    public RoleResponseBody body(OperationsRoleView role) {
        return new RoleResponseBody(role.getRoleId(), role.getRoleCode(), role.getRoleName(), role.isSystemRole(),
            role.getVersion(), role.getPermissionIds());
    }

    /** 将 Owner 业务结果转换为稳定 HTTP 字段。 */
    public RoleEmployeeCount body(OperationsManagementModels.RoleEmployeeCount count) {
        return new RoleEmployeeCount(count.getRoleId(), count.getEmployeeCount());
    }

    /** 将 Owner 业务结果转换为稳定 HTTP 字段。 */
    public PermissionSummary body(OperationsManagementModels.PermissionSummary permission) {
        return new PermissionSummary(permission.getPermissionId(), permission.getPermissionCode(),
            permission.getResourceType(), permission.getActionCode(), permission.getRiskLevel());
    }

    /** 将 Owner 业务结果转换为稳定 HTTP 字段。 */
    public RoleResource body(OperationsManagementModels.RoleResource resource) {
        return new RoleResource(resource.getResourceId(), resource.getResourceCode(), resource.getResourceType(),
            resource.getParentResourceId(), resource.getDisplayName(), resource.getFrontendRoute(),
            resource.getComponentKey(), resource.getPermissionId(), resource.getSortOrder());
    }

    /** 将 Owner 业务结果转换为稳定 HTTP 字段。 */
    public AuditEvent body(OperationsAuditQueryModels.AuditEvent event) {
        return new AuditEvent(event.auditEventId(), event.actorId(), event.actionCode(), event.resourceType(),
            event.resourceId(), event.result(), event.requestId(), event.occurredTime(), event.reason());
    }

    /** 将 Owner 业务结果转换为稳定 HTTP 字段。 */
    public AuditEventResponseBody body(OperationsAuditQueryModels.AuditDetails details) {
        return new AuditEventResponseBody(body(details.event()), details.attributes().stream()
            .map(attribute -> new AuditAttribute(attribute.number(), attribute.key(), attribute.value())).toList());
    }

    /** 解析 accounts 的协议输入。 */
    public ListOperationsAccountsRequest accounts(String actor, String session, Integer page, Integer size) {
        var paging = PagingRequestBody.of(page, size);
        return new ListOperationsAccountsRequest(actor, session, paging.page(), paging.size());
    }

    /** 解析 createAccount 的协议输入。 */
    public CreateOperationsAccountRequest createAccount(String actor, String session, CreateAccountRequestBody body) {
        return new CreateOperationsAccountRequest(actor, session, body.getEmail(), body.getInitialPassword(),
            body.getRoleIds(), body.getReason());
    }

    /** 解析 disableAccount 的协议输入。 */
    public DisableOperationsAccountRequest disableAccount(String actor, String session, String target,
        DisableAccountRequestBody body) {
        return new DisableOperationsAccountRequest(actor, session, parseId(target), body.getExpectedVersion(),
            body.getReason());
    }

    /** 解析 enableAccount 的协议输入。 */
    public EnableOperationsAccountRequest enableAccount(String actor, String session, String target,
        EnableAccountRequestBody body) {
        return new EnableOperationsAccountRequest(actor, session, parseId(target), body.getExpectedVersion(),
            body.getReason());
    }

    /** 解析 deleteAccount 的协议输入。 */
    public DeleteOperationsAccountRequest deleteAccount(String actor, String session, String target,
        DeleteAccountRequestBody body) {
        return new DeleteOperationsAccountRequest(actor, session, parseId(target), body.getExpectedVersion(),
            body.getExpectedMemberVersion(), body.getReason());
    }

    /** 解析 assignRoles 的协议输入。 */
    public AssignOperationsRolesRequest assignRoles(String actor, String session, String target,
        AssignRolesRequestBody body) {
        return new AssignOperationsRolesRequest(actor, session, parseId(target), body.getExpectedMemberVersion(),
            body.getRoleIds(), body.getReason());
    }

    /** 解析 roles 的协议输入。 */
    public ListOperationsRolesRequest roles(String actor, String session, Integer page, Integer size) {
        var paging = PagingRequestBody.of(page, size);
        return new ListOperationsRolesRequest(actor, session, paging.page(), paging.size());
    }

    /** 解析 roleEmployeeCounts 的协议输入。 */
    public QueryOperationsRoleEmployeeCountsRequest roleEmployeeCounts(String actor, String session,
        RoleEmployeeCountsRequestBody body) {
        return new QueryOperationsRoleEmployeeCountsRequest(actor, session, body.getRoleIds());
    }

    /** 解析 role 的协议输入。 */
    public QueryOperationsRoleRequest role(String actor, String session, String roleId) {
        return new QueryOperationsRoleRequest(actor, session, parseId(roleId));
    }

    /** 解析 createRole 的协议输入。 */
    public CreateOperationsRoleRequest createRole(String actor, String session, CreateRoleRequestBody body) {
        return new CreateOperationsRoleRequest(actor, session, body.getRoleCode(), body.getRoleName(),
            body.getPermissionIds(), body.getReason());
    }

    /** 解析 updateRole 的协议输入。 */
    public UpdateOperationsRoleRequest updateRole(String actor, String session, String roleId,
        UpdateRoleRequestBody body) {
        return new UpdateOperationsRoleRequest(actor, session, parseId(roleId), body.getRoleName(),
            body.getPermissionIds(), body.getExpectedVersion(), body.getReason());
    }

    /** 解析 deleteRole 的协议输入。 */
    public DeleteOperationsRoleRequest deleteRole(String actor, String session, String roleId,
        DeleteRoleRequestBody body) {
        return new DeleteOperationsRoleRequest(actor, session, parseId(roleId), body.getExpectedVersion(),
            body.getReason());
    }

    /** 解析 permissions 的协议输入。 */
    public QueryOperationsPermissionsRequest permissions(String actor, String session) {
        return new QueryOperationsPermissionsRequest(actor, session);
    }

    /** 解析 roleResources 的协议输入。 */
    public QueryOperationsRoleResourcesRequest roleResources(String actor, String session) {
        return new QueryOperationsRoleResourcesRequest(actor, session);
    }

    /** 解析 auditEvents 的协议输入。 */
    public ListOperationsAuditEventsRequest auditEvents(String actor, String session, Integer page, Integer size,
        Instant occurredFrom, Instant occurredTo, String actorId, String actionCode, String result, String requestId,
        String resourceType, String resourceId) {
        var paging = PagingRequestBody.of(page, size);
        return new ListOperationsAuditEventsRequest(actor, session, paging.page(), paging.size(), occurredFrom,
            occurredTo, actorId, actionCode, result, requestId, resourceType, resourceId);
    }

    /** 解析 auditEvent 的协议输入。 */
    public QueryOperationsAuditEventRequest auditEvent(String actor, String session, String auditEventId) {
        return new QueryOperationsAuditEventRequest(actor, session, parseId(auditEventId));
    }

    /** 只解析稳定正整数 ID，资源存在性和授权由 Processor 判断。 */
    long parseId(String value) {
        try {
            if (value == null || !value.matches("[0-9]{1,19}")) {
                throw new NumberFormatException();
            }
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException failure) {
            throw new InvalidParameterException("标识必须为正整数");
        }
    }
}
