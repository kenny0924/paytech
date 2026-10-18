package com.paytech.operations.console.bff.integration.impl;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformOperationsClient;
import com.paytech.operations.console.bff.service.model.AuditModels.AuditAttribute;
import com.paytech.operations.console.bff.service.model.AuditModels.AuditEventDetails;
import com.paytech.operations.console.bff.service.model.AuditModels.AuditQueryRequestBody;
import com.paytech.operations.console.bff.service.model.AuthenticationModels.AccountCreatedResponseBody;
import com.paytech.operations.console.bff.service.model.AuthenticationModels.ChangePasswordRequestBody;
import com.paytech.operations.console.bff.service.model.AuthenticationModels.FirstAdministratorRequestBody;
import com.paytech.operations.console.bff.service.model.AuthenticationModels.LoginRequestBody;
import com.paytech.operations.console.bff.service.model.AuthenticationModels.SessionContextResponseBody;
import com.paytech.operations.console.bff.service.model.AuthenticationModels.TokenGrantResponseBody;
import com.paytech.operations.console.bff.service.model.ManagementModels.AccountResponseBody;
import com.paytech.operations.console.bff.service.model.ManagementModels.AssignRolesRequestBody;
import com.paytech.operations.console.bff.service.model.ManagementModels.AuditEvent;
import com.paytech.operations.console.bff.service.model.ManagementModels.CreateAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ManagementModels.DeleteAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ManagementModels.DisableAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ManagementModels.EnableAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ManagementModels.RoleSummary;
import com.paytech.operations.console.bff.service.model.NavigationModels.ConsoleResource;
import com.paytech.operations.console.bff.service.model.NavigationModels.NavigationResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RoleModels.CreateRoleRequestBody;
import com.paytech.operations.console.bff.service.model.RoleModels.DeleteRoleRequestBody;
import com.paytech.operations.console.bff.service.model.RoleModels.GrantablePermission;
import com.paytech.operations.console.bff.service.model.RoleModels.PermissionResource;
import com.paytech.operations.console.bff.service.model.RoleModels.RoleOverview;
import com.paytech.operations.console.bff.service.model.RoleModels.RoleResponseBody;
import com.paytech.operations.console.bff.service.model.RoleModels.UpdateRoleRequestBody;
import com.paytech.platform.support.api.authentication.OperationsAuthenticationApi;
import com.paytech.platform.support.api.authentication.request.OperationsLoginRequestBody;
import com.paytech.platform.support.api.authentication.request.OperationsRefreshRequestBody;
import com.paytech.platform.support.api.authentication.response.AuthenticationTokensResponseBody;
import com.paytech.platform.support.api.authorization.OperationsAuthorizationApi;
import com.paytech.platform.support.api.authorization.request.AuthorizationRequestBody;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.api.operations.OperationsManagementApi;
import static com.paytech.operations.console.bff.service.model.AuthenticationModels.isValidRefreshToken;

import lombok.AllArgsConstructor;

/**
 * Platform DTO 的适配与字段校验；HTTP 异常由公共客户端统一处理。
 *
 * @author Codex
 */
@Component
@AllArgsConstructor
public final class PlatformOperationsClientImpl implements PlatformOperationsClient {
    private static final long MAX_ACCESS_TOKEN_TTL_SECONDS = Duration.ofMinutes(15).toSeconds();
    private static final long MAX_REFRESH_TOKEN_TTL_SECONDS = Duration.ofHours(8).toSeconds();

    private final OperationsManagementApi management;
    private final OperationsAuthorizationApi authorization;
    private final OperationsAuthenticationApi authentication;

