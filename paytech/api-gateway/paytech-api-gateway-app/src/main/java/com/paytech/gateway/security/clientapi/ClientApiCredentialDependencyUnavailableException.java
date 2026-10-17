package com.paytech.gateway.security.clientapi;

import org.springframework.security.core.AuthenticationException;

/** 此认证边界不携带可能包含 Credential 或 Owner 响应的原始异常链。 */
public final class ClientApiCredentialDependencyUnavailableException extends AuthenticationException {
    public ClientApiCredentialDependencyUnavailableException() {
        super("Client API Credential 校验暂时不可用");
    }
}
