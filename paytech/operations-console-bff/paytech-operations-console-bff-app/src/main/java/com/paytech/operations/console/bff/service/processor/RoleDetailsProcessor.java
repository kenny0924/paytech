package com.paytech.operations.console.bff.service.processor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.PlatformOperationsClient;
import com.paytech.operations.console.bff.service.RolePermissionTree;
import com.paytech.operations.console.bff.service.model.RoleModels.GrantablePermission;
import com.paytech.operations.console.bff.service.model.RoleModels.RoleDetailsResponseBody;
import com.paytech.operations.console.bff.service.processor.request.RoleDetailsRequest;

import lombok.AllArgsConstructor;

/**
 * 角色详情页：组合角色事实、可授予权限及资源展示树
 * <li>回执角色 ID 不一致，或角色已有权限不在当前可授予范围内，视为上游无效响应。</li>
 * <li>任一上游失败都不返回可提交的部分配置。</li>
 *
 * @author Codex
 */
@Component
@AllArgsConstructor
public class RoleDetailsProcessor extends AbstractProcessor<RoleDetailsRequest, RoleDetailsResponseBody> {
    private final PlatformOperationsClient client;

    @Override
    protected RoleDetailsResponseBody processRequest(RoleDetailsRequest request) {
        var operator = request.getOperator();
        var role = client.role(operator, request.getRoleId());
        List<GrantablePermission> permissions = client.permissions(operator);
        var nodes = RolePermissionTree.assemble(permissions, client.roleResources(operator));
        Set<String> grantable = new HashSet<>();
        permissions.forEach(permission -> grantable.add(permission.permissionId()));
        if (!request.getRoleId().equals(role.roleId()) || !grantable.containsAll(role.permissionIds())) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return new RoleDetailsResponseBody(role.roleId(), role.roleCode(), role.roleName(), role.systemRole(), role.version(),
            role.permissionIds(), nodes);
    }
}
