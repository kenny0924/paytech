package com.paytech.platform.support.ext.rest.authentication;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.accesscontrol.service.processor.*;
import com.paytech.platform.support.api.authentication.OperationsAuthenticationApi;
import com.paytech.platform.support.api.authentication.request.ChangePasswordRequestBody;
import com.paytech.platform.support.api.authentication.request.FirstAdministratorRequestBody;
import com.paytech.platform.support.api.authentication.request.OperationsLoginRequestBody;
import com.paytech.platform.support.api.authentication.request.OperationsRefreshRequestBody;
import com.paytech.platform.support.api.authentication.response.AccountCreatedResponseBody;
import com.paytech.platform.support.api.authentication.response.AuthenticationTokensResponseBody;
import com.paytech.platform.support.api.authentication.response.OperationsContextResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;

import lombok.AllArgsConstructor;

/** Operations BFF 内部认证适配器；可信身份由部署边界与实时Session交叉校验。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class OperationsAuthenticationController implements OperationsAuthenticationApi {
    private final OperationsAuthenticationConverter converter;
    private final LoginOperationsProcessor loginOperationsProcessor;
    private final LogoutOperationsProcessor logoutOperationsProcessor;
    private final GetOperationsContextProcessor getOperationsContextProcessor;
    private final ChangeOperationsPasswordProcessor changeOperationsPasswordProcessor;
    private final RefreshOperationsSessionProcessor refreshOperationsSessionProcessor;
    private final CreateFirstOperationsAdministratorProcessor createFirstOperationsAdministratorProcessor;

    /** 适配 Operations 登录。 */
    @Audit(action = "OPERATIONS_LOGIN", resourceType = "USER_ACCOUNT", alternateResourceTypes = "AUTH_SESSION")
    @Override
    public AuthenticationTokensResponseBody login(OperationsLoginRequestBody body) {
        return converter.tokens(loginOperationsProcessor.process(converter.login(body)));
    }

    /** 适配刷新轮换。 */
    @Audit(action = "OPERATIONS_REFRESH", resourceType = "AUTH_SESSION")
    @Override
    public AuthenticationTokensResponseBody refresh(OperationsRefreshRequestBody body) {
        return converter.tokens(refreshOperationsSessionProcessor.process(converter.refresh(body)));
    }

    /** 适配当前会话退出。 */
    @Audit(action = "OPERATIONS_LOGOUT", resourceType = "USER_ACCOUNT", resourceIdParameter = "accountId",
        alternateResourceTypes = "AUTH_SESSION")
    @Override
    public void logout(String accountId, String sessionId, OperationsRefreshRequestBody body) {
        logoutOperationsProcessor.process(converter.logout(accountId, sessionId, body));
    }

    /** 投影当前账号上下文。 */
    @Override
    public OperationsContextResponseBody context(String accountId, String sessionId) {
        return converter.context(getOperationsContextProcessor.process(converter.context(accountId, sessionId)));
    }

    /** 适配本人改密。 */
    @Audit(action = "OPERATIONS_PASSWORD_CHANGE", resourceType = "USER_ACCOUNT", resourceIdParameter = "accountId")
    @Override
    public void changePassword(String accountId, String sessionId, ChangePasswordRequestBody body) {
        changeOperationsPasswordProcessor.process(converter.password(accountId, sessionId, body));
    }

    /** 适配首次管理员交接。 */
    @Audit(action = "OPERATIONS_BOOTSTRAP_COMPLETE", resourceType = "USER_ACCOUNT", resourceIdParameter = "accountId")
    @Override
    public AccountCreatedResponseBody createFirstAdministrator(String accountId, String sessionId,
        FirstAdministratorRequestBody body) {
        return converter.created(
            createFirstOperationsAdministratorProcessor.process(converter.administrator(accountId, sessionId, body)));
    }

}
