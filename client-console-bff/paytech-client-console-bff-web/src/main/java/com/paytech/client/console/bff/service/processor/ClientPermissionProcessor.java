package com.paytech.client.console.bff.service.processor;

import com.paytech.client.console.bff.domain.enums.ClientBffErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.api.authorization.ClientAuthorizationApi;
import com.paytech.platform.support.api.authorization.request.ClientAuthorizationRequestBody;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 工作区成员权限：每次请求由 Platform 判断（PLT-I02 CLIENT），BFF 不缓存、不从角色推导
 * <li>只在租户上下文可用；账号上下文直接拒绝，不调用 Platform。</li>
 */
@Component
@lombok.AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientPermissionProcessor {

    /** 页面能力名 → Platform 权限码；顺序即返回顺序。 */
    static final Map<String, String> CAPABILITIES = capabilities();

    private final ClientAuthorizationApi platform;
    private final ClientSessionProcessor sessions;

    /** 要求租户上下文并具备指定权限，否则 403。 */
    public void require(HttpServletRequest request, String permissionCode) {
        requireTenant(request);
        var identity = sessions.identity(request);
        var granted = platform.authorize(identity[0], identity[1], new ClientAuthorizationRequestBody(List.of(permissionCode)));
        if (granted == null || granted.getGranted() == null || !granted.getGranted().contains(permissionCode)) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_PERMISSION_DENIED);
        }
    }

    /**
     * 工作区首页的页面能力
     * <li>会话失效照常抛出；其他失败返回空能力，页面只隐藏开发者菜单。</li>
     */
    public List<String> capabilities(String[] identity) {
        try {
            var granted = platform.authorize(identity[0], identity[1],
                new ClientAuthorizationRequestBody(List.copyOf(CAPABILITIES.values())));
            if (granted == null || granted.getGranted() == null) {
                return List.of();
            }
            return CAPABILITIES.entrySet().stream().filter(e -> granted.getGranted().contains(e.getValue()))
                .map(Map.Entry::getKey).toList();
        } catch (FailException failure) {
            if (failure.getErrorCode() == ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID) {
                throw failure;
            }
            return List.of();
        } catch (ErrorException unavailable) {
            return List.of();
        }
    }

    /** 只接受 Gateway 重建的单值租户上下文。 */
    void requireTenant(HttpServletRequest request) {
        var values = Collections.list(request.getHeaders("X-Gateway-Context-Type"));
        if (values.size() != 1 || !"TENANT".equals(values.getFirst())) {
            throw new FailException(ClientBffErrorCode.WORKSPACE_CONTEXT_REQUIRED);
        }
    }

    /** 固定的四项开发者能力。 */
    private static Map<String, String> capabilities() {
        var map = new LinkedHashMap<String, String>();
        map.put("API_CREDENTIAL_READ", "CLIENT_API_CREDENTIAL_READ");
        map.put("API_CREDENTIAL_MANAGE", "CLIENT_API_CREDENTIAL_MANAGE");
        map.put("WEBHOOK_READ", "CLIENT_WEBHOOK_READ");
        map.put("WEBHOOK_MANAGE", "CLIENT_WEBHOOK_MANAGE");
        return Collections.unmodifiableMap(map);
    }
}
