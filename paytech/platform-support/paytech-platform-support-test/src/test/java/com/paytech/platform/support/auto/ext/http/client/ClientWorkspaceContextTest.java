package com.paytech.platform.support.auto.ext.http.client;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.accesscontrol.domain.enums.AuthenticationRealm;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientSessionContextType;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsMemberStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;
import com.paytech.platform.support.api.authentication.ClientAuthenticationApi;
import com.paytech.platform.support.api.authentication.request.RefreshRequestBody;
import com.paytech.platform.support.api.authentication.request.WorkspaceContextRequestBody;
import com.paytech.platform.support.api.authentication.response.AccountContextResponseBody;
import com.paytech.platform.support.api.authentication.response.AuthenticationResponseBody;
import com.paytech.platform.support.api.authentication.response.ClientWorkspaceResponseBody;
import com.paytech.platform.support.api.authentication.response.ClientWorkspacesResponseBody;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;
import com.paytech.platform.support.api.enums.OperationsAccountType;
import com.paytech.platform.support.test.base.OperationsApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.util.TestAuthenticationKeys;

/**
 * Client 工作区上下文（PLT-I16）：列出工作区、进入工作区、返回账号，以及租户会话失效时刷新降级为账号上下文。
 *
 * @see ClientAuthenticationApi#workspaces(String, String)
 * @see ClientAuthenticationApi#selectWorkspace(String, String, WorkspaceContextRequestBody)
 * @see ClientAuthenticationApi#selectAccountContext(String, String, RefreshRequestBody)
 * @see ClientAuthenticationApi#refresh(RefreshRequestBody)
 * @see ClientAuthenticationApi#context(String, String)
 */
class ClientWorkspaceContextTest extends OperationsApiTestBase {

    private static final String BASE = "/authentication/v1/client";

    private static final String REFRESH_PURPOSE = "client-refresh-v1";

    @Autowired
    TestAuthenticationKeys keys;

