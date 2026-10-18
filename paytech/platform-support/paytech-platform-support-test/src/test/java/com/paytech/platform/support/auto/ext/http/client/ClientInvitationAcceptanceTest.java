package com.paytech.platform.support.auto.ext.http.client;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientDeveloperPermissions;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientInvitationAcceptance;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientTeamPermissions;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengeStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsMemberStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantInvitationStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantMemberType;
import com.paytech.platform.support.api.authentication.response.ClientWorkspacesResponseBody;
import com.paytech.platform.support.api.clientinvitation.ClientInvitationApi;
import com.paytech.platform.support.api.clientinvitation.request.ClientInvitationProofRequestBody;
import com.paytech.platform.support.api.clientinvitation.response.ClientInvitationAcceptanceResponseBody;
import com.paytech.platform.support.api.clientinvitation.response.ClientInvitationDetailsResponseBody;
import com.paytech.platform.support.api.clientinvitation.response.ClientInvitationPreviewResponseBody;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.api.enums.ClientTeamErrorCode;
import com.paytech.platform.support.api.enums.ClientTeamRole;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.test.base.ClientTeamTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.ClientWorkspaceMocker.Workspace;

/**
 * 被邀请人凭链接预览、查看详情与接受邀请：加入后的权限、重复接受幂等、邮箱不一致、秘密错误计次、工作区不可用与并发接受。
 *
 * @see ClientInvitationApi#preview(ClientInvitationProofRequestBody)
 * @see ClientInvitationApi#details(String, String, ClientInvitationProofRequestBody)
 * @see ClientInvitationApi#accept(String, String, ClientInvitationProofRequestBody)
 */
class ClientInvitationAcceptanceTest extends ClientTeamTestBase {

    /** 已有账号：预览不含工作区，详情邮箱一致附 Client；接受 JOINED，获得初始角色及其权限，工作区列表可见；重复接受 ALREADY_MEMBER。 */
    @Test
    void test_normalFlow() {
        var workspace = teamWorkspace();
        String email = email("invitee");
        var created = invite(workspace.owner(), email, ClientTeamRole.TENANT_DEVELOPER);
        var link = invitationLink(email);
        var invitee = account(email);

        var previewed = preview(link).assertOk();
        var preview = previewed.as(ClientInvitationPreviewResponseBody.class);
        var details = details(invitee, link).assertOk().as(ClientInvitationDetailsResponseBody.class);
        var accepted = accept(invitee, link).assertOk().as(ClientInvitationAcceptanceResponseBody.class);
        String acceptRequest = requestId;

        var event = auditChecker.assertEvent(acceptRequest, "CLIENT_TEAM_INVITATION_ACCEPT",
            AuditModels.Result.SUCCEEDED.getCode(), invitee.actor(), "TENANT_INVITATION", created.getInvitationId());
        auditChecker.assertNoSecret(event, link[1]);
        long memberId = Long.parseLong(accepted.getMemberId());
        var invitation = teamData.invitations(workspace.tenantId()).getFirst();
        assertAll(() -> assertEquals(created.getEmailMasked(), preview.getEmailMasked()),
            () -> assertEquals(ClientTeamRole.TENANT_DEVELOPER.getCode(), preview.getRoleCode()),
            () -> assertEquals(TenantInvitationStatus.PENDING.getCode(), preview.getStatus()),
            () -> assertFalse(previewed.text().contains("clientId")),
            () -> assertTrue(details.isEmailMatches()),
            () -> assertEquals(accepted.getClientId(), details.getClientId()),
            () -> assertEquals(ClientInvitationAcceptance.JOINED, accepted.getOutcome()),
            () -> assertEquals(Long.toString(workspace.tenantId()), accepted.getTenantId()),
            () -> assertEquals(List.of(ClientTeamRole.TENANT_DEVELOPER.getCode()), teamData.activeRoles(memberId)),
            () -> assertEquals(TenantInvitationStatus.ACCEPTED.getCode(), invitation.get("status")),
            () -> assertEquals(invitee.accountId(), ((Number) invitation.get("accepted_account_id")).longValue()),
            () -> assertEquals(memberId, ((Number) invitation.get("accepted_member_id")).longValue()),
            () -> assertNull(invitation.get("active_slot")),
            () -> assertEquals(ClientChallengeStatus.CONSUMED.getCode(),
                teamData.challenges(Long.parseLong(created.getInvitationId())).getFirst().get("status")));

        var member = new Workspace(invitee.accountId(), workspaces.tenantSession(invitee.accountId(), workspace.tenantId(),
            memberId), workspace.tenantId(), memberId);
        assertEquals(List.of(ClientTeamPermissions.TEAM_READ, ClientDeveloperPermissions.API_CREDENTIAL_MANAGE),
            granted(member, ClientTeamPermissions.TEAM_READ, ClientTeamPermissions.TEAM_MANAGE,
                ClientDeveloperPermissions.API_CREDENTIAL_MANAGE));
        var listed = get(invitee, "/authentication/v1/client/workspaces").assertOk()
            .as(ClientWorkspacesResponseBody.class).getItems();
        assertEquals(List.of(accepted.getMemberId()), listed.stream().map(item -> item.getMemberId()).toList());

        var again = accept(invitee, link).assertOk().as(ClientInvitationAcceptanceResponseBody.class);
        assertEquals(ClientInvitationAcceptance.ALREADY_MEMBER, again.getOutcome());
        assertEquals(accepted.getMemberId(), again.getMemberId());
        assertEquals(TenantInvitationStatus.ACCEPTED.getCode(), preview(link).assertOk()
            .as(ClientInvitationPreviewResponseBody.class).getStatus());
        assertEquals(2, teamData.members(workspace.tenantId()).size());
    }

