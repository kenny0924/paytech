package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.accesscontrol.domain.operations.CreateOperationsRoleContext;
import com.paytech.platform.support.accesscontrol.domain.operations.DeleteOperationsRoleContext;
import com.paytech.platform.support.accesscontrol.domain.operations.UpdateOperationsRoleContext;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsRolePermissionRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsRoleRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsRoleService;

import lombok.AllArgsConstructor;

/** 只编排所属表的保存，不查询资格、不调用其他 Service 或外部依赖。 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class OperationsRoleServiceImpl implements OperationsRoleService {
    private final IdGenerator ids;
    private final OperationsRoleRepository roles;
    private final OperationsRolePermissionRepository permissions;

    /** 创建角色及所有初始权限。 */
    @Override
    public void createRole(CreateOperationsRoleContext context) {
        roles.insert(context.getRole(), context.getTenantId(), context.getNow());
        context.getPermissionIds().forEach(id -> permissions.grant(ids.nextId(), context.getRole().id(), id, context.getNow()));
    }

    /** 角色 CAS 与权限增删同事务完成，已有授权保持不变。 */
    @Override
    public void updateRole(UpdateOperationsRoleContext context) {
        var role = context.getRole();
        roles.update(role, context.getTenantId(), context.getName(), context.getNow());
        context.getPreviousPermissions().stream().filter(id -> !context.getPermissionIds().contains(id))
            .forEach(id -> permissions.revoke(role.id(), id, context.getNow()));
        context.getPermissionIds().stream().filter(id -> !context.getPreviousPermissions().contains(id))
            .forEach(id -> permissions.grant(ids.nextId(), role.id(), id, context.getNow()));
    }

    /** 删除角色并撤销全部当前权限。 */
    @Override
    public void deleteRole(DeleteOperationsRoleContext context) {
        roles.delete(context.getRole(), context.getTenantId(), context.getNow());
        permissions.revokeAll(context.getRole().id(), context.getNow());
    }
}
