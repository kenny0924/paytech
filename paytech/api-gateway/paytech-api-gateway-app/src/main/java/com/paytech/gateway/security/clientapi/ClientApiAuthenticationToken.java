package com.paytech.gateway.security.clientapi;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;

/** 原始凭据只在未认证 Token 中短暂存在，认证结果与诊断字符串不保留它。 */
public final class ClientApiAuthenticationToken extends AbstractAuthenticationToken {
    private final ClientApiIdentity identity;
    private final String requestId;
    private String credential;

    public ClientApiAuthenticationToken(String credential, String requestId) {
        super(List.of());
        this.credential = credential;
        this.requestId = requestId;
        this.identity = null;
    }

    private ClientApiAuthenticationToken(ClientApiIdentity identity) {
        super(List.of());
        this.identity = java.util.Objects.requireNonNull(identity);
        this.requestId = null;
        super.setAuthenticated(true);
    }

    public static ClientApiAuthenticationToken authenticated(ClientApiIdentity identity) {
        return new ClientApiAuthenticationToken(identity);
    }

    public String requestId() {
        return requestId;
    }

    @Override
    public Object getCredentials() {
        return credential;
    }

    @Override
    public ClientApiIdentity getPrincipal() {
        return identity;
    }

    @Override
    public void setAuthenticated(boolean authenticated) {
        if (authenticated) {
            throw new IllegalArgumentException("必须从验证身份建立认证结果");
        }
        super.setAuthenticated(false);
    }

    @Override
    public void eraseCredentials() {
        credential = null;
        super.eraseCredentials();
    }

    @Override
    public String toString() {
        return "ClientApiAuthenticationToken[authenticated=" + isAuthenticated() + "]";
    }
}
