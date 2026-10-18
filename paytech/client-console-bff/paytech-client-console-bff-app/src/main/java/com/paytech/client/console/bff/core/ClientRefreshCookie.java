package com.paytech.client.console.bff.core;

import java.util.Arrays;
import java.util.List;

import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/** CLIENT 的固定外部 Cookie 合同，签发和清理使用完全相同的属性集合。 */
public final class ClientRefreshCookie {
    /** Cookie 名，带 __Secure- 前缀。 */
    static final String NAME = "__Secure-client-refresh";

    /** Refresh Token 的固定格式。 */
    public static final String TOKEN_PATTERN = "[A-Za-z0-9_-]{22}\\.[A-Za-z0-9_-]{43}";

    private ClientRefreshCookie() { }

    /**
     * 读取唯一的 Refresh Cookie。
     *
     * @param request 当前请求
     * @return Refresh Token；缺失、重复或格式无效时按会话无效拒绝
     */
    public static String read(HttpServletRequest request) {
        List<Cookie> found = request.getCookies() == null ? List.of()
            : Arrays.stream(request.getCookies()).filter(cookie -> NAME.equals(cookie.getName())).toList();
        if (found.size() != 1 || !found.getFirst().getValue().matches(TOKEN_PATTERN)) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        }
        return found.getFirst().getValue();
    }

    /** Max-Age 是唯一有效期属性，避免框架补充 Expires 后被 Gateway 严格校验拒收。 */
    public static String encode(String value, long seconds) {
        if (value == null || seconds < 0 || seconds > 604800
            || (seconds == 0 ? !value.isEmpty() : !value.matches(TOKEN_PATTERN))) {
            throw new IllegalArgumentException("无效的 Client Refresh Cookie");
        }
        return NAME + "=" + value + "; Path=/api/client/auth/v1; Max-Age=" + seconds
            + "; Secure; HttpOnly; SameSite=Lax";
    }
}
