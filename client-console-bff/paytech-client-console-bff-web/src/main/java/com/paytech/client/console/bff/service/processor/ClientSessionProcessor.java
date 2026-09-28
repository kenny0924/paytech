package com.paytech.client.console.bff.service.processor;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.core.ClientRefreshCookie;
import com.paytech.client.console.bff.service.model.ClientBrowserModels.TokenGrantResponseBody;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.authentication.ClientAuthenticationApi;
import com.paytech.platform.support.api.authentication.response.AccountContextResponseBody;
import com.paytech.platform.support.api.authentication.response.AuthenticationResponseBody;
import com.paytech.platform.support.api.authentication.request.LoginRequestBody;
import com.paytech.platform.support.api.authentication.request.RefreshRequestBody;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 无状态 Cookie 与页面响应编排，Platform 独占会话事实与撤销规则。 */
@Component
public class ClientSessionProcessor {
    private static final String COOKIE = "__Secure-client-refresh";
    private final ClientAuthenticationApi platform;

    public ClientSessionProcessor(ClientAuthenticationApi platform) {
        this.platform = platform;
    }

    /** 登录成功后才写 Cookie，不将私网原响应转交浏览器。 */
    public TokenGrantResponseBody login(LoginRequestBody body, HttpServletResponse response) {
        return grant(platform.login(body), response);
    }

    /** 浏览器提交空对象，刷新秘密只来自唯一的 host Cookie。 */
    public TokenGrantResponseBody refresh(HttpServletRequest request, HttpServletResponse response) {
        return grant(platform.refresh(new RefreshRequestBody(refreshCookie(request))), response);
    }

    /** 当前 Access 可信身份与 Cookie 必须由 Platform 确认属于同一会话。 */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String[] identity = identity(request);
        try {
            platform.logout(identity[0], identity[1], new RefreshRequestBody(refreshCookie(request)));
        } catch (FailException failure) {
            if (failure.getErrorCode() != ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID) {
                throw failure;
            }
        }
        clear(response);
    }

    /** 每次回读真实会话状态，不从 JWT 自行推导账号可操作范围。 */
    public AccountContextResponseBody context(HttpServletRequest request) {
        String[] identity = identity(request);
        return platform.context(identity[0], identity[1]);
    }

    private TokenGrantResponseBody grant(AuthenticationResponseBody grant, HttpServletResponse response) {
        if (grant == null || grant.getContext() == null || grant.getAccessToken() == null || grant.getAccessToken().isBlank()
            || grant.getRefreshToken() == null || !grant.getRefreshToken().matches("[A-Za-z0-9_-]{22}\\.[A-Za-z0-9_-]{43}")) {
            throw new ServiceUnavailableException();
        }
        long seconds;
        try {
            seconds =
                Math.min(604800, Duration.between(Instant.now(), Instant.parse(grant.getRefreshExpiresAt())).getSeconds());
        } catch (RuntimeException invalid) {
            throw new ServiceUnavailableException();
        }
        if (seconds <= 0) {
            throw new ServiceUnavailableException();
        }
        response.addHeader("Set-Cookie", cookie(grant.getRefreshToken(), seconds));
        return new TokenGrantResponseBody(grant.getContext(), grant.getAccessToken(), grant.getExpiresAt());
    }

    private String refreshCookie(HttpServletRequest request) {
        var found = request.getCookies() == null ? List.<Cookie>of()
            : Arrays.stream(request.getCookies()).filter(c -> COOKIE.equals(c.getName())).toList();
        if (found.size() != 1 || !found.getFirst().getValue().matches("[A-Za-z0-9_-]{22}\\.[A-Za-z0-9_-]{43}")) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        }
        return found.getFirst().getValue();
    }

    /** 解析 Gateway 唯一重建的 CLIENT 身份；真实会话仍由 Owner 核验。 */
    public String[] identity(HttpServletRequest request) {
        if (!"CUSTOMER".equals(header(request, "X-Gateway-Realm"))
            || !"CLIENT".equals(header(request, "X-Gateway-Channel"))
            || !"PLATFORM_USER".equals(header(request, "X-Gateway-Actor-Type"))
            || !Set.of("ACCOUNT", "TENANT").contains(header(request, "X-Gateway-Context-Type"))) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        }
        String account = header(request, "X-Gateway-Actor-Subject"), session = header(request, "X-Gateway-Session-Id");
        try {
            if (!account.matches("[1-9][0-9]{0,18}") || !session.matches("[1-9][0-9]{0,18}")) {
                throw new NumberFormatException();
            }
            Long.parseLong(account);
            Long.parseLong(session);
        } catch (RuntimeException invalid) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        }
        return new String[] {account, session};
    }

    private String header(HttpServletRequest request, String name) {
        var values = Collections.list(request.getHeaders(name));
        return values.size() == 1 ? values.getFirst() : "";
    }

    private String cookie(String value, long seconds) {
        return ClientRefreshCookie.encode(value, seconds);
    }

    private void clear(HttpServletResponse response) {
        response.addHeader("Set-Cookie", cookie("", 0));
    }
}
