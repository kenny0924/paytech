package com.paytech.gateway.security.clientapi;

import java.time.Duration;
import java.util.Set;

import org.springframework.security.authentication.BadCredentialsException;

import com.paytech.platform.support.api.apicredential.ApiCredentialConstraints;
import com.paytech.platform.support.api.apicredential.ApiCredentialValidationApi;
import com.paytech.platform.support.api.apicredential.response.ValidatedCredentialResponseBody;

import reactor.core.publisher.Mono;

/** 调用 Owner reactive HTTP 合同；零缓存、零重试且异常不保留认证材料。 */
public final class PlatformClientApiCredentialValidationClient implements ReactiveClientApiCredentialValidationClient {
    private final ApiCredentialValidationApi api;
    private final Duration timeout;

    public PlatformClientApiCredentialValidationClient(ApiCredentialValidationApi api, Duration timeout) {
        this.api = java.util.Objects.requireNonNull(api);
        this.timeout = java.util.Objects.requireNonNull(timeout);
    }

    @Override
    public Mono<ClientApiIdentity> validate(String credential) {
        return Mono.defer(() -> {
            if (!ApiCredentialConstraints.isValidCredential(credential)) {
                return Mono.error(new BadCredentialsException("未通过身份验证"));
            }
            return api.validate(credential).map(this::identity)
                .switchIfEmpty(Mono.error(new ClientApiCredentialDependencyUnavailableException()));
        }).timeout(timeout).onErrorMap(error -> error instanceof BadCredentialsException
            || error instanceof ClientApiCredentialDependencyUnavailableException ? error
                : new ClientApiCredentialDependencyUnavailableException());
    }

    private ClientApiIdentity identity(ValidatedCredentialResponseBody response) {
        if (!"CLIENT".equals(response.getTenantType()) || response.getEffectiveScopeCodes() == null
            || response.getEffectiveScopeCodes().size() > ApiCredentialConstraints.MAX_EFFECTIVE_SCOPE_CODES) {
            throw new ClientApiCredentialDependencyUnavailableException();
        }
        return new ClientApiIdentity(response.getTenantId(), response.getOwnerRefId(), response.getCredentialId(),
            Set.copyOf(response.getEffectiveScopeCodes()));
    }
}
