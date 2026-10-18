package com.paytech.platform.support.api.authorization;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.platform.support.api.authorization.request.ClientAuthorizationRequestBody;
import com.paytech.platform.support.api.authorization.response.ClientAuthorizationResponseBody;

import jakarta.validation.Valid;

/** CLIENT 渠道权限判断（PLT-I02 CLIENT 版）；只供 Client BFF 私网调用，浏览器不能直接访问。 */
@HttpExchange("/authorization/v1/client")
public interface ClientAuthorizationApi {
    /**
     * 判断当前工作区成员被授予了哪些权限码
     * <li>会话必须是有效的 CLIENT 租户上下文；按“成员角色授予 → 角色权限 → 权限状态”实时判定。</li>
     * <li>账号上下文或工作区不可访问返回 403 CLIENT_WORKSPACE_UNAVAILABLE；会话失效返回 401。</li>
     *
     * @param accountId 可信账号标识
     * @param sessionId 可信会话标识
     * @param request 待判断的权限码
     * @return 其中被授予的权限码，保持请求顺序
     */
    @PostExchange("/authorize")
    ClientAuthorizationResponseBody authorize(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @Valid @RequestBody ClientAuthorizationRequestBody request);
}
