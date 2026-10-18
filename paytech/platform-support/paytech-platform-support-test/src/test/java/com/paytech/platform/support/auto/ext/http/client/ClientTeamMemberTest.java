package com.paytech.platform.support.auto.ext.http.client;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientDeveloperPermissions;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientInvitationAcceptance;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientTeamPermissions;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsMemberStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantMemberType;
import com.paytech.platform.support.api.authentication.ClientAuthenticationApi;
import com.paytech.platform.support.api.authentication.response.ClientWorkspacesResponseBody;
import com.paytech.platform.support.api.clientinvitation.response.ClientInvitationAcceptanceResponseBody;
import com.paytech.platform.support.api.clientteam.ClientTeamApi;
import com.paytech.platform.support.api.clientteam.request.ChangeClientTeamMemberRoleRequestBody;
import com.paytech.platform.support.api.clientteam.response.ClientTeamMemberResponseBody;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.api.enums.ClientTeamErrorCode;
import com.paytech.platform.support.api.enums.ClientTeamRole;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.test.base.ClientTeamTestBase;
import com.paytech.platform.support.test.mocker.local.ClientWorkspaceMocker.Workspace;

import tools.jackson.core.type.TypeReference;

/**
 * 成员列表、调整角色与移除成员：排序与自己标记、权限即时变化、所有者与自己受保护、移除后失败关闭与工作区列表、重新邀请恢复、跨工作区隔离。
 *
 * @see ClientTeamApi#members(String, String, Integer, Integer)
 * @see ClientTeamApi#changeMemberRole(String, String, String, ChangeClientTeamMemberRoleRequestBody)
 * @see ClientTeamApi#removeMember(String, String, String)
 * @see ClientAuthenticationApi#workspaces(String, String)
 */
class ClientTeamMemberTest extends ClientTeamTestBase {

    private static final TypeReference<PagingResponseBody<ClientTeamMemberResponseBody>> PAGE = new TypeReference<>() {};

    private static final String WORKSPACES = "/authentication/v1/client/workspaces";

    /** 只读成员也能看成员列表：所有者在前，其余按加入时间（同一时刻按成员 ID），标记自己；不含已移除成员；看不到邀请列表。 */
    @Test
    void test_listMembers() {
        var workspace = teamWorkspace();
        var admin = workspaces.teamMember(workspace, ClientTeamRole.TENANT_ADMIN, email("admin"));
        var viewer = workspaces.teamMember(workspace, ClientTeamRole.TENANT_VIEWER, email("viewer"));
        var removed = workspaces.teamMember(workspace, ClientTeamRole.TENANT_DEVELOPER, email("removed"));
        remove(workspace.owner(), removed.memberId()).assertOk();

        var page = get(viewer, TEAM + "/members").assertOk().as(PAGE);

        var others = java.util.stream.Stream.of(admin, viewer).sorted(java.util.Comparator.comparingLong(Workspace::memberId))
            .toList();
        var items = page.items();
        assertAll(() -> assertEquals(3, page.total()),
            () -> assertEquals(List.of(workspace.owner().memberId(), others.get(0).memberId(), others.get(1).memberId()),
                items.stream().map(item -> Long.parseLong(item.getMemberId())).toList()),
            () -> assertEquals(ClientTeamRole.TENANT_OWNER.getCode(), items.getFirst().getRoleCode()),
            () -> assertEquals(TenantMemberType.OWNER.getCode(), items.getFirst().getMemberType()),
            () -> assertEquals(ClientTeamRole.TENANT_ADMIN.getCode(), item(items, admin).getRoleCode()),
            () -> assertEquals(TenantMemberType.ADMIN.getCode(), item(items, admin).getMemberType()),
            () -> assertEquals(ClientTeamRole.TENANT_VIEWER.getCode(), item(items, viewer).getRoleCode()),
            () -> assertEquals(TenantMemberType.MEMBER.getCode(), item(items, viewer).getMemberType()),
            () -> assertEquals(List.of(viewer.memberId()), items.stream().filter(ClientTeamMemberResponseBody::isSelf)
                .map(item -> Long.parseLong(item.getMemberId())).toList()),
            () -> assertTrue(items.stream().allMatch(item -> item.getEmailMasked().contains("***"))));
        get(viewer, TEAM + "/invitations").assertProblem(403, ClientAuthenticationErrorCode.CLIENT_PERMISSION_DENIED);
        assertEquals(2, get(workspace.owner(), TEAM + "/members?page=2&size=10").assertOk().as(PAGE).page());
    }

