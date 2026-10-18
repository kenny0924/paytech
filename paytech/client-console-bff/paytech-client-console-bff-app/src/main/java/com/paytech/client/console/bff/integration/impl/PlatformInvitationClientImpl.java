package com.paytech.client.console.bff.integration.impl;

import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.integration.PlatformInvitationClient;
import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.clientinvitation.ClientInvitationApi;
import com.paytech.platform.support.api.clientinvitation.request.ClientInvitationProofRequestBody;
import com.paytech.platform.support.api.clientinvitation.response.ClientInvitationDetailsResponseBody;

import lombok.AllArgsConstructor;

/** 调用 Platform 邀请详情接口；邀请与邮箱一致性由 Platform 判定。 */
@Component
@AllArgsConstructor
public class PlatformInvitationClientImpl implements PlatformInvitationClient {
    private final ClientInvitationApi invitations;

    /** 读取邀请详情；回执为空按依赖不可用。 */
    @Override
    public ClientInvitationDetailsResponseBody details(ClientActor actor, ClientInvitationProofRequestBody proof) {
        var details = invitations.details(actor.getAccountId(), actor.getSessionId(), proof);
        if (details == null) {
            throw new ServiceUnavailableException();
        }
        return details;
    }
}
