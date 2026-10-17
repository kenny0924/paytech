package com.paytech.operations.console.bff.service.model;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 角色命令与权限配置页面模型；权限事实和可授予范围由 Platform 决定。 @author Codex */
public final class RoleModels {
    private RoleModels() {
    }

    public record CreateRoleRequestBody(@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String roleCode,
                                       @NotBlank @Size(max = 255) String roleName,
                                       @NotNull List<@NotBlank String> permissionIds,
                                       @NotBlank @Size(max = 500) String reason) {
    }

    public record UpdateRoleRequestBody(@NotBlank @Size(max = 255) String roleName,
                                       @NotNull List<@NotBlank String> permissionIds,
                                       @Positive int expectedVersion,
                                       @NotBlank @Size(max = 500) String reason) {
    }

    public record DeleteRoleRequestBody(@Positive int expectedVersion, @NotBlank @Size(max = 500) String reason) {
    }

    /** Platform 角色列表事实，员工人数由独立批量查询提供。 */
    public record RoleOverview(String roleId, String roleCode, String roleName, boolean systemRole, int version) {
    }

    public record RoleListItem(String roleId, String roleCode, String roleName, boolean systemRole,
                               int version, long employeeCount) {
    }

    public record RoleResponseBody(String roleId, String roleCode, String roleName, boolean systemRole,
                                   int version, List<String> permissionIds) {
    }

    public record RoleDetailsResponseBody(String roleId, String roleCode, String roleName, boolean systemRole,
                                          int version, List<String> permissionIds, List<PermissionNode> nodes) {
    }

    public record RolePermissionOptionsResponseBody(List<PermissionNode> nodes) {
    }

    public record PermissionNode(String nodeId, String displayName, String permissionId, List<PermissionNode> children) {
    }

    public record GrantablePermission(String permissionId, String permissionCode) {
    }

    public record PermissionResource(String resourceId, String parentResourceId, String displayName,
                                     String permissionId, int sortOrder) {
    }
}