    /** 列表中的指定成员；不存在时失败。 */
    static ClientTeamMemberResponseBody item(List<ClientTeamMemberResponseBody> items, Workspace member) {
        return items.stream().filter(item -> item.getMemberId().equals(Long.toString(member.memberId()))).findFirst()
            .orElseThrow(() -> new AssertionError("成员列表缺少成员：" + member.memberId()));
    }

    /** 调整角色：下一次请求即按新角色判断权限；角色未变返回原结果；管理员角色使成员类型变为 ADMIN。 */
    @Test
    void test_changeRole() {
        var workspace = teamWorkspace();
        var developer = workspaces.teamMember(workspace, ClientTeamRole.TENANT_DEVELOPER, email("developer"));
        assertEquals(List.of(ClientDeveloperPermissions.API_CREDENTIAL_MANAGE),
            granted(developer, ClientDeveloperPermissions.API_CREDENTIAL_MANAGE));

        var changed = changeRole(workspace.owner(), developer.memberId(), ClientTeamRole.TENANT_VIEWER).assertOk()
            .as(ClientTeamMemberResponseBody.class);
        auditChecker.assertEvent(requestId, "CLIENT_TEAM_MEMBER_ROLE_CHANGE", AuditModels.Result.SUCCEEDED.getCode(),
            workspace.owner().actor(), "TENANT_MEMBER", Long.toString(developer.memberId()));

        assertAll(() -> assertEquals(ClientTeamRole.TENANT_VIEWER.getCode(), changed.getRoleCode()),
            () -> assertEquals(TenantMemberType.MEMBER.getCode(), changed.getMemberType()),
            () -> assertTrue(granted(developer, ClientDeveloperPermissions.API_CREDENTIAL_MANAGE).isEmpty()),
            () -> assertEquals(List.of(ClientDeveloperPermissions.API_CREDENTIAL_READ),
                granted(developer, ClientDeveloperPermissions.API_CREDENTIAL_READ)),
            () -> assertEquals(List.of(ClientTeamRole.TENANT_VIEWER.getCode()), teamData.activeRoles(developer.memberId())));
        changeRole(workspace.owner(), developer.memberId(), ClientTeamRole.TENANT_VIEWER).assertOk();
        assertEquals(List.of(ClientTeamRole.TENANT_VIEWER.getCode()), teamData.activeRoles(developer.memberId()));

        var promoted = changeRole(workspace.owner(), developer.memberId(), ClientTeamRole.TENANT_ADMIN).assertOk()
            .as(ClientTeamMemberResponseBody.class);
        assertEquals(TenantMemberType.ADMIN.getCode(), promoted.getMemberType());
        assertEquals(List.of(ClientTeamPermissions.TEAM_MANAGE), granted(developer, ClientTeamPermissions.TEAM_MANAGE));
    }

    /** 不能修改或移除所有者与自己；管理员可以管理其他成员；无 TEAM_MANAGE 403。 */
    @Test
    void test_protection() {
        var workspace = teamWorkspace();
        var admin = workspaces.teamMember(workspace, ClientTeamRole.TENANT_ADMIN, email("admin"));
        var viewer = workspaces.teamMember(workspace, ClientTeamRole.TENANT_VIEWER, email("viewer"));

        changeRole(admin, workspace.owner().memberId(), ClientTeamRole.TENANT_VIEWER)
            .assertProblem(409, ClientTeamErrorCode.CLIENT_MEMBER_PROTECTED);
        remove(admin, workspace.owner().memberId()).assertProblem(409, ClientTeamErrorCode.CLIENT_MEMBER_PROTECTED);
        changeRole(admin, admin.memberId(), ClientTeamRole.TENANT_VIEWER)
            .assertProblem(409, ClientTeamErrorCode.CLIENT_MEMBER_PROTECTED);
        remove(admin, admin.memberId()).assertProblem(409, ClientTeamErrorCode.CLIENT_MEMBER_PROTECTED);
        remove(workspace.owner(), workspace.owner().memberId())
            .assertProblem(409, ClientTeamErrorCode.CLIENT_MEMBER_PROTECTED);
        changeRole(viewer, admin.memberId(), ClientTeamRole.TENANT_VIEWER)
            .assertProblem(403, ClientAuthenticationErrorCode.CLIENT_PERMISSION_DENIED);

        changeRole(admin, viewer.memberId(), ClientTeamRole.TENANT_ADMIN).assertOk();
        assertEquals(List.of(ClientTeamRole.TENANT_ADMIN.getCode()), teamData.activeRoles(viewer.memberId()));
        assertEquals(List.of(ClientTeamRole.TENANT_OWNER.getCode()), teamData.activeRoles(workspace.owner().memberId()));
    }

