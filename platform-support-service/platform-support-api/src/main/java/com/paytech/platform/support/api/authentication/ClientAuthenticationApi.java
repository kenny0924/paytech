package com.paytech.platform.support.api.authentication;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.platform.support.api.authentication.request.*;
import com.paytech.platform.support.api.authentication.response.AcceptedResponseBody;
import com.paytech.platform.support.api.authentication.response.AccountContextResponseBody;
import com.paytech.platform.support.api.authentication.response.AuthenticationResponseBody;
import com.paytech.platform.support.api.authentication.response.VerificationResponseBody;

import jakarta.validation.Valid;

/** Client BFF 调用的私网邮箱认证合同，公共 URL 和 Cookie 由 BFF 管理。 */
@HttpExchange("/authentication/v1/client")
public interface ClientAuthenticationApi {
    /**
     * 创建未验证账号并申请验证邮件；公开响应不暴露账号存在性。
     *
     * @param request
     *            认证请求；秘密字段禁止日志
     * @return 固定受理结果，HTTP 202
     */
    @PostExchange("/register")
    AcceptedResponseBody register(@Valid @RequestBody RegisterRequestBody request);

    /**
     * 重发会使原邮件链接失效，且继续遵守服务器冷却。
     *
     * @param request
     *            认证请求；秘密字段禁止日志
     * @return 固定受理结果，HTTP 202
     */
    @PostExchange("/email-verification-requests")
    AcceptedResponseBody requestEmailVerification(@Valid @RequestBody EmailRequestBody request);

    /**
     * POST 单次消费验证链接；GET 页面不改变账号，不自动登录。
     *
     * @param request
     *            认证请求；秘密字段禁止日志
     * @return 邮箱验证结果，不签发会话
     */
    @PostExchange("/email-verifications")
    VerificationResponseBody verifyEmail(@Valid @RequestBody LinkRequestBody request);

    /**
     * 密码恢复邮件请求与未知账号保持同样受理响应。
     *
     * @param request
     *            认证请求；秘密字段禁止日志
     * @return 固定受理结果，HTTP 202
     */
    @PostExchange("/password-reset-requests")
    AcceptedResponseBody requestPasswordReset(@Valid @RequestBody EmailRequestBody request);

    /**
     * 消费恢复链接并撤销该 CUSTOMER 账号的旧会话。
     *
     * @param request
     *            认证请求；秘密字段禁止日志
     */
    @PostExchange("/password-resets")
    void resetPassword(@Valid @RequestBody PasswordResetRequestBody request);

    /**
     * 邮箱和密码登录，返回仅供私网 BFF 使用的签发结果。
     *
     * @param request
     *            认证请求；秘密字段禁止日志
     * @return 仅供私网 BFF 使用的签发结果
     */
    @PostExchange("/login")
    AuthenticationResponseBody login(@Valid @RequestBody LoginRequestBody request);

    /**
     * BFF 从 HttpOnly Cookie 取出原值，单次轮换刷新代。
     *
     * @param request
     *            认证请求；秘密字段禁止日志
     * @return 轮换后的签发结果
     */
    @PostExchange("/refresh")
    AuthenticationResponseBody refresh(@Valid @RequestBody RefreshRequestBody request);

    /**
     * 核对可信上下文和 Cookie 属于同一会话，撤销当前 CLIENT 会话。
     *
     * @param accountId
     *            可信账号编号
     * @param sessionId
     *            可信会话编号
     * @param request
     *            认证请求；秘密字段禁止日志
     */
    @PostExchange("/logout")
    void logout(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @Valid @RequestBody RefreshRequestBody request);

    /**
     * 查询当前账号上下文；服务端重新核对状态及安全纪元。
     *
     * @param accountId
     *            可信账号编号
     * @param sessionId
     *            可信会话编号
     * @return 当前有效账号上下文
     */
    @GetExchange("/context")
    AccountContextResponseBody context(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId);
}
