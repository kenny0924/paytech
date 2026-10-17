package com.paytech.operations.console.bff.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.operations.console.bff.integration.PlatformOperationsClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RoleModels.GrantablePermission;
import com.paytech.operations.console.bff.service.model.RoleModels.RoleDetailsResponseBody;
import com.paytech.operations.console.bff.service.model.RoleModels.RolePermissionOptionsResponseBody;

import lombok.AllArgsConstructor;

/** 组合角色事实、可授予权限及资源展示树；任一上游失败都不返回可提交的部分配置。 @author Codex */
@Component
@AllArgsConstructor
public final class RoleDetailsProcessor {
    private final PlatformOperationsClient client;

    public RoleDetailsResponseBody details(OperatorContext operator, String roleId) {
        var role = client.role(operator, roleId);
        List<GrantablePermission> permissions = client.permissions(operator);
        var nodes = RolePermissionTree.assemble(permissions, client.roleResources(operator));
        Set<String> grantable = new HashSet<>();
        permissions.forEach(permission -> grantable.add(permission.permissionId()));
        if (!roleId.equals(role.roleId()) || !grantable.containsAll(role.permissionIds())) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return new RoleDetailsResponseBody(role.roleId(), role.roleCode(), role.roleName(), role.systemRole(), role.version(),
            role.permissionIds(), nodes);
    }

    public RolePermissionOptionsResponseBody options(OperatorContext operator) {
        var permissions = client.permissions(operator);
        var resources = client.roleResources(operator);
        return new RolePermissionOptionsResponseBody(RolePermissionTree.assemble(permissions, resources));
    }
}
