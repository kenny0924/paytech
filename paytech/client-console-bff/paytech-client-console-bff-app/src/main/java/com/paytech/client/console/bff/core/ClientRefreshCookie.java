package com.paytech.client.console.bff.core;

/** CLIENT 的固定外部 Cookie 合同，签发和清理使用完全相同的属性集合。 */
public final class ClientRefreshCookie {
    private ClientRefreshCookie() { }

    /** Max-Age 是唯一有效期属性，避免框架补充 Expires 后被 Gateway 严格校验拒收。 */
    public static String encode(String value, long seconds) {
        if (value == null || seconds < 0 || seconds > 604800
            || (seconds == 0 ? !value.isEmpty() : !value.matches("[A-Za-z0-9_-]{22}\\.[A-Za-z0-9_-]{43}"))) {
            throw new IllegalArgumentException("无效的 Client Refresh Cookie");
        }
        return "__Secure-client-refresh=" + value + "; Path=/api/client/auth/v1; Max-Age=" + seconds
            + "; Secure; HttpOnly; SameSite=Lax";
    }
}
