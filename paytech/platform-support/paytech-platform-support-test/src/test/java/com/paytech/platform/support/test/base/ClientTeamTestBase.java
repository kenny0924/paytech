package com.paytech.platform.support.test.base;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.platform.support.api.authorization.request.ClientAuthorizationRequestBody;
import com.paytech.platform.support.api.authorization.response.ClientAuthorizationResponseBody;
import com.paytech.platform.support.api.clientinvitation.request.ClientInvitationProofRequestBody;
import com.paytech.platform.support.api.clientteam.request.ChangeClientTeamMemberRoleRequestBody;
import com.paytech.platform.support.api.clientteam.request.CreateClientTeamInvitationRequestBody;
import com.paytech.platform.support.api.clientteam.response.ClientTeamInvitationResponseBody;
import com.paytech.platform.support.api.enums.ClientTeamRole;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.dal.ClientTeamTestMapper;
import com.paytech.platform.support.test.mocker.external.SesMocker;
import com.paytech.platform.support.test.mocker.local.ClientTeamFixture;
import com.paytech.platform.support.test.mocker.local.ClientWorkspaceMocker;
import com.paytech.platform.support.test.mocker.local.ClientWorkspaceMocker.TeamWorkspace;
import com.paytech.platform.support.test.mocker.local.ClientWorkspaceMocker.Workspace;

/**
 * 团队与邀请入口测试的公共调用：真实 HTTP → Controller → Processor → 本地 MySQL/Redis；邀请邮件经 Delivery 发到 SES Mock，
 * 从邮件正文取回接受链接
 * <li>类级准备在认证邮件准备之后插入邀请邮件模板；工作区、成员与账号由 ClientWorkspaceMocker 按用例插入。</li>
 */
public abstract class ClientTeamTestBase extends AuthenticationEmailTestBase {

    protected static final String TEAM = "/client-team/v1";

    protected static final String INVITATIONS = "/client-invitations/v1";

    private static final AtomicInteger MESSAGES = new AtomicInteger();

    @Autowired protected ClientWorkspaceMocker workspaces;
    @Autowired protected ClientTeamFixture teamFixture;
    @Autowired protected ClientTeamTestMapper teamData;

    /** 父类认证邮件准备之后插入邀请邮件模板。 */
    @BeforeEach
    void setUpClientTeam() {
        teamFixture.prepareInvitationTemplate();
    }

    /** 本例唯一的合成邮箱。 */
    protected static String email(String name) {
        return name + "-" + OperatorBuilder.nextId() + "@example.test";
    }

    /** 新的团队工作区及其所有者。 */
    protected TeamWorkspace teamWorkspace() {
        return workspaces.teamWorkspace(email("owner"));
    }

    /** 新账号及其账号上下文会话。 */
    protected Workspace account(String email) {
        long accountId = workspaces.customerAccount(email);
        return new Workspace(accountId, workspaces.accountSession(accountId), 0, 0);
    }

    /** 登记 Vault 与 SES 成功响应后发起邀请。 */
    protected ClientTeamInvitationResponseBody invite(Workspace actor, String email, ClientTeamRole role) {
        loginEmailVault.allow(email);
        ses.accept(email, "ses-invitation-" + MESSAGES.incrementAndGet());
        return createInvitation(actor, email, role.getCode()).assertStatus(201).as(ClientTeamInvitationResponseBody.class);
    }

    /** 原样发起邀请请求。 */
    protected PlatformResponseChecker createInvitation(Workspace actor, String email, String roleCode) {
        return call(HttpMethod.POST, TEAM + "/invitations", headers(actor.actor(), actor.session()),
            new CreateClientTeamInvitationRequestBody(email, roleCode));
    }

    /** 以工作区会话 GET。 */
    protected PlatformResponseChecker get(Workspace actor, String path) {
        return call(HttpMethod.GET, path, headers(actor.actor(), actor.session()), null);
    }

    /** 以会话发送空 Body 的 POST。 */
    protected PlatformResponseChecker post(Workspace actor, String path) {
        return call(HttpMethod.POST, path, headers(actor.actor(), actor.session()), null);
    }

    /** 调整成员角色。 */
    protected PlatformResponseChecker changeRole(Workspace actor, long memberId, ClientTeamRole role) {
        return call(HttpMethod.POST, TEAM + "/members/" + memberId + "/role", headers(actor.actor(), actor.session()),
            new ChangeClientTeamMemberRoleRequestBody(role.getCode()));
    }

    /** 移除成员。 */
    protected PlatformResponseChecker remove(Workspace actor, long memberId) {
        return post(actor, TEAM + "/members/" + memberId + "/removal");
    }

    /** 重发邀请（预先登记 Vault 与 SES 成功响应）。 */
    protected PlatformResponseChecker resend(Workspace actor, String invitationId, String email) {
        loginEmailVault.allow(email);
        ses.accept(email, "ses-invitation-" + MESSAGES.incrementAndGet());
        return post(actor, TEAM + "/invitations/" + invitationId + "/resend");
    }

    /** 撤销邀请。 */
    protected PlatformResponseChecker revoke(Workspace actor, String invitationId) {
        return post(actor, TEAM + "/invitations/" + invitationId + "/revocation");
    }

    /** 公开预览，不带账号身份。 */
    protected PlatformResponseChecker preview(String[] link) {
        return call(HttpMethod.POST, INVITATIONS + "/preview", headers(null, null),
            new ClientInvitationProofRequestBody(link[0], link[1]));
    }

    /** 已登录详情。 */
    protected PlatformResponseChecker details(Workspace actor, String[] link) {
        return call(HttpMethod.POST, INVITATIONS + "/details", headers(actor.actor(), actor.session()),
            new ClientInvitationProofRequestBody(link[0], link[1]));
    }

    /** 接受邀请。 */
    protected PlatformResponseChecker accept(Workspace actor, String[] link) {
        return call(HttpMethod.POST, INVITATIONS + "/acceptance", headers(actor.actor(), actor.session()),
            new ClientInvitationProofRequestBody(link[0], link[1]));
    }

    /** 发给该收件人的最近一封邀请邮件中的查找编号与秘密。 */
    protected String[] invitationLink(String email) {
        List<SesMocker.Received> mails = ses.requests().stream().filter(mail -> email.equals(mail.recipient())).toList();
        assertTrue(!mails.isEmpty(), "没有发给受邀邮箱的邮件");
        var mail = mails.getLast();
        assertTrue(mail.subject().equals(ClientTeamFixture.INVITATION_SUBJECT), "不是邀请邮件");
        assertTrue(mail.text().contains(PUBLIC_ORIGIN + "/invitations/accept#challengeNo="), "邀请链接路径不正确");
        return link(mail);
    }

    /** 成员当前已授予的权限码（PLT-I02），按请求顺序。 */
    protected List<String> granted(Workspace member, String... codes) {
        return call(HttpMethod.POST, "/authorization/v1/client/authorize", headers(member.actor(), member.session()),
            new ClientAuthorizationRequestBody(List.of(codes))).assertOk().as(ClientAuthorizationResponseBody.class)
            .getGranted();
    }
}
