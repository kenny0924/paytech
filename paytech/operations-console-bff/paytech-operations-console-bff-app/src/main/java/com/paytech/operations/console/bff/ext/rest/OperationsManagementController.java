package com.paytech.operations.console.bff.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformOperationsClient;
import com.paytech.operations.console.bff.service.AuditDetailsProcessor;
import com.paytech.operations.console.bff.service.RoleDetailsProcessor;
import com.paytech.operations.console.bff.service.RoleListProcessor;
import com.paytech.operations.console.bff.service.model.AuditModels.AuditEventResponseBody;
import com.paytech.operations.console.bff.service.model.AuditModels.AuditQueryRequestBody;
import com.paytech.operations.console.bff.service.model.ManagementModels.AccountResponseBody;
import com.paytech.operations.console.bff.service.model.ManagementModels.AssignRolesRequestBody;
import com.paytech.operations.console.bff.service.model.ManagementModels.AuditEvent;
import com.paytech.operations.console.bff.service.model.ManagementModels.CreateAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ManagementModels.DeleteAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ManagementModels.DisableAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ManagementModels.EnableAccountRequestBody;
import com.paytech.operations.console.bff.service.model.NavigationModels.NavigationResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RoleModels.CreateRoleRequestBody;
import com.paytech.operations.console.bff.service.model.RoleModels.DeleteRoleRequestBody;
import com.paytech.operations.console.bff.service.model.RoleModels.RoleDetailsResponseBody;
import com.paytech.operations.console.bff.service.model.RoleModels.RoleListItem;
import com.paytech.operations.console.bff.service.model.RoleModels.RolePermissionOptionsResponseBody;
import com.paytech.operations.console.bff.service.model.RoleModels.RoleResponseBody;
import com.paytech.operations.console.bff.service.model.RoleModels.UpdateRoleRequestBody;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** 运营管理 HTTP 入口；授权后的页面查询交给 Processor，简单命令直接委托 Client。 @author Codex */
@RestController
@AllArgsConstructor
public class OperationsManagementController {

    private final RoleListProcessor roleListProcessor;
    private final RoleDetailsProcessor roleDetailsProcessor;
    private final AuditDetailsProcessor auditDetailsProcessor;
    private final PlatformOperationsClient client;

    @GetMapping("/authorization/v1/navigation")
    public NavigationResponseBody navigation(OperatorContext operator) {
        return client.navigation(operator);
    }

    @GetMapping("/iam/v1/accounts")
    public PagingResponseBody<AccountResponseBody> accounts(OperatorContext operator,
        @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.accounts(operator, paging.page(), paging.size());
    }

    @PostMapping("/iam/v1/accounts")
    public AccountResponseBody createAccount(OperatorContext operator, @Valid @RequestBody CreateAccountRequestBody request) {
        return client.createAccount(operator, request);
    }

    @PostMapping("/iam/v1/accounts/{accountId}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableAccount(OperatorContext operator, @PathVariable String accountId,
        @Valid @RequestBody DisableAccountRequestBody request) {
        client.disableAccount(operator, accountId, request);
    }

    @PostMapping("/iam/v1/accounts/{accountId}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enableAccount(OperatorContext operator, @PathVariable String accountId,
        @Valid @RequestBody EnableAccountRequestBody request) {
        client.enableAccount(operator, accountId, request);
    }

    @DeleteMapping("/iam/v1/accounts/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(OperatorContext operator, @PathVariable String accountId,
        @Valid @RequestBody DeleteAccountRequestBody request) {
        client.deleteAccount(operator, accountId, request);
    }

    @PutMapping("/iam/v1/accounts/{accountId}/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignRoles(OperatorContext operator, @PathVariable String accountId,
        @Valid @RequestBody AssignRolesRequestBody request) {
        client.assignRoles(operator, accountId, request);
    }

    @GetMapping("/iam/v1/roles")
    public PagingResponseBody<RoleListItem> roles(OperatorContext operator,
        @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return roleListProcessor.list(operator, paging.page(), paging.size());
    }

    @GetMapping("/iam/v1/role-permission-options")
    public RolePermissionOptionsResponseBody rolePermissionOptions(OperatorContext operator) {
        return roleDetailsProcessor.options(operator);
    }

    @GetMapping("/iam/v1/roles/{roleId}")
    public RoleDetailsResponseBody role(OperatorContext operator, @PathVariable String roleId) {
        return roleDetailsProcessor.details(operator, roleId);
    }

    @PostMapping("/iam/v1/roles")
    public RoleResponseBody createRole(OperatorContext operator, @Valid @RequestBody CreateRoleRequestBody request) {
        return client.createRole(operator, request);
    }

    @PutMapping("/iam/v1/roles/{roleId}")
    public RoleResponseBody updateRole(OperatorContext operator, @PathVariable String roleId,
        @Valid @RequestBody UpdateRoleRequestBody request) {
        return client.updateRole(operator, roleId, request);
    }

    @DeleteMapping("/iam/v1/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(OperatorContext operator, @PathVariable String roleId,
        @Valid @RequestBody DeleteRoleRequestBody request) {
        client.deleteRole(operator, roleId, request);
    }

    @GetMapping("/audit/v1/events")
    public PagingResponseBody<AuditEvent> audit(OperatorContext operator,
        @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
        @Valid @ModelAttribute AuditQueryRequestBody query) {
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.auditEvents(operator, paging.page(), paging.size(), query);
    }

    @GetMapping("/audit/v1/events/{auditEventId}")
    public AuditEventResponseBody auditEvent(OperatorContext operator, @PathVariable String auditEventId) {
        return auditDetailsProcessor.details(operator, auditEventId);
    }
}