    /** 列表不含 PENDING 租户；进入工作区签发租户令牌、作废原刷新令牌；绝对到期沿用原会话；可返回账号上下文。 */
    @Test
    void test_normalFlow() {
        var client = customer();
        long active = workspace(client.accountId(), OperationsTenantOwnerStatus.ACTIVE,
            OperationsTenantSecurityStatus.ACTIVE, OperationsMemberStatus.ACTIVE);
        workspace(client.accountId(), OperationsTenantOwnerStatus.PENDING, OperationsTenantSecurityStatus.ACTIVE,
            OperationsMemberStatus.ACTIVE);
        long suspended = workspace(client.accountId(), OperationsTenantOwnerStatus.SUSPENDED,
            OperationsTenantSecurityStatus.ACTIVE, OperationsMemberStatus.ACTIVE);

        var list = workspaces(client).assertOk().as(ClientWorkspacesResponseBody.class).getItems();

        assertEquals(2, list.size());
        var accessible = item(list, active);
        assertTrue(accessible.isAccessible());
        assertNull(accessible.getReasonCode());
        assertEquals(OperationsTenantOwnerStatus.ACTIVE.getCode(), accessible.getOwnerStatus());
        var restricted = item(list, suspended);
        assertFalse(restricted.isAccessible());
        assertEquals("TENANT_SUSPENDED", restricted.getReasonCode());

        var entered = select(client, active).assertOk().as(AuthenticationResponseBody.class);

        var context = entered.getContext();
        assertEquals(ClientSessionContextType.TENANT.getCode(), context.getContextType());
        assertEquals(Long.toString(active), context.getMemberId());
        assertEquals(accessible.getTenantId(), context.getTenantId());
        assertNotEquals(Long.toString(client.sessionId()), context.getSessionId());
        assertEquals(List.of("WORKSPACE", "SELECT_WORKSPACE", "ACCOUNT_CONTEXT", "LOGOUT"), context.getAllowedActions());
        assertEquals(client.absoluteExpiry(), Instant.parse(entered.getRefreshExpiresAt()));
        var claims = keys.verifiedJwt(AuthenticationRealm.CUSTOMER.getCode(), entered.getAccessToken())[1];
        assertEquals(ClientSessionContextType.TENANT.getCode(), claims.path("context_type").asString());
        assertEquals(accessible.getTenantId(), claims.path("tenant_id").asString());
        assertEquals(Long.toString(active), claims.path("tenant_member_id").asString());
        assertEquals(AccessTenantType.CLIENT.getCode(), claims.path("tenant_type").asString());
        assertTrue(claims.path("email").isMissingNode());
        refresh(client.refreshToken()).assertProblem(401, ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);

        var tenant = new Client(client.accountId(), Long.parseLong(context.getSessionId()), entered.getRefreshToken(),
            client.absoluteExpiry());
        assertEquals(ClientSessionContextType.TENANT.getCode(), context(tenant).assertOk()
            .as(AccountContextResponseBody.class).getContextType());
        var rotated = refresh(tenant.refreshToken()).assertOk().as(AuthenticationResponseBody.class);
        assertEquals(ClientSessionContextType.TENANT.getCode(), rotated.getContext().getContextType());
        assertNull(rotated.getContext().getFallbackReason());

        var back = accountContext(new Client(tenant.accountId(), tenant.sessionId(), rotated.getRefreshToken(),
            client.absoluteExpiry())).assertOk().as(AuthenticationResponseBody.class);

        assertEquals(ClientSessionContextType.ACCOUNT.getCode(), back.getContext().getContextType());
        assertNull(back.getContext().getTenantId());
        assertEquals(List.of("ONBOARDING", "SELECT_WORKSPACE", "LOGOUT"), back.getContext().getAllowedActions());
        assertEquals(client.absoluteExpiry(), Instant.parse(back.getRefreshExpiresAt()));
        refresh(rotated.getRefreshToken()).assertProblem(401, ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        refresh(back.getRefreshToken()).assertOk();
    }

    /** 租户被安全暂停、来源终止或成员被撤销后，查询上下文失败，刷新撤销租户会话并降级为账号上下文且带公开原因。 */
    @Test
    void test_fallbackOnRefresh() {
        assertFallback(OperationsTenantOwnerStatus.ACTIVE, OperationsTenantSecurityStatus.SUSPENDED,
            OperationsMemberStatus.ACTIVE, "TENANT_SECURITY_SUSPENDED");
        assertFallback(OperationsTenantOwnerStatus.TERMINATED, OperationsTenantSecurityStatus.ACTIVE,
            OperationsMemberStatus.ACTIVE, "TENANT_TERMINATED");
        assertFallback(OperationsTenantOwnerStatus.ACTIVE, OperationsTenantSecurityStatus.ACTIVE,
            OperationsMemberStatus.REVOKED, "MEMBER_INACTIVE");
    }

    /** 他人成员、不存在、PENDING、暂停或已撤销的成员统一拒绝，不透露原因；当前账号会话保持可用。 */
    @Test
    void test_workspaceUnavailable() {
        var client = customer();
        var other = customer();
        long foreign = workspace(other.accountId(), OperationsTenantOwnerStatus.ACTIVE,
            OperationsTenantSecurityStatus.ACTIVE, OperationsMemberStatus.ACTIVE);
        long pending = workspace(client.accountId(), OperationsTenantOwnerStatus.PENDING,
            OperationsTenantSecurityStatus.ACTIVE, OperationsMemberStatus.ACTIVE);
        long suspended = workspace(client.accountId(), OperationsTenantOwnerStatus.ACTIVE,
            OperationsTenantSecurityStatus.SUSPENDED, OperationsMemberStatus.ACTIVE);
        long revoked = workspace(client.accountId(), OperationsTenantOwnerStatus.ACTIVE,
            OperationsTenantSecurityStatus.ACTIVE, OperationsMemberStatus.REVOKED);

        for (long memberId : new long[] {foreign, pending, suspended, revoked, OperatorBuilder.nextId()}) {
            select(client, memberId).assertProblem(403, ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE);
        }
        call(HttpMethod.POST, BASE + "/workspace-context", headers(client),
            new WorkspaceContextRequestBody(client.refreshToken(), "9999999999999999999"))
            .assertProblem(403, ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE);

        assertEquals(ClientSessionContextType.ACCOUNT.getCode(), context(client).assertOk()
            .as(AccountContextResponseBody.class).getContextType());
        refresh(client.refreshToken()).assertOk();
    }

    /** Cookie 证明不属于当前会话或会话已撤销时，切换返回会话失效且不改变任何会话。 */
    @Test
    void test_invalidSession() {
        var client = customer();
        var other = customer();
        long member = workspace(client.accountId(), OperationsTenantOwnerStatus.ACTIVE,
            OperationsTenantSecurityStatus.ACTIVE, OperationsMemberStatus.ACTIVE);

        select(new Client(client.accountId(), client.sessionId(), other.refreshToken(), client.absoluteExpiry()), member)
            .assertProblem(401, ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        accountContext(new Client(client.accountId(), other.sessionId(), other.refreshToken(), client.absoluteExpiry()))
            .assertProblem(401, ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);

        refresh(client.refreshToken()).assertOk();
        refresh(other.refreshToken()).assertOk();
    }

    /** 进入一个工作区后改变其状态，核对查询与刷新降级。 */
    void assertFallback(OperationsTenantOwnerStatus owner, OperationsTenantSecurityStatus security,
        OperationsMemberStatus member, String reason) {
        var client = customer();
        long memberId = workspace(client.accountId(), OperationsTenantOwnerStatus.ACTIVE,
            OperationsTenantSecurityStatus.ACTIVE, OperationsMemberStatus.ACTIVE);
        var entered = select(client, memberId).assertOk().as(AuthenticationResponseBody.class);
        var tenant = new Client(client.accountId(), Long.parseLong(entered.getContext().getSessionId()),
            entered.getRefreshToken(), client.absoluteExpiry());
        long tenantId = Long.parseLong(entered.getContext().getTenantId());
        assertEquals(1, operationsData.updateTenantStatus(tenantId, owner.getCode(), security.getCode()));
        assertEquals(1, operationsData.updateMemberStatus(memberId, member.getCode()));

        context(tenant).assertProblem(401, ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        var fallback = refresh(tenant.refreshToken()).assertOk().as(AuthenticationResponseBody.class);

        assertEquals(ClientSessionContextType.ACCOUNT.getCode(), fallback.getContext().getContextType(), reason);
        assertEquals(reason, fallback.getContext().getFallbackReason());
        assertNotEquals(Long.toString(tenant.sessionId()), fallback.getContext().getSessionId());
        assertEquals(client.absoluteExpiry(), Instant.parse(fallback.getRefreshExpiresAt()));
        refresh(tenant.refreshToken()).assertProblem(401, ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        var account = new Client(client.accountId(), Long.parseLong(fallback.getContext().getSessionId()),
            fallback.getRefreshToken(), client.absoluteExpiry());
        select(account, memberId).assertProblem(403, ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE);
        assertNull(context(account).assertOk().as(AccountContextResponseBody.class).getFallbackReason());
    }

    /** 已验证、ACTIVE 的 CUSTOMER 账号及一个账号上下文会话和当前刷新代。 */
    Client customer() {
        Instant now = operationsMocker.now();
        long accountId = OperatorBuilder.nextId();
        assertEquals(1, operationsData.insertAccount(accountId, AuthenticationRealm.CUSTOMER.getCode(),
            OperationsAccountType.HUMAN.getCode(), null, null, "c***@example.test", now,
            OperationsAccountStatus.ACTIVE.getCode(), null, now));
        assertEquals(1, operationsData.insertPassword(OperatorBuilder.nextId(), accountId,
            keys.passwordPhc("client-password-0001"), false, "ACTIVE", now));
        long sessionId = OperatorBuilder.nextId();
        Instant absolute = now.plus(Duration.ofDays(7));
        assertEquals(1, operationsData.insertSession(sessionId, accountId, AuthenticationRealm.CUSTOMER.getCode(),
            "CLIENT", ClientSessionContextType.ACCOUNT.getCode(), null, null, "ACTIVE", now.plus(Duration.ofDays(1)),
            absolute, now));
        String refresh = operationsMocker.refresh(AuthenticationRealm.CUSTOMER.getCode(), REFRESH_PURPOSE, sessionId,
            absolute);
        return new Client(accountId, sessionId, refresh, absolute);
    }

    /** CLIENT 租户（指定来源与安全状态）及账号在其中的 OWNER 成员；返回成员编号。 */
    long workspace(long accountId, OperationsTenantOwnerStatus owner, OperationsTenantSecurityStatus security,
        OperationsMemberStatus member) {
        long tenantId = operationsMocker.tenant(OperatorBuilder.nextId(), AccessTenantType.CLIENT.getCode(),
            OperatorBuilder.nextId(), "Client fixture");
        assertEquals(1, operationsData.updateTenantStatus(tenantId, owner.getCode(), security.getCode()));
        long memberId = OperatorBuilder.nextId();
        assertEquals(1, operationsData.insertMember(memberId, tenantId, accountId, "OWNER", member.getCode(),
            operationsMocker.now()));
        return memberId;
    }

    /** 按成员编号取列表项。 */
    static ClientWorkspaceResponseBody item(List<ClientWorkspaceResponseBody> items, long memberId) {
        return items.stream().filter(item -> item.getMemberId().equals(Long.toString(memberId))).findFirst()
            .orElseThrow();
    }

    /** 以会话身份 GET 工作区列表。 */
    PlatformResponseChecker workspaces(Client client) {
        return call(HttpMethod.GET, BASE + "/workspaces", headers(client), null);
    }

    /** 以会话身份和 Cookie 原值进入指定成员的工作区。 */
    PlatformResponseChecker select(Client client, long memberId) {
        return call(HttpMethod.POST, BASE + "/workspace-context", headers(client),
            new WorkspaceContextRequestBody(client.refreshToken(), Long.toString(memberId)));
    }

    /** 以会话身份和 Cookie 原值返回账号上下文。 */
    PlatformResponseChecker accountContext(Client client) {
        return call(HttpMethod.POST, BASE + "/account-context", headers(client),
            new RefreshRequestBody(client.refreshToken()));
    }

    /** 以会话身份查询当前上下文。 */
    PlatformResponseChecker context(Client client) {
        return call(HttpMethod.GET, BASE + "/context", headers(client), null);
    }

    /** 刷新只凭 Cookie 原值，不带可信身份 Header。 */
    PlatformResponseChecker refresh(String refreshToken) {
        return anonymous(HttpMethod.POST, BASE + "/refresh", new RefreshRequestBody(refreshToken));
    }

    /** 会话坐标作为 Gateway 转发的可信 Header。 */
    org.springframework.http.HttpHeaders headers(Client client) {
        return headers(Long.toString(client.accountId()), Long.toString(client.sessionId()));
    }

    /** 测试侧持有的 Client 会话坐标与刷新令牌原值。 */
    record Client(long accountId, long sessionId, String refreshToken, Instant absoluteExpiry) {}
}
