package com.paytech.platform.support.ext.rest.clientteam;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientInvitationAcceptance;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientInvitationView;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientTeamPage;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TeamMemberView;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TenantInvitationResult;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TenantInvitationView;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantInvitationStatus;
import com.paytech.platform.support.api.clientinvitation.response.ClientInvitationAcceptanceResponseBody;
import com.paytech.platform.support.api.clientinvitation.response.ClientInvitationDetailsResponseBody;
import com.paytech.platform.support.api.clientinvitation.response.ClientInvitationPreviewResponseBody;
import com.paytech.platform.support.api.clientteam.response.ClientTeamInvitationResponseBody;
import com.paytech.platform.support.api.clientteam.response.ClientTeamMemberResponseBody;

/** 团队与邀请接口的响应投影；不访问数据库，ID 按十进制字符串、时间按 UTC ISO-8601 输出。 */
@Component
public class ClientTeamConverter {

    /** 成员一页；标记当前操作人自己。 */
    public PagingResponseBody<ClientTeamMemberResponseBody> toMembers(ClientTeamPage<TeamMemberView> page) {
        return new PagingResponseBody<>(page.getItems().stream()
            .map(member -> toMember(member, member.getMemberId() == page.getSelfMemberId())).toList(),
            page.getPaging().page(), page.getPaging().size(), page.getTotal());
    }

    /** 单个成员。 */
    public ClientTeamMemberResponseBody toMember(TeamMemberView member, boolean self) {
        return new ClientTeamMemberResponseBody(Long.toString(member.getMemberId()), member.getEmailMasked(),
            member.getMemberType(), member.getRoleCode(), member.getStatus(), text(member.getJoinedAt()), self);
    }

    /** 邀请一页。 */
    public PagingResponseBody<ClientTeamInvitationResponseBody> toInvitations(ClientTeamPage<TenantInvitationView> page) {
        return new PagingResponseBody<>(page.getItems().stream()
            .map(invitation -> toInvitation(invitation, page.getAsOf(), null)).toList(),
            page.getPaging().page(), page.getPaging().size(), page.getTotal());
    }

    /** 发起、重发或撤销结果。 */
    public ClientTeamInvitationResponseBody toInvitation(TenantInvitationResult result) {
        return toInvitation(result.getInvitation(), result.getAsOf(), result.getEmailDelivery());
    }

    /** 单个邀请；PENDING 且已到期显示 EXPIRED。 */
    ClientTeamInvitationResponseBody toInvitation(TenantInvitationView invitation, Instant asOf, String emailDelivery) {
        String status = TenantInvitationStatus.PENDING.getCode().equals(invitation.getStatus())
            && !invitation.getExpiresTime().isAfter(asOf) ? TenantInvitationStatus.EXPIRED.getCode()
            : invitation.getStatus();
        return new ClientTeamInvitationResponseBody(Long.toString(invitation.getInvitationId()),
            invitation.getEmailMasked(), invitation.getRoleCode(), status, text(invitation.getExpiresTime()),
            invitation.getInvitedByEmailMasked(), text(invitation.getCreateTime()), emailDelivery);
    }

    /** 公开预览。 */
    public ClientInvitationPreviewResponseBody toPreview(ClientInvitationView view) {
        return new ClientInvitationPreviewResponseBody(view.getEmailMasked(), view.getRoleCode(), view.getStatus(),
            text(view.getExpiresTime()));
    }

    /** 已登录详情；邮箱不一致时 clientId 为空。 */
    public ClientInvitationDetailsResponseBody toDetails(ClientInvitationView view) {
        return new ClientInvitationDetailsResponseBody(view.getEmailMasked(), view.getRoleCode(), view.getStatus(),
            text(view.getExpiresTime()), Boolean.TRUE.equals(view.getEmailMatches()),
            view.getClientId() == null ? null : Long.toString(view.getClientId()));
    }

    /** 接受结果。 */
    public ClientInvitationAcceptanceResponseBody toAcceptance(ClientInvitationAcceptance acceptance) {
        return new ClientInvitationAcceptanceResponseBody(acceptance.getOutcome(),
            Long.toString(acceptance.getTenantId()), Long.toString(acceptance.getMemberId()),
            Long.toString(acceptance.getClientId()));
    }

    /** 时间输出；为空保持为空。 */
    String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
