package com.paytech.platform.support.auto.ext.http.client;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientTeamRules;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TenantInvitationResult;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengePurpose;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengeStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantInvitationStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantMemberType;
import com.paytech.platform.support.api.clientinvitation.response.ClientInvitationPreviewResponseBody;
import com.paytech.platform.support.api.clientteam.ClientTeamApi;
import com.paytech.platform.support.api.clientteam.response.ClientTeamInvitationResponseBody;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.api.enums.ClientTeamErrorCode;
import com.paytech.platform.support.api.enums.ClientTeamRole;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.test.base.ClientTeamTestBase;

import tools.jackson.core.type.TypeReference;

/**
 * 发起、列表、重发与撤销工作区邀请：邮件链接、重复与上限、旧链接失效、到期、邮件失败、工作区不可用、权限与隔离。
 *
 * @see ClientTeamApi#createInvitation(String, String, com.paytech.platform.support.api.clientteam.request.CreateClientTeamInvitationRequestBody)
 * @see ClientTeamApi#invitations(String, String, String, Integer, Integer)
 * @see ClientTeamApi#resendInvitation(String, String, String)
 * @see ClientTeamApi#revokeInvitation(String, String, String)
 */
class ClientTeamInvitationTest extends ClientTeamTestBase {

    private static final TypeReference<PagingResponseBody<ClientTeamInvitationResponseBody>> PAGE = new TypeReference<>() {};

    /** 所有者发起：201、PENDING、7 天到期、邮件含接受链接；库中只有 Token 与 HMAC；列表显示脱敏邮箱与发起人。 */
    @Test
    void test_normalFlow() {
        var workspace = teamWorkspace();
        String email = email("invitee");

        var created = invite(workspace.owner(), email, ClientTeamRole.TENANT_DEVELOPER);

        String createRequest = requestId;
        var link = invitationLink(email);
        var event = auditChecker.assertEvent(createRequest, "CLIENT_TEAM_INVITATION_CREATE",
            AuditModels.Result.SUCCEEDED.getCode(), workspace.owner().actor(), "TENANT_INVITATION", created.getInvitationId());
        auditChecker.assertNoSecret(event, email);
        auditChecker.assertNoSecret(event, link[1]);
        var rows = teamData.invitations(workspace.tenantId());
        var challenges = teamData.challenges(Long.parseLong(created.getInvitationId()));
        assertAll(() -> assertEquals(TenantInvitationStatus.PENDING.getCode(), created.getStatus()),
            () -> assertEquals(ClientTeamRole.TENANT_DEVELOPER.getCode(), created.getRoleCode()),
            () -> assertEquals(TenantInvitationResult.EMAIL_SENT, created.getEmailDelivery()),
            () -> assertEquals(email.charAt(0) + "***@example.test", created.getEmailMasked()),
            () -> assertEquals(ClientTeamRules.INVITATION_VALIDITY, Duration.between(Instant.parse(created.getCreatedAt()),
                Instant.parse(created.getExpiresAt()))),
            () -> assertEquals(1, rows.size()),
            () -> assertEquals(TenantMemberType.MEMBER.getCode(), rows.getFirst().get("member_type")),
            () -> assertEquals(1, ((Number) rows.getFirst().get("active_slot")).intValue()),
            () -> assertNotEquals(email, rows.getFirst().get("invited_email_vault_token")),
            () -> assertEquals(1, challenges.size()),
            () -> assertEquals(ClientChallengePurpose.TENANT_INVITATION_ACCEPT.getCode(), challenges.getFirst().get("purpose")),
            () -> assertNull(challenges.getFirst().get("account_id")),
            () -> assertEquals(link[0], challenges.getFirst().get("challenge_no")),
            () -> assertNotEquals(link[1], challenges.getFirst().get("secret_hmac")),
            () -> assertEquals(rows.getFirst().get("expires_time"), challenges.getFirst().get("expires_time")));

        var page = get(workspace.owner(), TEAM + "/invitations").assertOk();
        assertFalse(page.text().contains(email), "列表不返回邮箱原值");
        var items = page.as(PAGE).items();
        assertAll(() -> assertEquals(1, items.size()),
            () -> assertEquals(created.getInvitationId(), items.getFirst().getInvitationId()),
            () -> assertEquals("o***@example.test", items.getFirst().getInvitedByEmailMasked()),
            () -> assertNull(items.getFirst().getEmailDelivery()));
        assertEquals(TenantInvitationStatus.PENDING.getCode(), preview(link).assertOk()
            .as(ClientInvitationPreviewResponseBody.class).getStatus());
        assertNothingPersisted();
    }

