package com.paytech.platform.support.api.authorization;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.platform.support.api.authorization.request.AuthorizationRequestBody;
import com.paytech.platform.support.api.authorization.response.AuthorizationDecisionResponseBody;
import com.paytech.platform.support.api.authorization.response.NavigationResponseBody;

import jakarta.validation.Valid;

/** 当前 Operations 权限与菜单查询；Route Code 只能取自 Gateway 可信上下文。 */
@HttpExchange("/authorization/v1")
public interface OperationsAuthorizationApi {
    /**
     * PLT-I02：解析当前 Route 与 Permission 绑定并校验实时授权。
     *
     * @param accountId
     *            可信账号标识
     * @param sessionId
     *            可信会话标识
     * @param request
     *            包含 Gateway 可信路由代码的请求
     * @return 当前授权决策；明确拒绝返回 allowed=false，身份无效或依赖失败返回统一错误
     */
    @PostExchange("/operations/authorize")
    AuthorizationDecisionResponseBody authorize(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @Valid @RequestBody AuthorizationRequestBody request);

    /**
     * PLT-I21：返回当前权限过滤的 MENU/BUTTON，浏览器不能直接调用。
     *
     * @param accountId
     *            可信账号标识
     * @param sessionId
     *            可信会话标识
     * @return 当前可见导航资源，保持排序且不包含失去可见父节点的后代
     */
    @PostExchange("/console-navigation")
    NavigationResponseBody navigation(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId);
}
