package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ChangePermissionStatusContext;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.CreatePermissionContext;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.UpdatePermissionContext;
import com.paytech.platform.support.accesscontrol.domainservice.repository.PermissionRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.PermissionManagementService;

import lombok.AllArgsConstructor;

/** 只保存权限本表，不访问外部能力、其他 Service 或请求幂等。 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class PermissionManagementServiceImpl implements PermissionManagementService {
    private final PermissionRepository permissions;

    @Override
    public void create(CreatePermissionContext context) { permissions.insert(context.getPermission()); }

    @Override
    public void update(UpdatePermissionContext context) {
        permissions.update(context.getPermission(), context.getRiskLevel(), context.getPermission().status(), context.getUpdateTime());
    }

    @Override
    public void changeStatus(ChangePermissionStatusContext context) {
        permissions.update(context.getPermission(), context.getPermission().riskLevel(), context.getStatus(), context.getUpdateTime());
    }
}
