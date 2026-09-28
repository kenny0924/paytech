package com.paytech.platform.support.ext.rest.authentication;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAccountView;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.Tokens;
import com.paytech.platform.support.accesscontrol.service.processor.request.*;
import com.paytech.platform.support.api.authentication.request.*;
import com.paytech.platform.support.api.authentication.response.AcceptedResponseBody;
import com.paytech.platform.support.api.authentication.response.AccountContextResponseBody;
import com.paytech.platform.support.api.authentication.response.AuthenticationResponseBody;
import com.paytech.platform.support.api.authentication.response.VerificationResponseBody;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;

/** Client 认证的协议适配：可信 ID、请求字段与 UTC 响应转换。 */
@Component
public class ClientAuthenticationConverter {
    private static final DateTimeFormatter UTC = new DateTimeFormatterBuilder().appendInstant(6).toFormatter();

    /** 注册携带密码，仍由 Owner 执行密码业务策略。 */
    ClientEmailRequest register(RegisterRequestBody body) {
        return new ClientEmailRequest(body.getEmail(), body.getPassword(), null);
    }

    /** 邮件用途由具体入口固定，浏览器不能指定用途。 */
    ClientEmailRequest email(EmailRequestBody body) {
        return new ClientEmailRequest(body.getEmail(), null, null);
    }

    /** 验证邮箱不接收新密码。 */
    ClientLinkRequest verify(LinkRequestBody body) {
        return new ClientLinkRequest(body.getChallengeNo(), body.getSecret(), null, null);
    }

    /** 恢复密码只携带本次链接和新密码。 */
    ClientLinkRequest reset(PasswordResetRequestBody body) {
        return new ClientLinkRequest(body.getChallengeNo(), body.getSecret(), body.getNewPassword(), null);
    }

    /** 登录邮箱不在协议层改写。 */
    ClientLoginRequest login(LoginRequestBody body) {
        return new ClientLoginRequest(body.getEmail(), body.getPassword(), null);
    }

    /** 私网刷新原值只能来自 BFF 的 Cookie 适配。 */
    ClientRefreshRequest refresh(RefreshRequestBody body) {
        return new ClientRefreshRequest(body.getRefreshToken(), null);
    }

    /** 解析可信账号、会话和待核对的 Cookie 原值。 */
    ClientLogoutRequest logout(String accountId, String sessionId, RefreshRequestBody body) {
        return new ClientLogoutRequest(id(accountId), id(sessionId), body.getRefreshToken());
    }

    /** 解析当前账号上下文的可信标识。 */
    ClientContextRequest context(String accountId, String sessionId) {
        return new ClientContextRequest(id(accountId), id(sessionId));
    }

    /** 邮件受理不暴露账号存在性，也不承诺投递成功。 */
    AcceptedResponseBody accepted() {
        return new AcceptedResponseBody(true, 60);
    }

    /** 邮箱验证成功后仍须密码登录。 */
    VerificationResponseBody verified() {
        return new VerificationResponseBody(true, "LOGIN");
    }

    /** 私网签发响应保持现有字段和微秒时间格式。 */
    AuthenticationResponseBody tokens(Tokens tokens) {
        return new AuthenticationResponseBody(account(tokens.context()), tokens.accessToken(),
            UTC.format(tokens.expiresAt()), tokens.refreshToken(), UTC.format(tokens.refreshExpiresAt()));
    }

    /** 只投影当前账号与会话的最小可见事实。 */
    AccountContextResponseBody account(ClientAccountView context) {
        if (context == null) {
            throw new ErrorException("Client 账号上下文未返回结果");
        }
        return new AccountContextResponseBody(Long.toString(context.accountId()), Long.toString(context.sessionId()),
            context.emailMasked(), context.emailVerified(), context.contextType(),
            context.tenantId() == null ? null : context.tenantId().toString(),
            context.memberId() == null ? null : context.memberId().toString(), context.allowedActions());
    }

    /** 可信数字标识必须规范且可被本库 BIGINT 表达。 */
    long id(String value) {
        try {
            if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
                throw new NumberFormatException();
            }
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        }
    }
}
