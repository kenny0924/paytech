package com.paytech.platform.support.ext.rest.clientteam;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.accesscontrol.service.processor.AcceptClientInvitationProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.ViewClientInvitationProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientInvitationRequest;
import com.paytech.platform.support.api.clientinvitation.ClientInvitationApi;
import com.paytech.platform.support.api.clientinvitation.request.ClientInvitationProofRequestBody;
import com.paytech.platform.support.api.clientinvitation.response.ClientInvitationAcceptanceResponseBody;
import com.paytech.platform.support.api.clientinvitation.response.ClientInvitationDetailsResponseBody;
import com.paytech.platform.support.api.clientinvitation.response.ClientInvitationPreviewResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.ext.rest.clientdeveloper.ClientDeveloperConverter;

import lombok.AllArgsConstructor;

/** 私网被邀请人入口：公开预览、已登录详情与接受；协议转换后委托用例。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ClientInvitationController implements ClientInvitationApi {
    private final ClientTeamConverter converter;
    private final ClientDeveloperConverter identifiers;
    private final ViewClientInvitationProcessor viewProcessor;
    private final AcceptClientInvitationProcessor acceptProcessor;

    /** 公开预览，不携带账号身份。 */
    @Override
    public ClientInvitationPreviewResponseBody preview(ClientInvitationProofRequestBody request) {
        return converter.toPreview(viewProcessor.process(new ClientInvitationRequest(null, null,
            request.getChallengeNo(), request.getSecret(), null)));
    }

    /** 已登录详情。 */
    @Override
    public ClientInvitationDetailsResponseBody details(String accountId, String sessionId,
        ClientInvitationProofRequestBody request) {
        return converter.toDetails(viewProcessor.process(new ClientInvitationRequest(identifiers.session(accountId),
            identifiers.session(sessionId), request.getChallengeNo(), request.getSecret(), null)));
    }

    /** 接受邀请。 */
    @Override
    @Audit(action = "CLIENT_TEAM_INVITATION_ACCEPT", resourceType = "TENANT_INVITATION")
    public ClientInvitationAcceptanceResponseBody accept(String accountId, String sessionId,
        ClientInvitationProofRequestBody request) {
        return converter.toAcceptance(acceptProcessor.process(new ClientInvitationRequest(identifiers.session(accountId),
            identifiers.session(sessionId), request.getChallengeNo(), request.getSecret(), null)));
    }
}
