package com.paytech.client.console.bff.ext.rest;

import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.clientteam.ClientTeamApi;
import com.paytech.platform.support.api.clientteam.request.ChangeClientTeamMemberRoleRequestBody;
import com.paytech.platform.support.api.clientteam.request.CreateClientTeamInvitationRequestBody;
import com.paytech.platform.support.api.clientteam.response.ClientTeamInvitationResponseBody;
import com.paytech.platform.support.api.clientteam.response.ClientTeamMemberResponseBody;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作区“团队”接口的薄转发：成员与邀请管理
 * <li>租户上下文与权限由 {@code ClientTeamWebConfiguration} 先行判断；租户由 Platform 从会话解析。</li>
 * <li>只透传 Platform 合同与状态码；请求含邮箱，日志按 metadata-only 前缀处理。</li>
 */
@RestController
@lombok.AllArgsConstructor
@RequestMapping("/client-team/v1")
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientTeamController {

    private final ClientTeamApi team;

    /** T01 成员列表（CLIENT_TEAM_READ）。 */
    @GetMapping("/members")
    public PagingResponseBody<ClientTeamMemberResponseBody> members(ClientActor actor,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return team.members(actor.getAccountId(), actor.getSessionId(), page, size);
    }

    /** T02 邀请列表（CLIENT_TEAM_MANAGE）。 */
    @GetMapping("/invitations")
    public PagingResponseBody<ClientTeamInvitationResponseBody> invitations(ClientActor actor,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return team.invitations(actor.getAccountId(), actor.getSessionId(), status, page, size);
    }

    /** T03 发起邀请；透传 201 与 emailDelivery。 */
    @PostMapping("/invitations")
    public ResponseEntity<ClientTeamInvitationResponseBody> createInvitation(ClientActor actor,
        @Valid @RequestBody CreateClientTeamInvitationRequestBody body) {
        return ClientDeveloperController.relay(team.createInvitation(actor.getAccountId(), actor.getSessionId(), body));
    }

    /** T04 重发邀请；无 Body。 */
    @PostMapping("/invitations/{invitationId}/resend")
    public ClientTeamInvitationResponseBody resendInvitation(ClientActor actor, @PathVariable String invitationId) {
        return team.resendInvitation(actor.getAccountId(), actor.getSessionId(), invitationId);
    }

    /** T05 撤销邀请；无 Body。 */
    @PostMapping("/invitations/{invitationId}/revocation")
    public ClientTeamInvitationResponseBody revokeInvitation(ClientActor actor, @PathVariable String invitationId) {
        return team.revokeInvitation(actor.getAccountId(), actor.getSessionId(), invitationId);
    }

    /** T06 调整成员角色。 */
    @PostMapping("/members/{memberId}/role")
    public ClientTeamMemberResponseBody changeMemberRole(ClientActor actor, @PathVariable String memberId,
        @Valid @RequestBody ChangeClientTeamMemberRoleRequestBody body) {
        return team.changeMemberRole(actor.getAccountId(), actor.getSessionId(), memberId, body);
    }

    /** T07 移除成员；无 Body。 */
    @PostMapping("/members/{memberId}/removal")
    public ClientTeamMemberResponseBody removeMember(ClientActor actor, @PathVariable String memberId) {
        return team.removeMember(actor.getAccountId(), actor.getSessionId(), memberId);
    }
}
