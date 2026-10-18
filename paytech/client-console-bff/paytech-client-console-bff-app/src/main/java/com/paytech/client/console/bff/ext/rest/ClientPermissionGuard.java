package com.paytech.client.console.bff.ext.rest;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.domain.enums.ClientBffErrorCode;
import com.paytech.client.console.bff.integration.PlatformAuthorizationClient;
import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;

import lombok.AllArgsConstructor;

/**
 * 工作区接口的入站权限检查：每次请求由 Platform 判断（PLT-I02 CLIENT），BFF 不缓存、不从角色推导
 * <li>只在租户上下文可用；账号上下文直接拒绝，不调用 Platform。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientPermissionGuard {
    private final PlatformAuthorizationClient authorization;

    /** 要求租户上下文并具备指定权限，否则 403。 */
    public void require(ClientActor actor, String permissionCode) {
        if (!actor.isTenantContext()) {
            throw new FailException(ClientBffErrorCode.WORKSPACE_CONTEXT_REQUIRED);
        }
        if (!authorization.grantedPermissions(actor, List.of(permissionCode)).contains(permissionCode)) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_PERMISSION_DENIED);
        }
    }
}