    /** 移除：成员与授予 REVOKED；被移除成员下一次请求失败关闭，工作区列表不再显示；再次移除返回原结果。 */
    @Test
    void test_remove() {
        var workspace = teamWorkspace();
        var developer = workspaces.teamMember(workspace, ClientTeamRole.TENANT_DEVELOPER, email("developer"));
        var account = new Workspace(developer.accountId(), workspaces.accountSession(developer.accountId()), 0, 0);
        assertEquals(1, get(account, WORKSPACES).assertOk().as(ClientWorkspacesResponseBody.class).getItems().size());

        var removed = remove(workspace.owner(), developer.memberId()).assertOk().as(ClientTeamMemberResponseBody.class);
        auditChecker.assertEvent(requestId, "CLIENT_TEAM_MEMBER_REMOVE", AuditModels.Result.SUCCEEDED.getCode(),
            workspace.owner().actor(), "TENANT_MEMBER", Long.toString(developer.memberId()));

        assertAll(() -> assertEquals(OperationsMemberStatus.REVOKED.getCode(), removed.getStatus()),
            () -> assertNull(removed.getRoleCode()),
            () -> assertTrue(teamData.activeRoles(developer.memberId()).isEmpty()));
        get(developer, TEAM + "/members").assertProblem(401, ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        assertTrue(get(account, WORKSPACES).assertOk().as(ClientWorkspacesResponseBody.class).getItems().isEmpty());
        assertEquals(OperationsMemberStatus.REVOKED.getCode(), remove(workspace.owner(), developer.memberId()).assertOk()
            .as(ClientTeamMemberResponseBody.class).getStatus());
        changeRole(workspace.owner(), developer.memberId(), ClientTeamRole.TENANT_VIEWER)
            .assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /** 移除后重新邀请：接受后恢复原成员记录为 ACTIVE，获得新邀请的角色，不产生重复成员。 */
    @Test
    void test_reinviteRemovedMember() {
        var workspace = teamWorkspace();
        String email = email("developer");
        var developer = workspaces.teamMember(workspace, ClientTeamRole.TENANT_DEVELOPER, email);
        remove(workspace.owner(), developer.memberId()).assertOk();

        invite(workspace.owner(), email, ClientTeamRole.TENANT_ADMIN);
        var account = new Workspace(developer.accountId(), workspaces.accountSession(developer.accountId()), 0, 0);
        var accepted = accept(account, invitationLink(email)).assertOk().as(ClientInvitationAcceptanceResponseBody.class);

        var members = teamData.members(workspace.tenantId());
        var restored = members.stream()
            .filter(row -> ((Number) row.get("member_id")).longValue() == developer.memberId()).toList();
        assertAll(() -> assertEquals(ClientInvitationAcceptance.JOINED, accepted.getOutcome()),
            () -> assertEquals(Long.toString(developer.memberId()), accepted.getMemberId()),
            () -> assertEquals(2, members.size()),
            () -> assertEquals(OperationsMemberStatus.ACTIVE.getCode(), restored.getFirst().get("status")),
            () -> assertEquals(TenantMemberType.ADMIN.getCode(), restored.getFirst().get("member_type")),
            () -> assertEquals(List.of(ClientTeamRole.TENANT_ADMIN.getCode()), teamData.activeRoles(developer.memberId())),
            () -> assertEquals(1, get(account, WORKSPACES).assertOk().as(ClientWorkspacesResponseBody.class)
                .getItems().size()));
    }

    /** 其他工作区的成员与非法 ID 一律 404，不改变对方数据。 */
    @Test
    void test_crossWorkspace() {
        var workspace = teamWorkspace();
        var other = teamWorkspace();
        var foreign = workspaces.teamMember(other, ClientTeamRole.TENANT_DEVELOPER, email("foreign"));

        changeRole(workspace.owner(), foreign.memberId(), ClientTeamRole.TENANT_VIEWER)
            .assertProblem(404, CommonErrorCode.NOT_FOUND);
        remove(workspace.owner(), foreign.memberId()).assertProblem(404, CommonErrorCode.NOT_FOUND);
        post(workspace.owner(), TEAM + "/members/abc/removal").assertProblem(404, CommonErrorCode.NOT_FOUND);

        assertEquals(List.of(ClientTeamRole.TENANT_DEVELOPER.getCode()), teamData.activeRoles(foreign.memberId()));
        assertFalse(get(workspace.owner(), TEAM + "/members").assertOk().text().contains(Long.toString(foreign.memberId())));
    }
}
