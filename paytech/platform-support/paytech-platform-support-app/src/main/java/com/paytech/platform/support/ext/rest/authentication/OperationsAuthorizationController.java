package com.paytech.platform.support.ext.rest.authentication;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.accesscontrol.service.processor.AuthorizeOperationsProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.QueryOperationsNavigationProcessor;
import com.paytech.platform.support.api.authorization.OperationsAuthorizationApi;
import com.paytech.platform.support.api.authorization.request.AuthorizationRequestBody;
import com.paytech.platform.support.api.authorization.response.AuthorizationDecisionResponseBody;
import com.paytech.platform.support.api.authorization.response.NavigationResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;

import lombok.AllArgsConstructor;

/** 当前授权和菜单只委托Platform用例，不相信浏览器角色。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class OperationsAuthorizationController implements OperationsAuthorizationApi {
    private final AuthorizeOperationsProcessor authorizeProcessor;
    private final OperationsAuthorizationConverter converter;
    private final QueryOperationsNavigationProcessor navigationProcessor;

    /** 将可信路由和身份交给模块授权入口，保留明确拒绝审计。 */
    @Audit(action = "OPERATIONS_AUTHORIZATION", resourceType = "USER_ACCOUNT", resourceIdParameter = "accountId",
        recordSuccess = false, alternateResourceTypes = "ROUTE")
    @Override
    public AuthorizationDecisionResponseBody authorize(String accountId, String sessionId,
        AuthorizationRequestBody body) {
        return converter.decision(authorizeProcessor.process(converter.authorize(accountId, sessionId, body)));
    }

    /** 当前普通会话通过后返回权限过滤的导航。 */
    @Audit(action = "OPERATIONS_NAVIGATION", resourceType = "USER_ACCOUNT", resourceIdParameter = "accountId",
        recordSuccess = false)
    @Override
    public NavigationResponseBody navigation(String accountId, String sessionId) {
        return converter.navigation(navigationProcessor.process(converter.navigation(accountId, sessionId)));
    }

}
