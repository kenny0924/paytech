package com.paytech.gateway.security.clientapi;

import java.util.Optional;
import java.util.Set;

import com.paytech.gateway.security.identity.GatewayAuthenticatedIdentity;
import com.paytech.platform.support.api.apicredential.ApiCredentialConstraints;

/** 仅由 PLT-I03 验证结果建立；Tenant 用于 Guard，Client 用于 Owner 身份。 */
public record ClientApiIdentity(String tenantId, String clientId, String credentialId,
    Set<String> effectiveScopes) implements GatewayAuthenticatedIdentity {
    public ClientApiIdentity {
        if (!validId(tenantId) || !validId(clientId) || !validId(credentialId) || effectiveScopes == null
            || effectiveScopes.size() > ApiCredentialConstraints.MAX_EFFECTIVE_SCOPE_CODES
            || effectiveScopes.stream().anyMatch(scope -> !ApiCredentialConstraints.isValidScopeCode(scope))) {
            throw new IllegalArgumentException("Client API 验证身份无效");
        }
        effectiveScopes = Set.copyOf(effectiveScopes);
    }

    private static boolean validId(String value) {
        return value != null && value.matches("[1-9][0-9]{0,18}");
    }

    @Override
    public String principalKey() {
        return credentialId;
    }

    @Override
    public Optional<String> tenantKey() {
        return Optional.of(tenantId);
    }

    @Override
    public String accessSubject() {
        return credentialId;
    }

    @Override
    public String toString() {
        return "ClientApiIdentity[validated=true, scopeCount=" + effectiveScopes.size() + "]";
    }
}