    /** 同邮箱已有未到期邀请 409 DUPLICATE；已是 ACTIVE 成员 409 MEMBER_EXISTS；均不产生第二条邀请与邮件。 */
    @Test
    void test_duplicateAndExistingMember() {
        var workspace = teamWorkspace();
        String email = email("invitee");
        invite(workspace.owner(), email, ClientTeamRole.TENANT_VIEWER);
        String memberEmail = email("member");
        workspaces.teamMember(workspace, ClientTeamRole.TENANT_DEVELOPER, memberEmail);
        loginEmailVault.allow(memberEmail);

        createInvitation(workspace.owner(), email, ClientTeamRole.TENANT_ADMIN.getCode())
            .assertProblem(409, ClientTeamErrorCode.CLIENT_INVITATION_DUPLICATE);
        createInvitation(workspace.owner(), memberEmail, ClientTeamRole.TENANT_VIEWER.getCode())
            .assertProblem(409, ClientTeamErrorCode.CLIENT_MEMBER_EXISTS);

        assertEquals(1, teamData.invitations(workspace.tenantId()).size());
        assertEquals(1, ses.requests().size());
    }

    /** 未到期 PENDING 最多 20 条，第 21 条 409 LIMIT_REACHED。 */
    @Test
    void test_limitReached() {
        var workspace = teamWorkspace();
        for (int index = 0; index < ClientTeamRules.PENDING_LIMIT; index++) {
            invite(workspace.owner(), email("invitee" + index), ClientTeamRole.TENANT_VIEWER);
        }
        String extra = email("extra");
        loginEmailVault.allow(extra);

        createInvitation(workspace.owner(), extra, ClientTeamRole.TENANT_VIEWER.getCode())
            .assertProblem(409, ClientTeamErrorCode.CLIENT_INVITATION_LIMIT_REACHED);

        assertEquals(ClientTeamRules.PENDING_LIMIT, teamData.invitations(workspace.tenantId()).size());
    }

    /** 重发：同一邀请重新计算到期，旧挑战 SUPERSEDED、旧链接 404，新链接可用；已撤销后重发 409 CLOSED。 */
    @Test
    void test_resendInvalidatesOldLink() {
        var workspace = teamWorkspace();
        String email = email("invitee");
        var created = invite(workspace.owner(), email, ClientTeamRole.TENANT_DEVELOPER);
        var oldLink = invitationLink(email);

        var resent = resend(workspace.owner(), created.getInvitationId(), email).assertOk()
            .as(ClientTeamInvitationResponseBody.class);

        var newLink = invitationLink(email);
        var challenges = teamData.challenges(Long.parseLong(created.getInvitationId()));
        assertAll(() -> assertEquals(created.getInvitationId(), resent.getInvitationId()),
            () -> assertEquals(TenantInvitationResult.EMAIL_SENT, resent.getEmailDelivery()),
            () -> assertFalse(Instant.parse(resent.getExpiresAt()).isBefore(Instant.parse(created.getExpiresAt()))),
            () -> assertNotEquals(oldLink[1], newLink[1]),
            () -> assertEquals(2, challenges.size()),
            () -> assertEquals(ClientChallengeStatus.SUPERSEDED.getCode(), challenges.get(0).get("status")),
            () -> assertEquals(ClientChallengeStatus.PENDING.getCode(), challenges.get(1).get("status")),
            () -> assertEquals(challenges.get(0).get("challenge_id"), challenges.get(1).get("supersedes_challenge_id")));
        preview(oldLink).assertProblem(404, ClientTeamErrorCode.CLIENT_INVITATION_INVALID);
        preview(newLink).assertOk();
        assertEquals(1, teamData.invitations(workspace.tenantId()).size());

        revoke(workspace.owner(), created.getInvitationId()).assertOk();
        post(workspace.owner(), TEAM + "/invitations/" + created.getInvitationId() + "/resend")
            .assertProblem(409, ClientTeamErrorCode.CLIENT_INVITATION_CLOSED);
    }

