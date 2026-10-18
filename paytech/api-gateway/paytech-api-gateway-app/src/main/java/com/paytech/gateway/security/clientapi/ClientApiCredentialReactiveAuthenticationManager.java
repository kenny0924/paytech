package com.paytech.gateway.security.clientapi;

import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;

import com.paytech.gateway.web.GatewayExchangeAttributes;

import reactor.core.publisher.Mono;

/** 认证只调用一次 PLT-I03，Scope 留到认证后 Guard 之后执行。 */
public final class ClientApiCredentialReactiveAuthenticationManager implements ReactiveAuthenticationManager {
    private final ReactiveClientApiCredentialValidationClient client;

    public ClientApiCredentialReactiveAuthenticationManager(ReactiveClientApiCredentialValidationClient client) {
        this.client = java.util.Objects.requireNonNull(client);
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        if (!(authentication instanceof ClientApiAuthenticationToken token) || token.isAuthenticated()) {
            return Mono.error(new ClientApiCredentialDependencyUnavailableException());
        }
        return Mono.defer(() -> client.validate((String)token.getCredentials()))
            .<Authentication>map(ClientApiAuthenticationToken::authenticated)
            .contextWrite(context -> context.put(GatewayExchangeAttributes.REQUEST_ID, token.requestId()))
            .doFinally(signal -> token.eraseCredentials());
    }
}
