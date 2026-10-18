package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.PlatformOperationsClient;
import com.paytech.operations.console.bff.service.RolePermissionTree;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RoleModels.RolePermissionOptionsResponseBody;

import lombok.AllArgsConstructor;

/**
 * 角色权限选项：组合可授予权限与资源绑定，组装新建角色页的权限树
 * <li>只展示 Platform 返回的可授予范围，不推导或补授权限。</li>
 * <li>请求只有操作人身份，直接以已校验的 {@link OperatorContext} 作为内部请求。</li>
 */
@Component
@AllArgsConstructor
public class RolePermissionOptionsProcessor extends AbstractProcessor<OperatorContext, RolePermissionOptionsResponseBody> {
    private final PlatformOperationsClient client;

    @Override
    protected RolePermissionOptionsResponseBody processRequest(OperatorContext operator) {
        var permissions = client.permissions(operator);
        var resources = client.roleResources(operator);
        return new RolePermissionOptionsResponseBody(RolePermissionTree.assemble(permissions, resources));
    }
}
