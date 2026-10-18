package com.paytech.client.console.bff.integration;

import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.platform.support.api.clientinvitation.request.ClientInvitationProofRequestBody;
import com.paytech.platform.support.api.clientinvitation.response.ClientInvitationDetailsResponseBody;

/** Platform 成员邀请的出站端口。 */
public interface PlatformInvitationClient {

    /**
     * 已登录账号查看邀请详情。
     *
     * @param actor 当前可信身份（账号上下文）
     * @param proof 链接证明，原样交给 Platform 校验
     * @return 邀请详情；回执为空时按依赖不可用抛出
     */
    ClientInvitationDetailsResponseBody details(ClientActor actor, ClientInvitationProofRequestBody proof);
}
