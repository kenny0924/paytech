package com.paytech.client.console.bff.integration.impl;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.integration.PlatformAuthorizationClient;
import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.platform.support.api.authorization.ClientAuthorizationApi;
import com.paytech.platform.support.api.authorization.request.ClientAuthorizationRequestBody;

import lombok.AllArgsConstructor;

/** 调用 Platform CLIENT 授权接口；BFF 不缓存结果，也不从角色推导权限。 */
@Component
@AllArgsConstructor
public class PlatformAuthorizationClientImpl implements PlatformAuthorizationClient {
    private final ClientAuthorizationApi platform;

    /** 实时授权；回执或列表为空视为未授予任何权限。 */
    @Override
    public List<String> grantedPermissions(ClientActor actor, List<String> permissionCodes) {
        var granted = platform.authorize(actor.getAccountId(), actor.getSessionId(), new ClientAuthorizationRequestBody(permissionCodes));
        if (granted == null || granted.getGranted() == null) {
            return List.of();
        }
        return granted.getGranted();
    }
}
