package com.paytech.foundation.internalauth.contract;

/**
 * Platform 签发的内部调用 Token。
 *
 * @author Claude
 */
public class InternalTokenIssueResponse {

    /**
     * ES256 JWS Compact Token。
     */
    private String token;

    /**
     * 过期时间。
     * <li>UTC ISO-8601，例如 {@code 2026-10-17T10:05:00Z}；与 Token {@code exp} 一致。</li>
     */
    private String expiresAt;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }
}
