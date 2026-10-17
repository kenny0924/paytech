package com.paytech.platform.support.test.mocker.local;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientDeveloperPermissions;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientTeamPermissions;
import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.accesscontrol.domain.enums.AuthenticationRealm;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientSessionContextType;
import com.paytech.platform.support.api.enums.ClientTeamRole;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;
import com.paytech.platform.support.api.enums.OperationsAccountType;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.dal.ClientDeveloperTestMapper;
import com.paytech.platform.support.test.dal.OperationsTestMapper;
import com.paytech.platform.support.test.mocker.external.LoginEmailVaultMocker;
import com.paytech.platform.support.test.util.TestAuthenticationKeys;

import lombok.RequiredArgsConstructor;

/**
 * CLIENT 工作区自助测试数据：CUSTOMER 账号、租户 OWNER 成员、仅含指定开发者权限的租户角色、租户上下文会话
 * <li>账号、成员、角色、权限与会话属于 PlatformDataCleaner 整表回收的表；本组件只按租户删除凭证两表并回收自建租户。</li>
 */
@TestComponent
@RequiredArgsConstructor
public class ClientWorkspaceMocker implements Clear {

    /** 与完整初始化 DML 相同的权限主键。 */
    public static final Map<String, Long> PERMISSION_IDS = Map.of(
        ClientDeveloperPermissions.API_CREDENTIAL_READ, 900000000000058101L,
        ClientDeveloperPermissions.API_CREDENTIAL_MANAGE, 900000000000058102L,
        ClientDeveloperPermissions.WEBHOOK_READ, 900000000000058103L,
        ClientDeveloperPermissions.WEBHOOK_MANAGE, 900000000000058104L,
        ClientTeamPermissions.TEAM_READ, 900000000000058105L,
        ClientTeamPermissions.TEAM_MANAGE, 900000000000058106L);

    /** 测试账号的登录密码，与 PHC 一致。 */
    public static final String PASSWORD = "client-password-0001";

    private final TestAuthenticationKeys keys;
    private final OperationsMocker operations;
    private final OperationsTestMapper mapper;
    private final ClientDeveloperTestMapper developerData;
    private final List<Long> credentialTenants = new ArrayList<>();
    private boolean permissions;

    /** 插入 6 个 CLIENT 工作区权限（开发者 4 个、团队 2 个；每例一次）。 */
    public void permissions() {
        if (permissions) {
            return;
        }
        var now = operations.now();
        PERMISSION_IDS.forEach((code, id) -> assertEquals(1, mapper.insertPermission(id, code,
            code.substring(0, code.lastIndexOf('_')), code.substring(code.lastIndexOf('_') + 1), "LOW", "ACTIVE", now)));
        permissions = true;
    }

    /** 新的 ACTIVE CLIENT 租户，由 OperationsMocker 回收。 */
    public long tenant() {
        long tenantId = operations.tenant(OperatorBuilder.nextId(), AccessTenantType.CLIENT.getCode(),
            OperatorBuilder.nextId(), "Client developer fixture");
        track(tenantId);
        return tenantId;
    }

    /** 登记需要按租户清理凭证的租户（例如其他 Fixture 插入的租户）。 */
    public void track(long tenantId) {
        if (!credentialTenants.contains(tenantId)) {
            credentialTenants.add(tenantId);
        }
    }

