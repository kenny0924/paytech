package com.paytech.client.console.bff.ext.rest;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.console.bff.core.ClientRefreshCookie;
import com.paytech.client.console.bff.integration.PlatformAuthenticationClient;
import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.client.console.bff.service.model.response.TokenGrantResponseBody;
import com.paytech.client.console.bff.service.model.request.WorkspaceSelectRequestBody;
import com.paytech.client.console.bff.service.model.SessionGrant;
import com.paytech.platform.support.api.authentication.ClientAuthenticationApi;
import com.paytech.platform.support.api.authentication.request.EmailRequestBody;
import com.paytech.platform.support.api.authentication.request.LinkRequestBody;
import com.paytech.platform.support.api.authentication.request.LoginRequestBody;
import com.paytech.platform.support.api.authentication.request.PasswordResetRequestBody;
import com.paytech.platform.support.api.authentication.request.RegisterRequestBody;
import com.paytech.platform.support.api.authentication.response.AcceptedResponseBody;
import com.paytech.platform.support.api.authentication.response.AccountContextResponseBody;
import com.paytech.platform.support.api.authentication.response.VerificationResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;

/**
 * Client 浏览器认证与上下文切换操作，公开路由由 Gateway 显式注册
 * <li>注册、邮箱验证与密码恢复直接转发 Platform；会话签发经 {@link PlatformAuthenticationClient} 校验后写入 Refresh Cookie。</li>
 * <li>Refresh Token 只经唯一 host Cookie 往返，浏览器 JSON 只得到 Access Token 投影。</li>
 */
@RestController
@AllArgsConstructor
@RequestMapping("/client-auth/v1")
public class ClientAuthenticationController {
    private final ClientAuthenticationApi platform;
    private final PlatformAuthenticationClient sessions;

    /** 请求验证邮件，未知与已存在账号统一受理。 */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AcceptedResponseBody register(@Valid @RequestBody RegisterRequestBody body) {
        return platform.register(body);
    }

    /** 新链接替换旧链接，冷却由 Platform 执行。 */
    @PostMapping("/email-verification-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AcceptedResponseBody resend(@Valid @RequestBody EmailRequestBody body) {
        return platform.requestEmailVerification(body);
    }

    /** POST 才消费邮件证明，成功后仍须密码登录。 */
    @PostMapping("/email-verifications")
    public VerificationResponseBody verify(@Valid @RequestBody LinkRequestBody body) {
        return platform.verifyEmail(body);
    }

    /** 密码恢复邮件不披露账号是否存在。 */
    @PostMapping("/password-reset-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AcceptedResponseBody requestReset(@Valid @RequestBody EmailRequestBody body) {
        return platform.requestPasswordReset(body);
    }

    /** 恢复密码由 Platform 原子撤销旧会话。 */
    @PostMapping("/password-resets")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody PasswordResetRequestBody body) {
        platform.resetPassword(body);
    }

    /** 登录成功后才写 Cookie，只向浏览器返回 Access Token 投影。 */
    @PostMapping("/login")
    public TokenGrantResponseBody login(@Valid @RequestBody LoginRequestBody body, HttpServletResponse response) {
        return issue(sessions.login(body), response);
    }

    /** 刷新请求不能自行指定 Token 或目标账号；刷新秘密只来自唯一的 host Cookie。 */
    @PostMapping("/refresh")
    public TokenGrantResponseBody refresh(@RequestBody @Size(max = 0) Map<String, Object> body,
        HttpServletRequest request, HttpServletResponse response) {
        return issue(sessions.refresh(ClientRefreshCookie.read(request)), response);
    }

    /** 当前 Access 与 Cookie 由 Platform 确认属于同一会话；退出成功或会话已经失效时删除同范围 Cookie。 */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody @Size(max = 0) Map<String, Object> body, HttpServletRequest request,
        HttpServletResponse response, ClientActor actor) {
        sessions.logout(actor, ClientRefreshCookie.read(request));
        response.addHeader("Set-Cookie", ClientRefreshCookie.encode("", 0));
    }

    /** C02 进入工作区：浏览器只提交成员 ID，归属与状态由 Platform 判定，成功后替换 Cookie。 */
    @PostMapping("/workspace-context")
    public TokenGrantResponseBody selectWorkspace(@Valid @RequestBody WorkspaceSelectRequestBody body,
        HttpServletRequest request, HttpServletResponse response, ClientActor actor) {
        return issue(sessions.selectWorkspace(actor, ClientRefreshCookie.read(request), body.getMemberId()), response);
    }

    /** W01 返回账号上下文，同样新建会话并替换 Cookie。 */
    @PostMapping("/account-context")
    public TokenGrantResponseBody selectAccountContext(@RequestBody @Size(max = 0) Map<String, Object> body,
        HttpServletRequest request, HttpServletResponse response, ClientActor actor) {
        return issue(sessions.selectAccountContext(actor, ClientRefreshCookie.read(request)), response);
    }

    /** 当前状态来自 Platform 实时回读。 */
    @GetMapping("/context")
    public AccountContextResponseBody context(ClientActor actor) {
        return sessions.context(actor);
    }

    /** 写入 Refresh Cookie 并返回浏览器投影；私网原响应不转交浏览器。 */
    static TokenGrantResponseBody issue(SessionGrant grant, HttpServletResponse response) {
        response.addHeader("Set-Cookie", ClientRefreshCookie.encode(grant.getRefreshToken(), grant.getRefreshMaxAgeSeconds()));
        return new TokenGrantResponseBody(grant.getContext(), grant.getAccessToken(), grant.getExpiresAt());
    }
}