    /** 本账号已是 ACTIVE 成员时接受：ALREADY_MEMBER，完成邀请但不新增成员或授予。 */
    @Test
    void test_alreadyActiveMember() {
        var workspace = teamWorkspace();
        String email = email("invitee");
        loginEmailVault.allow(email);
        ses.accept(email, "ses-invitation-existing");
        createInvitation(workspace.owner(), email, ClientTeamRole.TENANT_VIEWER.getCode()).assertStatus(201);
        var link = invitationLink(email);
        var member = workspaces.teamMember(workspace, ClientTeamRole.TENANT_DEVELOPER, email);
        var session = new Workspace(member.accountId(), workspaces.accountSession(member.accountId()), 0, 0);

        var accepted = accept(session, link).assertOk().as(ClientInvitationAcceptanceResponseBody.class);

        assertAll(() -> assertEquals(ClientInvitationAcceptance.ALREADY_MEMBER, accepted.getOutcome()),
            () -> assertEquals(Long.toString(member.memberId()), accepted.getMemberId()),
            () -> assertEquals(List.of(ClientTeamRole.TENANT_DEVELOPER.getCode()), teamData.activeRoles(member.memberId())),
            () -> assertEquals(TenantInvitationStatus.ACCEPTED.getCode(),
                teamData.invitations(workspace.tenantId()).getFirst().get("status")));
    }

    /** 邮箱不一致：详情 emailMatches=false 且无 Client；接受 403，不显示受邀邮箱原值，邀请保持 PENDING。 */
    @Test
    void test_emailMismatch() {
        var workspace = teamWorkspace();
        String email = email("invitee");
        invite(workspace.owner(), email, ClientTeamRole.TENANT_VIEWER);
        var link = invitationLink(email);
        var stranger = account(email("stranger"));

        var details = details(stranger, link).assertOk().as(ClientInvitationDetailsResponseBody.class);
        var rejected = accept(stranger, link).assertProblem(403, ClientTeamErrorCode.CLIENT_INVITATION_EMAIL_MISMATCH);

        assertAll(() -> assertFalse(details.isEmailMatches()), () -> assertNull(details.getClientId()),
            () -> assertFalse(rejected.text().contains(email)),
            () -> assertEquals(TenantInvitationStatus.PENDING.getCode(),
                teamData.invitations(workspace.tenantId()).getFirst().get("status")),
            () -> assertEquals(1, teamData.members(workspace.tenantId()).size()));
    }

