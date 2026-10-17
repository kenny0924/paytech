package com.paytech.gateway.security;

/** JWKS 无法在受控边界内取得或验证时的失败关闭信号。 */
public final class JwksUnavailableException extends RuntimeException {
    public JwksUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public JwksUnavailableException(String message) {
        super(message);
    }
}
