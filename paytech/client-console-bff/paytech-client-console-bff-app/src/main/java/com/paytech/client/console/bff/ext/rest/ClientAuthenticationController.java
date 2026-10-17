package com.paytech.client.console.bff.ext.rest;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.paytech.client.console.bff.service.model.ClientBrowserModels.TokenGrantResponseBody;
import com.paytech.client.console.bff.service.model.ClientBrowserModels.WorkspaceSelectRequestBody;
import com.paytech.client.console.bff.service.processor.ClientSessionProcessor;
import com.paytech.platform.support.api.authentication.ClientAuthenticationApi;
import com.paytech.platform.support.api.authentication.request.*;
import com.paytech.platform.support.api.authentication.response.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Client 浏览器认证与上下文切换操作，公开路由由 Gateway 显式注册。 */
@RestController
@RequestMapping("/client-auth/v1")
public class ClientAuthenticationController {
    private final ClientAuthenticationApi platform;
    private final ClientSessionProcessor sessions;

    public ClientAuthenticationController(ClientAuthenticationApi platform, ClientSessionProcessor sessions) {
        this.platform = platform;
        this.sessions = sessions;
    }

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

    /** 登录只向浏览器返回 Access Token 投影。 */
    @PostMapping("/login")
    public TokenGrantResponseBody login(@Valid @RequestBody LoginRequestBody body, HttpServletResponse response) {
        return sessions.login(body, response);
    }

    /** 刷新请求不能自行指定 Token 或目标账号。 */
    @PostMapping("/refresh")
    public TokenGrantResponseBody refresh(@RequestBody @Size(max = 0) Map<String, Object> body,
        HttpServletRequest request, HttpServletResponse response) {
        return sessions.refresh(request, response);
    }

    /** 退出成功或会话已经失效时删除同范围 Cookie。 */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody @Size(max = 0) Map<String, Object> body, HttpServletRequest request,
        HttpServletResponse response) {
        sessions.logout(request, response);
    }

    /** C02 进入工作区：浏览器只提交成员 ID，归属与状态由 Platform 判定。 */
    @PostMapping("/workspace-context")
    public TokenGrantResponseBody selectWorkspace(@Valid @RequestBody WorkspaceSelectRequestBody body,
        HttpServletRequest request, HttpServletResponse response) {
        return sessions.selectWorkspace(request, response, body.memberId());
    }

    /** W01 返回账号上下文。 */
    @PostMapping("/account-context")
    public TokenGrantResponseBody selectAccountContext(@RequestBody @Size(max = 0) Map<String, Object> body,
        HttpServletRequest request, HttpServletResponse response) {
        return sessions.selectAccountContext(request, response);
    }

    /** 当前状态来自 Platform 实时回读。 */
    @GetMapping("/context")
    public AccountContextResponseBody context(HttpServletRequest request) {
        return sessions.context(request);
    }
}
