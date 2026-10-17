package com.paytech.gateway.auto.security;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import com.paytech.gateway.security.clientapi.ClientApiIdentity;
import com.paytech.gateway.security.identity.OperationsIdentity;
import com.paytech.gateway.security.identity.ClientConsoleIdentity;
import com.paytech.platform.support.api.apicredential.ApiCredentialConstraints;

/** 可信身份值不能混淆租户上下文、接受非法 Owner ID 或被输入集合事后修改。 */
class GatewayIdentityValueAutoTest {
    /** 客户 ACCOUNT 与 TENANT 坐标互斥，Guard 键按当前工作区确定，诊断不能泄露身份。 */
    @Test
    void test_clientConsoleIdentityKeepsAccountAndTenantSeparate() {
        for (String context : Arrays.asList(null, "", "BOOTSTRAP")) {
            assertThrows(IllegalArgumentException.class, () -> new ClientConsoleIdentity("actor", "session", context, null, null));
        }
        assertThrows(IllegalArgumentException.class, () -> new ClientConsoleIdentity("actor", "session", "ACCOUNT", "tenant", null));
        assertThrows(IllegalArgumentException.class, () -> new ClientConsoleIdentity("actor", "session", "ACCOUNT", null, "member"));
        assertThrows(IllegalArgumentException.class, () -> new ClientConsoleIdentity("actor", "session", "TENANT", null, "member"));
        assertThrows(IllegalArgumentException.class, () -> new ClientConsoleIdentity("actor", "session", "TENANT", "tenant", null));
        var account = new ClientConsoleIdentity("actor-canary", "session-canary", "ACCOUNT", null, null);
        var tenant = new ClientConsoleIdentity("actor-canary", "session-canary", "TENANT", "tenant-canary", "member-canary");
        assertEquals(Optional.empty(), account.tenantKey());
        assertEquals(Optional.of("tenant-canary"), tenant.tenantKey());
        assertFalse(account.isTenantContext());
        assertTrue(tenant.isTenantContext());
        for (var identity : List.of(account, tenant)) {
            assertEquals("actor-canary", identity.principalKey());
            assertEquals("actor-canary", identity.accessSubject());
            assertFalse(identity.toString().contains("canary"));
        }
    }

    @Test
    void test_clientIdentityRejectsInvalidCoordinatesAndScopeCollections() {
        for (String invalid : Arrays.asList(null, "", "0", "01", "-1", "owner", "1".repeat(20))) {
            assertThrows(IllegalArgumentException.class, () -> new ClientApiIdentity(invalid, "2", "3", Set.of()));
            assertThrows(IllegalArgumentException.class, () -> new ClientApiIdentity("1", invalid, "3", Set.of()));
            assertThrows(IllegalArgumentException.class, () -> new ClientApiIdentity("1", "2", invalid, Set.of()));
        }
        assertThrows(IllegalArgumentException.class, () -> new ClientApiIdentity("1", "2", "3", null));
        assertThrows(IllegalArgumentException.class, () -> new ClientApiIdentity("1", "2", "3", Set.of("Upper")));
        var tooMany = IntStream.rangeClosed(0, ApiCredentialConstraints.MAX_EFFECTIVE_SCOPE_CODES)
            .mapToObj(i -> "scope" + i).collect(Collectors.toSet());
        assertThrows(IllegalArgumentException.class, () -> new ClientApiIdentity("1", "2", "3", tooMany));
        var scopes = new HashSet<>(Set.of("orders.read"));
        var identity = new ClientApiIdentity("1", "2", "3", scopes);
        scopes.add("orders.write");
        assertEquals(Set.of("orders.read"), identity.effectiveScopes());
        assertThrows(UnsupportedOperationException.class, () -> identity.effectiveScopes().clear());
        assertEquals("3", identity.principalKey());
        assertEquals(Optional.of("1"), identity.tenantKey());
        assertEquals("3", identity.accessSubject());
    }

    /** Bootstrap 与 Tenant 上下文保持互斥，诊断字符串不暴露身份值。 */
    @Test
    void test_bootstrapCannotCarryTenantCoordinatesOrUnknownContext() {
        for (String context : Arrays.asList(null, "", "UNKNOWN")) {
            assertThrows(IllegalArgumentException.class, () -> new OperationsIdentity("actor", "session", context, null, null));
        }
        assertThrows(IllegalArgumentException.class, () -> new OperationsIdentity("actor", "session", "BOOTSTRAP", "tenant", null));
        assertThrows(IllegalArgumentException.class, () -> new OperationsIdentity("actor", "session", "BOOTSTRAP", null, "member"));
        var bootstrap = new OperationsIdentity("actor-canary", "session-canary", "BOOTSTRAP", null, null);
        var tenant = new OperationsIdentity("actor-canary", "session-canary", "TENANT", "tenant-canary", "member-canary");
        assertEquals(Optional.empty(), bootstrap.tenantKey());
        assertEquals(Optional.of("tenant-canary"), tenant.tenantKey());
        assertFalse(bootstrap.isTenantContext());
        assertTrue(tenant.isTenantContext());
        for (var identity : List.of(bootstrap, tenant)) assertFalse(identity.toString().contains("canary"));
    }
}
