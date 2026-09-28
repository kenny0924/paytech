package com.paytech.platform.support.ext.rest.authentication;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.paytech.platform.support.api.authentication.ClientAuthenticationApi;
import com.paytech.platform.support.api.authentication.request.PasswordResetRequestBody;
import com.paytech.platform.support.api.authentication.request.RegisterRequestBody;
import com.paytech.platform.support.api.authentication.request.EmailRequestBody;
import com.paytech.platform.support.api.authentication.request.RefreshRequestBody;
import com.paytech.platform.support.api.authentication.request.LoginRequestBody;
import com.paytech.platform.support.api.authentication.request.LinkRequestBody;
import com.paytech.platform.support.api.authentication.response.VerificationResponseBody;
import com.paytech.platform.support.api.authentication.response.AcceptedResponseBody;
import com.paytech.platform.support.api.authentication.response.AccountContextResponseBody;
import com.paytech.platform.support.api.authentication.response.AuthenticationResponseBody;
import com.paytech.platform.support.accesscontrol.service.processor.RegisterClientProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.LogoutClientProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.RequestClientPasswordResetProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.GetClientAccountContextProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.LoginClientProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.ResetClientPasswordProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.RequestClientEmailVerificationProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.RefreshClientSessionProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.VerifyClientEmailProcessor;
import com.paytech.platform.support.ext.rest.audit.Audit;
import lombok.AllArgsConstructor;

/** 私网 Client 认证入口；协议转换后委托各 Owner 用例。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ClientAuthenticationController implements ClientAuthenticationApi {
    private final ClientAuthenticationConverter converter;
    private final LoginClientProcessor loginClientProcessor;
    private final LogoutClientProcessor logoutClientProcessor;
    private final RegisterClientProcessor registerClientProcessor;
    private final VerifyClientEmailProcessor verifyClientEmailProcessor;
    private final ResetClientPasswordProcessor resetClientPasswordProcessor;
    private final RefreshClientSessionProcessor refreshClientSessionProcessor;
    private final GetClientAccountContextProcessor getClientAccountContextProcessor;
    private final RequestClientPasswordResetProcessor requestClientPasswordResetProcessor;
    private final RequestClientEmailVerificationProcessor requestClientEmailVerificationProcessor;

    /** 适配 register 的认证协议及响应状态。 */
    @Override
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Audit(action = "CLIENT_REGISTER", resourceType = "USER_ACCOUNT")
    public AcceptedResponseBody register(RegisterRequestBody request) {
        registerClientProcessor.process(converter.register(request));
        return converter.accepted();
    }

    /** 适配 requestEmailVerification 的认证协议及响应状态。 */
    @Override
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Audit(action = "CLIENT_EMAIL_VERIFICATION_REQUEST", resourceType = "USER_ACCOUNT")
    public AcceptedResponseBody requestEmailVerification(EmailRequestBody request) {
        requestClientEmailVerificationProcessor.process(converter.email(request));
        return converter.accepted();
    }

    /** 适配 verifyEmail 的认证协议及响应状态。 */
    @Override
    @Audit(action = "CLIENT_EMAIL_VERIFY", resourceType = "USER_ACCOUNT")
    public VerificationResponseBody verifyEmail(LinkRequestBody request) {
        verifyClientEmailProcessor.process(converter.verify(request));
        return converter.verified();
    }

    /** 适配 requestPasswordReset 的认证协议及响应状态。 */
    @Override
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Audit(action = "CLIENT_PASSWORD_RESET_REQUEST", resourceType = "USER_ACCOUNT")
    public AcceptedResponseBody requestPasswordReset(EmailRequestBody request) {
        requestClientPasswordResetProcessor.process(converter.email(request));
        return converter.accepted();
    }

    /** 适配 resetPassword 的认证协议及响应状态。 */
    @Override
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Audit(action = "CLIENT_PASSWORD_RESET", resourceType = "USER_ACCOUNT")
    public void resetPassword(PasswordResetRequestBody request) {
        resetClientPasswordProcessor.process(converter.reset(request));
    }

    /** 适配 login 的认证协议及响应状态。 */
    @Override
    @Audit(action = "CLIENT_LOGIN", resourceType = "AUTH_SESSION", alternateResourceTypes = {"USER_ACCOUNT"})
    public AuthenticationResponseBody login(LoginRequestBody request) {
        return converter.tokens(loginClientProcessor.process(converter.login(request)));
    }

    /** 适配 refresh 的认证协议及响应状态。 */
    @Override
    @Audit(action = "CLIENT_REFRESH", resourceType = "AUTH_SESSION")
    public AuthenticationResponseBody refresh(RefreshRequestBody request) {
        return converter.tokens(refreshClientSessionProcessor.process(converter.refresh(request)));
    }

    /** 适配 logout 的认证协议及响应状态。 */
    @Override
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Audit(action = "CLIENT_LOGOUT", resourceType = "AUTH_SESSION")
    public void logout(String accountId, String sessionId, RefreshRequestBody request) {
        logoutClientProcessor.process(converter.logout(accountId, sessionId, request));
    }

    /** 适配 context 的认证协议及响应状态。 */
    @Override
    public AccountContextResponseBody context(String accountId, String sessionId) {
        return converter.account(getClientAccountContextProcessor.process(converter.context(accountId, sessionId)));
    }
}
