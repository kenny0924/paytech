package com.paytech.client.contract.test.mock.external.onboarding;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.paytech.client.contract.test.mocker.onboarding.OnboardingOwnerMocker;
import com.paytech.platform.support.api.authentication.ClientAuthenticationApi;
import com.paytech.platform.support.api.authentication.request.EmailRequestBody;
import com.paytech.platform.support.api.authentication.request.LinkRequestBody;
import com.paytech.platform.support.api.authentication.request.LoginRequestBody;
import com.paytech.platform.support.api.authentication.request.PasswordResetRequestBody;
import com.paytech.platform.support.api.authentication.request.RefreshRequestBody;
import com.paytech.platform.support.api.authentication.request.RegisterRequestBody;
import com.paytech.platform.support.api.authentication.request.WorkspaceContextRequestBody;
import com.paytech.platform.support.api.authentication.response.AcceptedResponseBody;
import com.paytech.platform.support.api.authentication.response.AccountContextResponseBody;
import com.paytech.platform.support.api.authentication.response.AuthenticationResponseBody;
import com.paytech.platform.support.api.authentication.response.ClientWorkspacesResponseBody;
import com.paytech.platform.support.api.authentication.response.VerificationResponseBody;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileVerificationApi;

/** Platform 会话的测试 HTTP 实现；CCT 只应调用 context，其他方法视为意外调用。 */
@RestController
@TestComponent
public class MockPlatformAuthenticationApi implements ClientAuthenticationApi, WebMvcConfigurer {
    private final OnboardingOwnerMocker mocker;

    /** 与用例共享配置与记录。 */
    public MockPlatformAuthenticationApi(OnboardingOwnerMocker mocker) {
        this.mocker = mocker;
    }

    /** Platform 部署在 /platform-support 上下文路径下；同一前缀只能注册一次，覆盖全部 Platform 测试接口。 */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/platform-support", type -> ClientAuthenticationApi.class.isAssignableFrom(type)
            || OnboardingFileVerificationApi.class.isAssignableFrom(type)
            || com.paytech.platform.support.api.tenantprojection.TenantProjectionApi.class.isAssignableFrom(type));
    }

    /** 会话查询。 */
    @Override
    public AccountContextResponseBody context(String accountId, String sessionId) {
        requestId();
        return required(mocker.context(accountId, sessionId));
    }

    @Override
    public ClientWorkspacesResponseBody workspaces(String accountId, String sessionId) {
        throw unexpected();
    }

    @Override
    public AuthenticationResponseBody selectWorkspace(String accountId, String sessionId, WorkspaceContextRequestBody request) {
        throw unexpected();
    }

    @Override
    public AuthenticationResponseBody selectAccountContext(String accountId, String sessionId, RefreshRequestBody request) {
        throw unexpected();
    }

    @Override
    public AcceptedResponseBody register(RegisterRequestBody request) {
        throw unexpected();
    }

    @Override
    public AcceptedResponseBody requestEmailVerification(EmailRequestBody request) {
        throw unexpected();
    }

    @Override
    public VerificationResponseBody verifyEmail(LinkRequestBody request) {
        throw unexpected();
    }

    @Override
    public AcceptedResponseBody requestPasswordReset(EmailRequestBody request) {
        throw unexpected();
    }

    @Override
    public void resetPassword(PasswordResetRequestBody request) {
        throw unexpected();
    }

    @Override
    public AuthenticationResponseBody login(LoginRequestBody request) {
        throw unexpected();
    }

    @Override
    public AuthenticationResponseBody refresh(RefreshRequestBody request) {
        throw unexpected();
    }

    @Override
    public void logout(String accountId, String sessionId, RefreshRequestBody request) {
        throw unexpected();
    }

    /** 未配置的请求让 Mock 服务记录为意外调用。 */
    static <T> T required(T value) {
        if (value == null) {
            throw unexpected();
        }
        return value;
    }

    /** 意外调用。 */
    static IllegalStateException unexpected() {
        return new IllegalStateException("未配置的 Platform 调用");
    }

    /** 响应 Request ID 与 Mock Problem Body 一致，满足严格 Problem 合同。 */
    static void requestId() {
        var attributes = (ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();
        attributes.getResponse().setHeader("X-Request-Id", "cct-onboarding-test");
    }
}
