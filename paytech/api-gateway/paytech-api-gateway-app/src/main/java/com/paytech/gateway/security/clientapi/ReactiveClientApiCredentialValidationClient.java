package com.paytech.gateway.security.clientapi;

import reactor.core.publisher.Mono;

/** Gateway 认证所需的最小本地端口，不暴露 Platform wire DTO。 */
public interface ReactiveClientApiCredentialValidationClient {
    Mono<ClientApiIdentity> validate(String credential);
}
