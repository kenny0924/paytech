package com.paytech.gateway.guard.policy;

import java.net.InetAddress;
import java.util.Objects;

import com.paytech.gateway.routing.OperationsRouteCode;

/** Guard 解析所需的可信请求上下文。 */
public record GuardContext(String routeCode, InetAddress remoteAddress, String authenticatedPrincipal,
    String tenantIdentity) {

    public GuardContext {
        if (routeCode == null || routeCode.isBlank()) {
            throw new IllegalArgumentException("Guard 路由编码不可为空");
        }
        Objects.requireNonNull(remoteAddress, "Guard 远端地址不可为空");
        if (authenticatedPrincipal != null) {
            RawGuardIdentity.requireValid(authenticatedPrincipal);
        }
        if (tenantIdentity != null) {
            RawGuardIdentity.requireValid(tenantIdentity);
        }
    }

    public GuardContext(OperationsRouteCode routeCode, InetAddress remoteAddress, String authenticatedSubject) {
        this(Objects.requireNonNull(routeCode, "Guard 路由编码不可为空").getCode(), remoteAddress,
            authenticatedSubject, null);
    }

    public GuardContext(String routeCode, InetAddress remoteAddress, String authenticatedSubject) {
        this(routeCode, remoteAddress, authenticatedSubject, null);
    }

    public boolean hasAuthenticatedSubject() {
        return authenticatedPrincipal != null;
    }

    public boolean hasTenantIdentity() {
        return tenantIdentity != null;
    }

    @Override
    public String toString() {
        return "GuardContext[routeCodePresent=" + (routeCode != null) + ", remoteAddressPresent="
            + (remoteAddress != null) + ", authenticatedPrincipalPresent=" + hasAuthenticatedSubject()
            + ", tenantIdentityPresent=" + hasTenantIdentity() + "]";
    }
}
