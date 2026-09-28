package com.paytech.gateway.security.identity;

import java.util.Optional;

/** Gateway 认证后 Guard 与授权阶段共享的最小可信身份。 */
public interface GatewayAuthenticatedIdentity {
    String principalKey();

    Optional<String> tenantKey();

    String accessSubject();
}