    /** 撤销：邀请与挑战 REVOKED，预览显示 REVOKED 不含工作区信息，接受 409；再次撤销返回原结果。 */
    @Test
    void test_revoke() {
        var workspace = teamWorkspace();
        String email = email("invitee");
        var created = invite(workspace.owner(), email, ClientTeamRole.TENANT_VIEWER);
        var link = invitationLink(email);
        var invitee = account(email);

        var revoked = revoke(workspace.owner(), created.getInvitationId()).assertOk()
            .as(ClientTeamInvitationResponseBody.class);

        assertEquals(TenantInvitationStatus.REVOKED.getCode(), revoked.getStatus());
        assertEquals(ClientChallengeStatus.REVOKED.getCode(),
            teamData.challenges(Long.parseLong(created.getInvitationId())).getFirst().get("status"));
        var previewed = preview(link).assertOk();
        assertEquals(TenantInvitationStatus.REVOKED.getCode(),
            previewed.as(ClientInvitationPreviewResponseBody.class).getStatus());
        assertFalse(previewed.text().contains("clientId") || previewed.text().contains("tenantId"));
        accept(invitee, link).assertProblem(409, ClientTeamErrorCode.CLIENT_INVITATION_CLOSED);
        assertEquals(TenantInvitationStatus.REVOKED.getCode(), revoke(workspace.owner(), created.getInvitationId())
            .assertOk().as(ClientTeamInvitationResponseBody.class).getStatus());
        assertEquals(1, get(workspace.owner(), TEAM + "/invitations?status=" + TenantInvitationStatus.REVOKED.getCode())
            .assertOk().as(PAGE).items().size());
    }

    /** 到期：列表 PENDING 不含、EXPIRED 含；预览 EXPIRED；接受 409；同邮箱重新邀请时旧邀请置为 EXPIRED。 */
    @Test
    void test_expired() {
        var workspace = teamWorkspace();
        String email = email("invitee");
        var created = invite(workspace.owner(), email, ClientTeamRole.TENANT_VIEWER);
        var link = invitationLink(email);
        long invitationId = Long.parseLong(created.getInvitationId());
        Instant past = operationsMocker.now().minusSeconds(1);
        assertEquals(1, teamData.expireInvitation(invitationId, past));
        assertEquals(1, teamData.expireChallenges(invitationId, past));

        assertTrue(get(workspace.owner(), TEAM + "/invitations").assertOk().as(PAGE).items().isEmpty());
        var expired = get(workspace.owner(), TEAM + "/invitations?status=" + TenantInvitationStatus.EXPIRED.getCode())
            .assertOk().as(PAGE).items();
        assertEquals(TenantInvitationStatus.EXPIRED.getCode(), expired.getFirst().getStatus());
        assertEquals(TenantInvitationStatus.EXPIRED.getCode(), preview(link).assertOk()
            .as(ClientInvitationPreviewResponseBody.class).getStatus());
        accept(account(email), link).assertProblem(409, ClientTeamErrorCode.CLIENT_INVITATION_CLOSED);

        var again = invite(workspace.owner(), email, ClientTeamRole.TENANT_ADMIN);

        var rows = teamData.invitations(workspace.tenantId());
        assertAll(() -> assertEquals(2, rows.size()),
            () -> assertEquals(TenantInvitationStatus.EXPIRED.getCode(), rows.get(0).get("status")),
            () -> assertNull(rows.get(0).get("active_slot")),
            () -> assertEquals(again.getInvitationId(), rows.get(1).get("invitation_id").toString()),
            () -> assertEquals(TenantMemberType.ADMIN.getCode(), rows.get(1).get("member_type")));
    }

