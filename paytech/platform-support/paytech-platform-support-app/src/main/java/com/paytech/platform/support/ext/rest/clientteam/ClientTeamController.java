package com.paytech.platform.support.ext.rest.clientteam;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.service.processor.ChangeClientTeamMemberRoleProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.CreateClientTeamInvitationProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.ListClientTeamInvitationsProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.ListClientTeamMembersProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.RemoveClientTeamMemberProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.ResendClientTeamInvitationProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.RevokeClientTeamInvitationProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientTeamRequest;
import com.paytech.platform.support.api.clientteam.ClientTeamApi;
import com.paytech.platform.support.api.clientteam.request.ChangeClientTeamMemberRoleRequestBody;
import com.paytech.platform.support.api.clientteam.request.CreateClientTeamInvitationRequestBody;
import com.paytech.platform.support.api.clientteam.response.ClientTeamInvitationResponseBody;
import com.paytech.platform.support.api.clientteam.response.ClientTeamMemberResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.ext.rest.clientdeveloper.ClientDeveloperConverter;

import lombok.AllArgsConstructor;

/** 私网工作区团队管理入口；协议转换后委托用例。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ClientTeamController implements ClientTeamApi {
    private final ClientTeamConverter converter;
    private final ClientDeveloperConverter identifiers;
    private final ListClientTeamMembersProcessor membersProcessor;
    private final RemoveClientTeamMemberProcessor removeProcessor;
    private final ResendClientTeamInvitationProcessor resendProcessor;
    private final RevokeClientTeamInvitationProcessor revokeProcessor;
    private final CreateClientTeamInvitationProcessor createProcessor;
    private final ListClientTeamInvitationsProcessor invitationsProcessor;
    private final ChangeClientTeamMemberRoleProcessor changeRoleProcessor;

    /** 成员列表。 */
    @Override
    public PagingResponseBody<ClientTeamMemberResponseBody> members(String accountId, String sessionId, Integer page,
        Integer size) {
        return converter.toMembers(membersProcessor.process(
            request(accountId, sessionId, null, null, null, null, PagingRequestBody.of(page, size))));
    }

    /** 邀请列表。 */
    @Override
    public PagingResponseBody<ClientTeamInvitationResponseBody> invitations(String accountId, String sessionId,
        String status, Integer page, Integer size) {
        return converter.toInvitations(invitationsProcessor.process(
            request(accountId, sessionId, null, null, null, status, PagingRequestBody.of(page, size))));
    }

    /** 发起邀请；201。 */
    @Override
    @Audit(action = "CLIENT_TEAM_INVITATION_CREATE", resourceType = "TENANT_INVITATION", fallbackResourceId = "NEW")
    public ResponseEntity<ClientTeamInvitationResponseBody> createInvitation(String accountId, String sessionId,
        CreateClientTeamInvitationRequestBody request) {
        var result = createProcessor.process(request(accountId, sessionId, null, request.getEmail(),
            request.getRoleCode(), null, null));
        return ResponseEntity.status(HttpStatus.CREATED).body(converter.toInvitation(result));
    }

    /** 重发邀请。 */
    @Override
    @Audit(action = "CLIENT_TEAM_INVITATION_RESEND", resourceType = "TENANT_INVITATION",
        resourceIdParameter = "invitationId")
    public ClientTeamInvitationResponseBody resendInvitation(String accountId, String sessionId, String invitationId) {
        return converter.toInvitation(resendProcessor.process(request(accountId, sessionId,
            identifiers.pathId(invitationId), null, null, null, null)));
    }

    /** 撤销邀请。 */
    @Override
    @Audit(action = "CLIENT_TEAM_INVITATION_REVOKE", resourceType = "TENANT_INVITATION",
        resourceIdParameter = "invitationId")
    public ClientTeamInvitationResponseBody revokeInvitation(String accountId, String sessionId, String invitationId) {
        return converter.toInvitation(revokeProcessor.process(request(accountId, sessionId,
            identifiers.pathId(invitationId), null, null, null, null)));
    }

    /** 调整成员角色。 */
    @Override
    @Audit(action = "CLIENT_TEAM_MEMBER_ROLE_CHANGE", resourceType = "TENANT_MEMBER", resourceIdParameter = "memberId")
    public ClientTeamMemberResponseBody changeMemberRole(String accountId, String sessionId, String memberId,
        ChangeClientTeamMemberRoleRequestBody request) {
        return converter.toMember(changeRoleProcessor.process(request(accountId, sessionId,
            identifiers.pathId(memberId), null, request.getRoleCode(), null, null)), false);
    }

    /** 移除成员。 */
    @Override
    @Audit(action = "CLIENT_TEAM_MEMBER_REMOVE", resourceType = "TENANT_MEMBER", resourceIdParameter = "memberId")
    public ClientTeamMemberResponseBody removeMember(String accountId, String sessionId, String memberId) {
        return converter.toMember(removeProcessor.process(request(accountId, sessionId, identifiers.pathId(memberId),
            null, null, null, null)), false);
    }

    /** 解析可信会话并组装内部请求。 */
    ClientTeamRequest request(String accountId, String sessionId, Long targetId, String email, String roleCode,
        String status, PagingRequestBody paging) {
        return new ClientTeamRequest(identifiers.session(accountId), identifiers.session(sessionId), targetId, email,
            roleCode, status, paging);
    }
}
