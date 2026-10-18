package com.paytech.gateway.security.identity;

import java.util.Optional;

import com.paytech.gateway.guard.policy.RawGuardIdentity;

/** 已验证 Client Access JWT 的不可变可信投影，不保留 Token。 */
public record ClientConsoleIdentity(String subject, String sessionId, String contextType, String tenantId,
    String tenantMemberId) implements GatewayAuthenticatedIdentity {
    private static final String ACCOUNT = "ACCOUNT";
    private static final String TENANT = "TENANT";

    public ClientConsoleIdentity {
        RawGuardIdentity.requireValid(subject);
        RawGuardIdentity.requireValid(sessionId);
        if (TENANT.equals(contextType)) {
            RawGuardIdentity.requireValid(tenantId);
            RawGuardIdentity.requireValid(tenantMemberId);
        } else if (!ACCOUNT.equals(contextType) || tenantId != null || tenantMemberId != null) {
            throw new IllegalArgumentException("Client 可信身份上下文无效");
        }
    }

    @Override
    public String principalKey() {
        return subject;
    }

    @Override
    public Optional<String> tenantKey() {
        return TENANT.equals(contextType) ? Optional.of(tenantId) : Optional.empty();
    }

    @Override
    public String accessSubject() {
        return subject;
    }

    public boolean isTenantContext() {
        return TENANT.equals(contextType);
    }

    @Override
    public String toString() {
        return "ClientConsoleIdentity[subjectPresent=" + (subject != null) + ", sessionPresent=" + (sessionId != null)
            + ", contextPresent=" + (contextType != null) + ", tenantPresent=" + (tenantId != null)
            + ", tenantMemberPresent=" + (tenantMemberId != null) + "]";
    }
}