    /** 已到期但仍 PENDING 的邀请可以重发，重新获得 7 天有效期。 */
    @Test
    void test_resendExpired() {
        var workspace = teamWorkspace();
        String email = email("invitee");
        var created = invite(workspace.owner(), email, ClientTeamRole.TENANT_VIEWER);
        long invitationId = Long.parseLong(created.getInvitationId());
        Instant past = operationsMocker.now().minusSeconds(1);
        teamData.expireInvitation(invitationId, past);
        teamData.expireChallenges(invitationId, past);

        var resent = resend(workspace.owner(), created.getInvitationId(), email).assertOk()
            .as(ClientTeamInvitationResponseBody.class);

        assertEquals(TenantInvitationStatus.PENDING.getCode(), resent.getStatus());
        assertEquals(TenantInvitationStatus.PENDING.getCode(), preview(invitationLink(email)).assertOk()
            .as(ClientInvitationPreviewResponseBody.class).getStatus());
    }

    /** 邮件发送失败：邀请仍创建为 PENDING，响应 emailDelivery=FAILED，可重发。 */
    @Test
    void test_emailDeliveryFailed() {
        var workspace = teamWorkspace();
        String email = email("invitee");
        loginEmailVault.allow(email);
        ses.fail(email, 400, "MessageRejected", "synthetic rejection");

        var created = createInvitation(workspace.owner(), email, ClientTeamRole.TENANT_VIEWER.getCode()).assertStatus(201)
            .as(ClientTeamInvitationResponseBody.class);

        assertEquals(TenantInvitationResult.EMAIL_FAILED, created.getEmailDelivery());
        assertEquals(TenantInvitationStatus.PENDING.getCode(), teamData.invitations(workspace.tenantId()).getFirst()
            .get("status"));
        assertEquals(TenantInvitationResult.EMAIL_SENT, resend(workspace.owner(), created.getInvitationId(), email)
            .assertOk().as(ClientTeamInvitationResponseBody.class).getEmailDelivery());
    }

    /** 工作区被暂停后，所有者的租户会话重证失败（与其他工作区接口一致返回 401），不创建邀请、不调用 Vault。 */
    @Test
    void test_workspaceSuspended() {
        var workspace = teamWorkspace();
        operationsData.updateTenantStatus(workspace.tenantId(), OperationsTenantOwnerStatus.ACTIVE.getCode(),
            OperationsTenantSecurityStatus.SUSPENDED.getCode());

        createInvitation(workspace.owner(), email("invitee"), ClientTeamRole.TENANT_VIEWER.getCode())
            .assertProblem(401, ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);

        assertTrue(teamData.invitations(workspace.tenantId()).isEmpty());
        assertEquals(0, loginEmailVault.protectCount());
    }

    /** 无 TEAM_MANAGE 403；其他工作区的邀请 404；角色与状态参数校验；所有者角色不可邀请。 */
    @Test
    void test_permissionAndIsolation() {
        var workspace = teamWorkspace();
        var other = teamWorkspace();
        String email = email("invitee");
        var foreign = invite(other.owner(), email, ClientTeamRole.TENANT_VIEWER);
        var developer = workspaces.teamMember(workspace, ClientTeamRole.TENANT_DEVELOPER, email("developer"));

        createInvitation(developer, email("x"), ClientTeamRole.TENANT_VIEWER.getCode())
            .assertProblem(403, ClientAuthenticationErrorCode.CLIENT_PERMISSION_DENIED);
        get(developer, TEAM + "/invitations").assertProblem(403, ClientAuthenticationErrorCode.CLIENT_PERMISSION_DENIED);
        post(workspace.owner(), TEAM + "/invitations/" + foreign.getInvitationId() + "/resend")
            .assertProblem(404, CommonErrorCode.NOT_FOUND);
        revoke(workspace.owner(), foreign.getInvitationId()).assertProblem(404, CommonErrorCode.NOT_FOUND);
        revoke(workspace.owner(), "not-a-number").assertProblem(404, CommonErrorCode.NOT_FOUND);
        createInvitation(workspace.owner(), email("y"), ClientTeamRole.TENANT_OWNER.getCode())
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR);
        createInvitation(workspace.owner(), "not-an-email", ClientTeamRole.TENANT_VIEWER.getCode())
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(workspace.owner(), TEAM + "/invitations?status=UNKNOWN").assertProblem(400, CommonErrorCode.INVALID_REQUEST);

        assertTrue(teamData.invitations(workspace.tenantId()).isEmpty());
        assertEquals(TenantInvitationStatus.PENDING.getCode(), teamData.invitations(other.tenantId()).getFirst()
            .get("status"));
    }
}