    /** 秘密错误统一 404 并计次；满 5 次挑战 REVOKED，之后正确秘密也 404；格式错误不计次。 */
    @Test
    void test_invalidSecret() {
        var workspace = teamWorkspace();
        String email = email("invitee");
        var created = invite(workspace.owner(), email, ClientTeamRole.TENANT_VIEWER);
        var link = invitationLink(email);
        String[] wrong = {link[0], "A".repeat(43)};

        preview(new String[] {link[0], "short"}).assertProblem(404, ClientTeamErrorCode.CLIENT_INVITATION_INVALID);
        for (int attempt = 1; attempt <= 4; attempt++) {
            preview(wrong).assertProblem(404, ClientTeamErrorCode.CLIENT_INVITATION_INVALID);
        }
        long invitationId = Long.parseLong(created.getInvitationId());
        assertEquals(4, ((Number) teamData.challenges(invitationId).getFirst().get("used_attempts")).intValue());
        accept(account(email), wrong).assertProblem(404, ClientTeamErrorCode.CLIENT_INVITATION_INVALID);

        assertEquals(ClientChallengeStatus.REVOKED.getCode(), teamData.challenges(invitationId).getFirst().get("status"));
        preview(link).assertProblem(404, ClientTeamErrorCode.CLIENT_INVITATION_INVALID);
        call(HttpMethod.POST, INVITATIONS + "/preview", headers(null, null),
            new ClientInvitationProofRequestBody("", link[1])).assertProblem(422, CommonErrorCode.VALIDATION_ERROR);
    }

    /** 工作区暂停或终止时不能接受：403 CLIENT_WORKSPACE_UNAVAILABLE，不创建成员，邀请保持 PENDING。 */
    @Test
    void test_workspaceUnavailable() {
        var workspace = teamWorkspace();
        String email = email("invitee");
        invite(workspace.owner(), email, ClientTeamRole.TENANT_VIEWER);
        var link = invitationLink(email);
        operationsData.updateTenantStatus(workspace.tenantId(), OperationsTenantOwnerStatus.SUSPENDED.getCode(),
            OperationsTenantSecurityStatus.ACTIVE.getCode());

        accept(account(email), link).assertProblem(403, ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE);

        assertEquals(1, teamData.members(workspace.tenantId()).size());
        assertEquals(TenantInvitationStatus.PENDING.getCode(),
            teamData.invitations(workspace.tenantId()).getFirst().get("status"));
    }

    /** 会话无效：详情与接受 401，公开预览不需要会话。 */
    @Test
    void test_invalidSession() {
        var workspace = teamWorkspace();
        String email = email("invitee");
        invite(workspace.owner(), email, ClientTeamRole.TENANT_VIEWER);
        var link = invitationLink(email);
        var invitee = account(email);
        var forged = new Workspace(invitee.accountId(), invitee.sessionId() + 1, 0, 0);

        details(forged, link).assertProblem(401, ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        accept(forged, link).assertProblem(401, ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);

        preview(link).assertOk();
        assertEquals(1, teamData.members(workspace.tenantId()).size());
    }

    /** 同一邀请并发接受两次：只产生一个成员和一份角色授予，其余请求为 ALREADY_MEMBER 或 409。 */
    @Test
    void test_concurrentAccept() throws Exception {
        var workspace = teamWorkspace();
        String email = email("invitee");
        invite(workspace.owner(), email, ClientTeamRole.TENANT_VIEWER);
        var link = invitationLink(email);
        var invitee = account(email);
        var start = new CountDownLatch(1);
        var calls = new ArrayList<CompletableFuture<PlatformResponseChecker>>();
        for (int index = 0; index < 2; index++) {
            calls.add(CompletableFuture.supplyAsync(() -> {
                await(start);
                return accept(invitee, link);
            }));
        }

        start.countDown();

        var statuses = new ArrayList<Integer>();
        for (var call : calls) {
            statuses.add(call.get(30, TimeUnit.SECONDS).status());
        }
        var members = teamData.members(workspace.tenantId());
        var joined = members.stream()
            .filter(row -> ((Number) row.get("account_id")).longValue() == invitee.accountId()).toList();
        assertAll(() -> assertTrue(statuses.contains(200), "至少一个接受成功：" + statuses),
            () -> assertTrue(statuses.stream().allMatch(status -> status == 200 || status == 409), "其余只能是 409：" + statuses),
            () -> assertEquals(2, members.size()),
            () -> assertEquals(1, joined.size()),
            () -> assertEquals(OperationsMemberStatus.ACTIVE.getCode(), joined.getFirst().get("status")),
            () -> assertEquals(TenantMemberType.MEMBER.getCode(), joined.getFirst().get("member_type")),
            () -> assertEquals(List.of(ClientTeamRole.TENANT_VIEWER.getCode()),
                teamData.activeRoles(((Number) joined.getFirst().get("member_id")).longValue())));
    }

    /** 等待并发起点。 */
    static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
