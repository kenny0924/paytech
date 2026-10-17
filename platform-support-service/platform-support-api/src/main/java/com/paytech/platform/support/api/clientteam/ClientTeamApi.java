package com.paytech.platform.support.api.clientteam;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.clientteam.request.ChangeClientTeamMemberRoleRequestBody;
import com.paytech.platform.support.api.clientteam.request.CreateClientTeamInvitationRequestBody;
import com.paytech.platform.support.api.clientteam.response.ClientTeamInvitationResponseBody;
import com.paytech.platform.support.api.clientteam.response.ClientTeamMemberResponseBody;

import jakarta.validation.Valid;

/**
 * 工作区成员管理本工作区的成员与邀请；只供 Client BFF 私网调用
 * <li>租户只取自当前有效的 CLIENT 租户上下文会话，不接受调用方指定 Client 或租户。</li>
 * <li>成员列表需要 CLIENT_TEAM_READ；邀请列表与全部写操作需要 CLIENT_TEAM_MANAGE；无权限返回 403 CLIENT_PERMISSION_DENIED。</li>
 * <li>按 ID 的操作不属于当前工作区时返回 404，不透露是否存在。</li>
 * <li>不能修改或移除所有者，也不能修改或移除自己：409 CLIENT_MEMBER_PROTECTED。</li>
 */
@HttpExchange("/client-team/v1")
public interface ClientTeamApi {

    /**
     * 本工作区 ACTIVE 成员
     *
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10
     * @return 所有者在前，其余按加入时间升序
     */
    @GetExchange("/members")
    PagingResponseBody<ClientTeamMemberResponseBody> members(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 本工作区邀请
     *
     * @param status 可选 PENDING（默认，只含未到期）、EXPIRED、ACCEPTED、REVOKED
     * @return 按创建时间倒序
     */
    @GetExchange("/invitations")
    PagingResponseBody<ClientTeamInvitationResponseBody> invitations(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 发起邀请
     * <li>同邮箱已有未到期邀请返回 409 CLIENT_INVITATION_DUPLICATE；已是 ACTIVE 成员返回 409 CLIENT_MEMBER_EXISTS。</li>
     * <li>未到期的 PENDING 邀请最多 20 条，超出返回 409 CLIENT_INVITATION_LIMIT_REACHED。</li>
     * <li>工作区暂停或终止返回 403 CLIENT_WORKSPACE_UNAVAILABLE。</li>
     *
     * @return 201；邮件在提交后发送，结果见 emailDelivery
     */
    @PostExchange("/invitations")
    ResponseEntity<ClientTeamInvitationResponseBody> createInvitation(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody CreateClientTeamInvitationRequestBody request);

    /**
     * 重发邀请：到期时间重新计算 7 天，新链接生效、旧链接失效
     * <li>已接受或已撤销返回 409 CLIENT_INVITATION_CLOSED；已到期的 PENDING 邀请可以重发。</li>
     */
    @PostExchange("/invitations/{invitationId}/resend")
    ClientTeamInvitationResponseBody resendInvitation(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("invitationId") String invitationId);

    /** 撤销邀请；已撤销时返回原结果，已接受返回 409 CLIENT_INVITATION_CLOSED。 */
    @PostExchange("/invitations/{invitationId}/revocation")
    ClientTeamInvitationResponseBody revokeInvitation(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("invitationId") String invitationId);

    /** 调整成员角色；角色未变时返回原结果；下一次请求即按新角色判断权限。 */
    @PostExchange("/members/{memberId}/role")
    ClientTeamMemberResponseBody changeMemberRole(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("memberId") String memberId,
        @Valid @RequestBody ChangeClientTeamMemberRoleRequestBody request);

    /** 移除成员：成员与其角色授权置为 REVOKED；已移除时返回原结果。 */
    @PostExchange("/members/{memberId}/removal")
    ClientTeamMemberResponseBody removeMember(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("memberId") String memberId);
}
