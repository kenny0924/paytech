package com.paytech.platform.support.api.clientinvitation;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.platform.support.api.clientinvitation.request.ClientInvitationProofRequestBody;
import com.paytech.platform.support.api.clientinvitation.response.ClientInvitationAcceptanceResponseBody;
import com.paytech.platform.support.api.clientinvitation.response.ClientInvitationDetailsResponseBody;
import com.paytech.platform.support.api.clientinvitation.response.ClientInvitationPreviewResponseBody;

import jakarta.validation.Valid;

/**
 * 被邀请人查看与接受 CLIENT 工作区邀请；只供 Client BFF 私网调用
 * <li>链接证明无效时统一返回 404 CLIENT_INVITATION_INVALID；Secret 校验失败计入挑战尝试次数。</li>
 * <li>预览与详情不消费挑战；接受一次性消费挑战。</li>
 */
@HttpExchange("/client-invitations/v1")
public interface ClientInvitationApi {

    /**
     * 未登录预览
     *
     * @param request 链接证明
     * @return 脱敏邮箱、角色、状态与到期时间；不返回工作区信息
     */
    @PostExchange("/preview")
    ClientInvitationPreviewResponseBody preview(@Valid @RequestBody ClientInvitationProofRequestBody request);

    /**
     * 已登录账号查看详情（账号上下文）
     *
     * @return 预览字段、邮箱是否一致；一致时附 clientId
     */
    @PostExchange("/details")
    ClientInvitationDetailsResponseBody details(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @Valid @RequestBody ClientInvitationProofRequestBody request);

    /**
     * 接受邀请（账号上下文）
     * <li>一个事务创建或恢复成员、授予初始角色、消费挑战并完成邀请。</li>
     * <li>邮箱不一致 403 CLIENT_INVITATION_EMAIL_MISMATCH；邀请已过期、已撤销或已被其他账号接受 409 CLIENT_INVITATION_CLOSED；
     * 工作区不可访问 403 CLIENT_WORKSPACE_UNAVAILABLE。</li>
     *
     * @return JOINED 或 ALREADY_MEMBER
     */
    @PostExchange("/acceptance")
    ClientInvitationAcceptanceResponseBody accept(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @Valid @RequestBody ClientInvitationProofRequestBody request);
}
