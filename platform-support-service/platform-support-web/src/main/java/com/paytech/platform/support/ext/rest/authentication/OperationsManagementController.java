package com.paytech.platform.support.ext.rest.authentication;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.service.processor.*;
import com.paytech.platform.support.api.operations.OperationsManagementApi;
import com.paytech.platform.support.api.operations.request.*;
import com.paytech.platform.support.api.operations.response.*;
import com.paytech.platform.support.ext.rest.audit.Audit;

import lombok.AllArgsConstructor;

/** Operations人员管理和授权审计查询的内部HTTP适配器。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class OperationsManagementController implements OperationsManagementApi {
    private final ListOperationsRolesProcessor rolesProcessor;
    private final QueryOperationsRoleProcessor roleProcessor;
    private final CreateOperationsRoleProcessor createRoleProcessor;
    private final DeleteOperationsRoleProcessor deleteRoleProcessor;
    private final OperationsManagementConverter converter;
    private final UpdateOperationsRoleProcessor updateRoleProcessor;
    private final AssignOperationsRolesProcessor assignRolesProcessor;
    private final ListOperationsAccountsProcessor accountsProcessor;
    private final CreateOperationsAccountProcessor createAccountProcessor;
    private final DeleteOperationsAccountProcessor deleteAccountProcessor;
    private final EnableOperationsAccountProcessor enableAccountProcessor;
    private final DisableOperationsAccountProcessor disableAccountProcessor;
    private final ListOperationsAuditEventsProcessor auditEventsProcessor;
    private final QueryOperationsAuditEventProcessor auditEventProcessor;
    private final QueryOperationsPermissionsProcessor permissionsProcessor;
    private final QueryOperationsRoleResourcesProcessor roleResourcesProcessor;
    private final QueryOperationsRoleEmployeeCountsProcessor roleEmployeeCountsProcessor;

    @Audit(action = "OPERATIONS_ACCOUNT_LIST", resourceType = "USER_ACCOUNT", resourceIdParameter = "actor",
        recordSuccess = false)
    @Override
    public PagingResponseBody<AccountResponseBody> accounts(String actor, String session, Integer page, Integer size) {
        return accountsProcessor.process(converter.accounts(actor, session, page, size)).map(converter::body);
    }

    @Audit(action = "OPERATIONS_ACCOUNT_CREATE", resourceType = "USER_ACCOUNT", resourceIdParameter = "actor")
    @Override
    public AccountResponseBody createAccount(String actor, String session, CreateAccountRequestBody body) {
        return converter.body(createAccountProcessor.process(converter.createAccount(actor, session, body)));
    }

    @Audit(action = "OPERATIONS_ACCOUNT_DISABLE", resourceType = "USER_ACCOUNT", resourceIdParameter = "target",
        fallbackResourceId = "INVALID_ACCOUNT_ID")
    @Override
    public void disableAccount(String actor, String session, String target,
        DisableAccountRequestBody body) {
        disableAccountProcessor.process(converter.disableAccount(actor, session, target, body));
    }

    @Audit(action = "OPERATIONS_ACCOUNT_ENABLE", resourceType = "USER_ACCOUNT", resourceIdParameter = "target",
        fallbackResourceId = "INVALID_ACCOUNT_ID")
    @Override
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enableAccount(String actor, String session, String target, EnableAccountRequestBody body) {
        enableAccountProcessor.process(converter.enableAccount(actor, session, target, body));
    }

    @Audit(action = "OPERATIONS_ACCOUNT_DELETE", resourceType = "USER_ACCOUNT", resourceIdParameter = "target",
        fallbackResourceId = "INVALID_ACCOUNT_ID")
    @Override
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(String actor, String session, String target, DeleteAccountRequestBody body) {
        deleteAccountProcessor.process(converter.deleteAccount(actor, session, target, body));
    }

    @Audit(action = "OPERATIONS_ACCOUNT_ROLES", resourceType = "USER_ACCOUNT", resourceIdParameter = "target",
        fallbackResourceId = "INVALID_ACCOUNT_ID")
    @Override
    public void assignRoles(String actor, String session, String target, AssignRolesRequestBody body) {
        assignRolesProcessor.process(converter.assignRoles(actor, session, target, body));
    }

    @Audit(action = "OPERATIONS_ROLE_LIST", resourceType = "USER_ACCOUNT", resourceIdParameter = "actor",
        recordSuccess = false)
    @Override
    public PagingResponseBody<RoleSummary> roles(String actor, String session, Integer page, Integer size) {
        return rolesProcessor.process(converter.roles(actor, session, page, size)).map(converter::body);
    }

    @Audit(action = "OPERATIONS_ROLE_EMPLOYEE_COUNTS", resourceType = "ROLE", fallbackResourceId = "OPERATIONS",
        recordSuccess = false)
    @Override
    public RoleEmployeeCountsResponseBody roleEmployeeCounts(String actor, String session,
        RoleEmployeeCountsRequestBody body) {
        return new RoleEmployeeCountsResponseBody(roleEmployeeCountsProcessor
            .process(converter.roleEmployeeCounts(actor, session, body)).stream().map(converter::body).toList());
    }

    @Audit(action = "OPERATIONS_ROLE_DELETE", resourceType = "ROLE", resourceIdParameter = "roleId",
        fallbackResourceId = "INVALID_ROLE_ID")
    @Override
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(String actor, String session, String roleId, DeleteRoleRequestBody body) {
        deleteRoleProcessor.process(converter.deleteRole(actor, session, roleId, body));
    }

    @Audit(action = "OPERATIONS_ROLE_DETAIL", resourceType = "ROLE", resourceIdParameter = "roleId",
        fallbackResourceId = "INVALID_ROLE_ID", recordSuccess = false)
    @Override
    public RoleResponseBody role(String actor, String session, String roleId) {
        return converter.body(roleProcessor.process(converter.role(actor, session, roleId)));
    }

    @Audit(action = "OPERATIONS_ROLE_CREATE", resourceType = "ROLE", fallbackResourceId = "NEW_ROLE")
    @Override
    public RoleResponseBody createRole(String actor, String session, CreateRoleRequestBody body) {
        return converter.body(createRoleProcessor.process(converter.createRole(actor, session, body)));
    }

    @Audit(action = "OPERATIONS_ROLE_UPDATE", resourceType = "ROLE", resourceIdParameter = "roleId",
        fallbackResourceId = "INVALID_ROLE_ID")
    @Override
    public RoleResponseBody updateRole(String actor, String session, String roleId,
        UpdateRoleRequestBody body) {
        return converter.body(updateRoleProcessor.process(converter.updateRole(actor, session, roleId, body)));
    }

    @Audit(action = "OPERATIONS_ROLE_PERMISSIONS", resourceType = "PERMISSION", fallbackResourceId = "OPERATIONS",
        recordSuccess = false)
    @Override
    public PermissionCatalogResponseBody permissions(String actor, String session) {
        return new PermissionCatalogResponseBody(
            permissionsProcessor.process(converter.permissions(actor, session)).stream().map(converter::body).toList());
    }

    @Audit(action = "OPERATIONS_ROLE_RESOURCES", resourceType = "CONSOLE_RESOURCE",
        fallbackResourceId = "OPERATIONS", recordSuccess = false)
    @Override
    public RoleResourcesResponseBody roleResources(String actor, String session) {
        return new RoleResourcesResponseBody(roleResourcesProcessor.process(converter.roleResources(actor, session))
            .stream().map(converter::body).toList());
    }

    @Audit(action = "OPERATIONS_AUDIT_LIST", resourceType = "USER_ACCOUNT", resourceIdParameter = "actor",
        recordSuccess = false)
    @Override
    public PagingResponseBody<AuditEvent> auditEvents(String actor, String session, Integer page, Integer size,
        Instant occurredFrom, Instant occurredTo, String actorId, String actionCode, String result,
        String filterRequestId, String resourceType, String resourceId) {
        return auditEventsProcessor.process(converter.auditEvents(actor, session, page, size, occurredFrom, occurredTo,
            actorId, actionCode, result, filterRequestId, resourceType, resourceId)).map(converter::body);
    }

    @Audit(action = "OPERATIONS_AUDIT_DETAIL", resourceType = "AUDIT_EVENT", resourceIdParameter = "auditEventId",
        fallbackResourceId = "INVALID_AUDIT_EVENT_ID", recordSuccess = false)
    @Override
    public AuditEventResponseBody auditEvent(String actor, String session, String auditEventId) {
        return converter.body(auditEventProcessor.process(converter.auditEvent(actor, session, auditEventId)));
    }

}
