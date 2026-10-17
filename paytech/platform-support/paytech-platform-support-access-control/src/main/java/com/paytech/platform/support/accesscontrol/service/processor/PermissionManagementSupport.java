package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.Permission;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConsoleResourceCapabilities;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConsoleResourcePolicy;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ManagementProtection;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayManagementProtection;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ConfigurationCatalogRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.GatewayConfigurationRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.PermissionRepository;

import lombok.AllArgsConstructor;

/** 权限管理共用的当前事实读取与管理入口保护，不承担基础字段校验。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class PermissionManagementSupport {
    private final PermissionRepository permissions;
    private final ConsoleResourcePolicy resourcePolicy;
    private final ConfigurationCatalogRepository catalog;
    private final GatewayConfigurationRepository gateways;

    /** 不存在返回公共 NOT_FOUND，不泄露其他对象信息。 */
    Permission requirePermission(String code) {
        var permission = permissions.permission(code);
        if (permission == null) throw new FailException(CommonErrorCode.NOT_FOUND);
        return permission;
    }

    /** 排他行锁与绑定、角色授予的共享锁协调；读取锁定后的当前版本。 */
    Permission lock(String code, int expectedVersion) {
        var observed = requirePermission(code);
        var current = permissions.lock(observed.permissionId());
        if (current == null) throw new ConcurrentRequestException("权限配置已被其他操作修改，请刷新后重试");
        current.requireVersion(expectedVersion);
        return current;
    }

    /** 管理 Route 或管理资源及祖先的引用都会形成停用保护。 */
    ManagementProtection protection(long id) {
        boolean gatewayProtected = gateways.loadCurrentConfiguration().routes().stream()
            .anyMatch(r -> r.permissionId() != null && r.permissionId() == id && GatewayManagementProtection.protectedRoute(r));
        var resources = catalog.resources(ConsoleResourceCapabilities.MAX_RESOURCES + 1);
        if (resources.size() > ConsoleResourceCapabilities.MAX_RESOURCES) throw new BizCheckFailException("资源目录超过上限");
        var protectedIds = resourcePolicy.protectedIds(resources);
        boolean resourceProtected = resources.stream().anyMatch(r -> r.getPermissionId() == id && protectedIds.contains(r.getResourceId()));
        return gatewayProtected || resourceProtected ? ManagementProtection.protectedFields("riskLevel")
            : ManagementProtection.editable("riskLevel");
    }
}