    /**
     * 租户中的 OWNER 成员及其租户上下文会话；角色只授予给定权限
     *
     * @return 会话坐标
     */
    public Workspace owner(long tenantId, String... permissionCodes) {
        permissions();
        track(tenantId);
        var now = operations.now();
        long accountId = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertAccount(accountId, AuthenticationRealm.CUSTOMER.getCode(),
            OperationsAccountType.HUMAN.getCode(), null, null, "c***@example.test", now,
            OperationsAccountStatus.ACTIVE.getCode(), null, now));
        assertEquals(1, mapper.insertPassword(OperatorBuilder.nextId(), accountId, keys.passwordPhc(PASSWORD),
            false, "ACTIVE", now));
        long memberId = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertMember(memberId, tenantId, accountId, "OWNER", "ACTIVE", now));
        long roleId = OperatorBuilder.nextId();
        String scope = Long.toString(tenantId);
        // 同一租户可有多个测试成员；角色编码按租户唯一，故每个成员使用独立角色，权限判断不依赖角色编码。
        assertEquals(1, mapper.insertRole(roleId, "TENANT", tenantId, scope, "TEST_CLIENT_ROLE_" + roleId, "客户工作区测试角色",
            "ACTIVE", false, now));
        for (String code : permissionCodes) {
            assertEquals(1, mapper.insertRolePermission(OperatorBuilder.nextId(), roleId, PERMISSION_IDS.get(code),
                "ACTIVE", now));
        }
        assertEquals(1, mapper.insertMemberRole(OperatorBuilder.nextId(), memberId, tenantId, roleId, scope,
            now.minusSeconds(1), null, memberId, "ACTIVE", now));
        long sessionId = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertSession(sessionId, accountId, AuthenticationRealm.CUSTOMER.getCode(), "CLIENT",
            ClientSessionContextType.TENANT.getCode(), tenantId, memberId, "ACTIVE", now.plus(Duration.ofDays(1)),
            now.plus(Duration.ofDays(7)), now));
        return new Workspace(accountId, sessionId, tenantId, memberId);
    }

    /**
     * 带四个系统角色（按 ClientTeamPermissions 矩阵授予权限）的 ACTIVE 工作区，以及持有 TENANT_OWNER 的入驻申请人
     * <li>与首次 ACTIVE 投影创建的角色结构一致；所有者账号使用给定合成邮箱的稳定 Token。</li>
     *
     * @return 工作区坐标、四个角色主键与所有者会话
     */
    public TeamWorkspace teamWorkspace(String ownerEmail) {
        permissions();
        long tenantId = tenant();
        var now = operations.now();
        var roleIds = new java.util.EnumMap<ClientTeamRole, Long>(ClientTeamRole.class);
        for (ClientTeamRole role : ClientTeamRole.values()) {
            long roleId = OperatorBuilder.nextId();
            assertEquals(1, mapper.insertRole(roleId, "TENANT", tenantId, Long.toString(tenantId), role.getCode(),
                role.getMessage(), "ACTIVE", true, now));
            for (String code : ClientTeamPermissions.of(role)) {
                assertEquals(1, mapper.insertRolePermission(OperatorBuilder.nextId(), roleId, PERMISSION_IDS.get(code),
                    "ACTIVE", now));
            }
            roleIds.put(role, roleId);
        }
        var workspace = new TeamWorkspace(tenantId, roleIds, null);
        return new TeamWorkspace(tenantId, roleIds, teamMember(workspace, ClientTeamRole.TENANT_OWNER, ownerEmail));
    }

    /**
     * 工作区中持有指定系统角色的 ACTIVE 成员及其租户上下文会话；成员类型随角色
     *
     * @return 会话坐标
     */
    public Workspace teamMember(TeamWorkspace workspace, ClientTeamRole role, String email) {
        var now = operations.now();
        long accountId = customerAccount(email);
        long memberId = OperatorBuilder.nextId();
        String scope = Long.toString(workspace.tenantId());
        assertEquals(1, mapper.insertMember(memberId, workspace.tenantId(), accountId, role.getMemberType(), "ACTIVE", now));
        assertEquals(1, mapper.insertMemberRole(OperatorBuilder.nextId(), memberId, workspace.tenantId(),
            workspace.roleIds().get(role), scope, now.minusSeconds(1), null, memberId, "ACTIVE", now));
        return new Workspace(accountId, tenantSession(accountId, workspace.tenantId(), memberId), workspace.tenantId(),
            memberId);
    }

    /** 已验证的 CUSTOMER 账号（含密码），邮箱 Token 与 LoginEmailVaultMocker 的稳定 Token 一致。 */
    public long customerAccount(String email) {
        var now = operations.now();
        long accountId = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertAccount(accountId, AuthenticationRealm.CUSTOMER.getCode(),
            OperationsAccountType.HUMAN.getCode(), null, LoginEmailVaultMocker.tokenOf(email),
            email.charAt(0) + "***" + email.substring(email.indexOf('@')), now, OperationsAccountStatus.ACTIVE.getCode(),
            null, now));
        assertEquals(1, mapper.insertPassword(OperatorBuilder.nextId(), accountId, keys.passwordPhc(PASSWORD), false,
            "ACTIVE", now));
        return accountId;
    }

    /** 账号在指定成员上的租户上下文会话。 */
    public long tenantSession(long accountId, long tenantId, long memberId) {
        var now = operations.now();
        long sessionId = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertSession(sessionId, accountId, AuthenticationRealm.CUSTOMER.getCode(), "CLIENT",
            ClientSessionContextType.TENANT.getCode(), tenantId, memberId, "ACTIVE", now.plus(Duration.ofDays(1)),
            now.plus(Duration.ofDays(7)), now));
        return sessionId;
    }

    /** 同一账号的账号上下文会话（不在任何工作区内）。 */
    public long accountSession(long accountId) {
        var now = operations.now();
        long sessionId = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertSession(sessionId, accountId, AuthenticationRealm.CUSTOMER.getCode(), "CLIENT",
            ClientSessionContextType.ACCOUNT.getCode(), null, null, "ACTIVE", now.plus(Duration.ofDays(1)),
            now.plus(Duration.ofDays(7)), now));
        return sessionId;
    }

    /** 删除本例租户的凭证与授权；其余表由整表回收负责。 */
    @Override
    public void clear() {
        try {
            if (!credentialTenants.isEmpty()) {
                developerData.deleteScopes(credentialTenants);
                developerData.deleteCredentials(credentialTenants);
            }
        } finally {
            credentialTenants.clear();
            permissions = false;
        }
    }

    /**
     * 测试侧持有的团队工作区：租户、四个系统角色主键与所有者会话
     *
     * @param tenantId 租户
     * @param roleIds 系统角色主键
     * @param owner 所有者会话坐标
     */
    public record TeamWorkspace(long tenantId, java.util.Map<ClientTeamRole, Long> roleIds, Workspace owner) {
    }

    /** 测试侧持有的工作区会话坐标。 */
    public record Workspace(long accountId, long sessionId, long tenantId, long memberId) {
        /** 可信账号 Header 值。 */
        public String actor() {
            return Long.toString(accountId);
        }

        /** 可信会话 Header 值。 */
        public String session() {
            return Long.toString(sessionId);
        }
    }
}