    @Override
    public void authorize(OperatorContext context) {
        var decision = authorization.authorize(context.accountId(), context.sessionId(),
            new AuthorizationRequestBody(context.routeCode()));
        if (!decision.isAllowed()) {
            throw new FailException(OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        }
    }

    @Override
    public TokenGrantResponseBody login(LoginRequestBody request) {
        var result = authentication
            .login(new OperationsLoginRequestBody(request.loginIdentifier(), request.password()));
        return mapTokenGrant(result);
    }

    @Override
    public TokenGrantResponseBody refresh(String refreshToken) {
        var result = authentication.refresh(new OperationsRefreshRequestBody(refreshToken));
        return mapTokenGrant(result);
    }

    @Override
    public void logout(OperatorContext context, String refreshToken) {
        authentication.logout(context.accountId(), context.sessionId(),
            new OperationsRefreshRequestBody(refreshToken));
    }

    @Override
    public SessionContextResponseBody context(OperatorContext context) {
        var result = authentication.context(context.accountId(), context.sessionId());
        return new SessionContextResponseBody(result.getAccountId(), result.getSessionId(), result.getAccountType(), result.getEmailMasked(),
            result.getAccountStatus(), result.getTenantId(), result.getMemberId(), result.isPasswordChangeRequired(),
            result.isBootstrapAdministratorRequired(), result.getIntegrationMode());
    }

    @Override
    public void changePassword(OperatorContext context, ChangePasswordRequestBody request) {
        authentication.changePassword(context.accountId(), context.sessionId(),
            new com.paytech.platform.support.api.authentication.request.ChangePasswordRequestBody(request.currentPassword(), request.newPassword()));
    }

    @Override
    public AccountCreatedResponseBody createFirstAdministrator(OperatorContext context, FirstAdministratorRequestBody request) {
        var result = authentication.createFirstAdministrator(context.accountId(), context.sessionId(),
            new com.paytech.platform.support.api.authentication.request.FirstAdministratorRequestBody(request.email(), request.initialPassword()));
        return new AccountCreatedResponseBody(result.getAccountId(), result.getMemberId(), result.getVersion());
    }

    @Override
    public NavigationResponseBody navigation(OperatorContext context) {
        var result = authorization.navigation(context.accountId(), context.sessionId());
        return new NavigationResponseBody(requiredList(result.getResources()).stream()
            .map(this::mapConsoleResource)
            .toList());
    }

    @Override
    public PagingResponseBody<AccountResponseBody> accounts(OperatorContext context, int page, int size) {
        return management.accounts(context.accountId(), context.sessionId(), page, size)
            .map(this::mapAccountSummary);
    }

    @Override
    public PagingResponseBody<RoleOverview> roles(OperatorContext context, int page, int size) {
        return management.roles(context.accountId(), context.sessionId(), page, size)
            .map(this::mapRoleOverview);
    }

    @Override
    public Map<String, Long> roleEmployeeCounts(OperatorContext context, List<String> roleIds) {
        var result = management.roleEmployeeCounts(context.accountId(), context.sessionId(),
            new com.paytech.platform.support.api.operations.request.RoleEmployeeCountsRequestBody(roleIds));
        Map<String, Long> counts = new LinkedHashMap<>();
        for (var item : requiredList(result.getItems())) {
            requireValidResponse(item != null && item.getRoleId() != null && !item.getRoleId().isBlank() && item.getEmployeeCount() >= 0);
            requireValidResponse(counts.putIfAbsent(item.getRoleId(), item.getEmployeeCount()) == null);
        }
        return Map.copyOf(counts);
    }

    @Override
    public RoleResponseBody role(OperatorContext context, String roleId) {
        return mapRole(management.role(context.accountId(), context.sessionId(), roleId));
    }

    @Override
    public List<GrantablePermission> permissions(OperatorContext context) {
        var result = management.permissions(context.accountId(), context.sessionId());
        return requiredList(result.getPermissions()).stream().map(permission -> {
            requireValidResponse(permission != null);
            return new GrantablePermission(permission.getPermissionId(), permission.getPermissionCode());
        }).toList();
    }

    @Override
    public List<PermissionResource> roleResources(OperatorContext context) {
        var result = management.roleResources(context.accountId(), context.sessionId());
        return requiredList(result.getResources()).stream().map(resource -> {
            requireValidResponse(resource != null);
            return new PermissionResource(resource.getResourceId(), resource.getParentResourceId(), resource.getDisplayName(),
                resource.getPermissionId(), resource.getSortOrder());
        }).toList();
    }

    @Override
    public RoleResponseBody createRole(OperatorContext context, CreateRoleRequestBody request) {
        return mapRole(management.createRole(context.accountId(), context.sessionId(),
            new com.paytech.platform.support.api.operations.request.CreateRoleRequestBody(request.roleCode(), request.roleName(), request.permissionIds(), request.reason())));
    }

    @Override
    public RoleResponseBody updateRole(OperatorContext context, String roleId, UpdateRoleRequestBody request) {
        return mapRole(management.updateRole(context.accountId(), context.sessionId(), roleId,
            new com.paytech.platform.support.api.operations.request.UpdateRoleRequestBody(request.roleName(), request.permissionIds(), request.expectedVersion(), request.reason())));
    }

    @Override
    public void deleteRole(OperatorContext context, String roleId, DeleteRoleRequestBody request) {
        management.deleteRole(context.accountId(), context.sessionId(), roleId,
            new com.paytech.platform.support.api.operations.request.DeleteRoleRequestBody(request.expectedVersion(), request.reason()));
    }

    @Override
    public PagingResponseBody<AuditEvent> auditEvents(OperatorContext context, int page, int size, AuditQueryRequestBody query) {
        return management.auditEvents(context.accountId(), context.sessionId(), page, size,
            query.occurredFrom(), query.occurredTo(), query.actorId(), query.actionCode(), query.result(), query.requestId(), query.resourceType(), query.resourceId())
            .map(this::mapAuditEvent);
    }

    @Override
    public AuditEventDetails auditEvent(OperatorContext context, String auditEventId) {
        var result = management.auditEvent(context.accountId(), context.sessionId(), auditEventId);
        return new AuditEventDetails(mapAuditEvent(result.getEvent()), requiredList(result.getAttributes()).stream()
            .map(attribute -> {
                requireValidResponse(attribute != null && attribute.getNumber() > 0
                    && attribute.getKey() != null && !attribute.getKey().isBlank());
                return new AuditAttribute(attribute.getNumber(), attribute.getKey(), attribute.getValue());
            }).toList());
    }

    @Override
    public AccountResponseBody createAccount(OperatorContext context, CreateAccountRequestBody request) {
        var result = management.createAccount(context.accountId(), context.sessionId(),
            new com.paytech.platform.support.api.operations.request.CreateAccountRequestBody(request.email(), request.initialPassword(),
                request.roleIds(), request.reason()));
        return mapAccountSummary(result);
    }

    @Override
    public void disableAccount(OperatorContext context, String accountId, DisableAccountRequestBody request) {
        management.disableAccount(context.accountId(), context.sessionId(), accountId,
            new com.paytech.platform.support.api.operations.request.DisableAccountRequestBody(request.expectedVersion(), request.reason()));
    }

    @Override
    public void enableAccount(OperatorContext context, String accountId, EnableAccountRequestBody request) {
        management.enableAccount(context.accountId(), context.sessionId(), accountId,
            new com.paytech.platform.support.api.operations.request.EnableAccountRequestBody(request.expectedVersion(), request.reason()));
    }

    @Override
    public void deleteAccount(OperatorContext context, String accountId, DeleteAccountRequestBody request) {
        management.deleteAccount(context.accountId(), context.sessionId(), accountId,
            new com.paytech.platform.support.api.operations.request.DeleteAccountRequestBody(request.expectedVersion(), request.expectedMemberVersion(), request.reason()));
    }

    @Override
    public void assignRoles(OperatorContext context, String accountId, AssignRolesRequestBody request) {
        management.assignRoles(context.accountId(), context.sessionId(), accountId,
            new com.paytech.platform.support.api.operations.request.AssignRolesRequestBody(request.expectedMemberVersion(), request.roleIds(),
                request.reason()));
    }

    private TokenGrantResponseBody mapTokenGrant(AuthenticationTokensResponseBody result) {
        requireValidResponse(result.getAccessToken() != null && !result.getAccessToken().isBlank());
        requireValidResponse("Bearer".equals(result.getTokenType())
            && result.getExpiresIn() >= 1 && result.getExpiresIn() <= MAX_ACCESS_TOKEN_TTL_SECONDS
            && result.getRefreshExpiresIn() >= 1 && result.getRefreshExpiresIn() <= MAX_REFRESH_TOKEN_TTL_SECONDS
            && isValidRefreshToken(result.getRefreshToken()));
        return new TokenGrantResponseBody(result.getAccessToken(), result.getTokenType(), result.getExpiresIn(), result.getRefreshToken(),
            result.getRefreshExpiresIn(), result.isPasswordChangeRequired(), result.isBootstrapAdministratorRequired());
    }

    private AccountResponseBody mapAccountSummary(com.paytech.platform.support.api.operations.response.AccountResponseBody result) {
        requireValidResponse(result != null);
        List<RoleSummary> roles = requiredList(result.getRoles()).stream()
            .map(this::mapRoleSummary)
            .toList();
        return new AccountResponseBody(result.getAccountId(), result.getEmailMasked(), result.getStatus(), result.getPasswordChangeRequired(),
            result.getLockedUntil(), result.getVersion(), result.getMemberId(), result.getMemberVersion(), roles);
    }

    private RoleSummary mapRoleSummary(com.paytech.platform.support.api.operations.response.RoleSummary result) {
        requireValidResponse(result != null);
        return new RoleSummary(result.getRoleId(), result.getRoleCode(), result.getRoleName(), result.getSystemRole());
    }

    private RoleOverview mapRoleOverview(com.paytech.platform.support.api.operations.response.RoleSummary result) {
        requireValidResponse(result != null && result.getRoleId() != null && !result.getRoleId().isBlank()
            && result.getRoleCode() != null && !result.getRoleCode().isBlank()
            && result.getRoleName() != null && !result.getRoleName().isBlank() && result.getVersion() > 0);
        return new RoleOverview(result.getRoleId(), result.getRoleCode(), result.getRoleName(), result.getSystemRole(), result.getVersion());
    }

    private RoleResponseBody mapRole(com.paytech.platform.support.api.operations.response.RoleResponseBody result) {
        requireValidResponse(result != null && result.getRoleId() != null && !result.getRoleId().isBlank()
            && result.getRoleCode() != null && !result.getRoleCode().isBlank()
            && result.getRoleName() != null && !result.getRoleName().isBlank() && result.getVersion() > 0);
        List<String> permissionIds = requiredList(result.getPermissionIds());
        requireValidResponse(permissionIds.stream().allMatch(id -> id != null && !id.isBlank())
            && new HashSet<>(permissionIds).size() == permissionIds.size());
        return new RoleResponseBody(result.getRoleId(), result.getRoleCode(), result.getRoleName(), result.getSystemRole(),
            result.getVersion(), List.copyOf(permissionIds));
    }

    private ConsoleResource mapConsoleResource(com.paytech.platform.support.api.authorization.response.ConsoleResource result) {
        requireValidResponse(result != null);
        return new ConsoleResource(result.getResourceId(), result.getResourceCode(), result.getResourceType(), result.getParentResourceId(),
            result.getDisplayName(), result.getFrontendRoute(), result.getComponentKey(), result.getSortOrder());
    }

    private AuditEvent mapAuditEvent(com.paytech.platform.support.api.operations.response.AuditEvent result) {
        requireValidResponse(result != null);
        return new AuditEvent(result.getAuditEventId(), result.getActorId(), result.getActionCode(), result.getResourceType(),
            result.getResourceId(), result.getResult(), result.getRequestId(), result.getOccurredTime(), result.getReason());
    }

    private static <T> List<T> requiredList(List<T> values) {
        requireValidResponse(values != null);
        return values;
    }

    private static void requireValidResponse(boolean condition) {
        if (!condition) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
    }

}
