package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientTeamPage;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientTeamPermissions;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientTeamRules;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TenantInvitationView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantInvitationRepository;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientTeamRequest;

import lombok.AllArgsConstructor;

/**
 * 查询本工作区邀请（需要 CLIENT_TEAM_MANAGE）
 * <li>状态筛选为显示状态：PENDING 只含未到期，EXPIRED 含已到期的 PENDING；按创建时间倒序。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ListClientTeamInvitationsProcessor
    extends AbstractProcessor<ClientTeamRequest, ClientTeamPage<TenantInvitationView>> {
    private final ClientWorkspaceAccessSupport access;
    private final TenantInvitationRepository invitations;
    private final ClientInvitationPreparation preparation;

    /** 状态筛选只依赖输入，查询前校验。 */
    @Override
    protected void validate(ClientTeamRequest request) {
        ClientTeamRules.listStatus(request.getStatus());
    }

    @Override
    protected ClientTeamPage<TenantInvitationView> processRequest(ClientTeamRequest request) {
        String status = ClientTeamRules.listStatus(request.getStatus()).getCode();
        return access.readTransaction().execute(tx -> {
            var workspace = access.require(request.getAccountId(), request.getSessionId(),
                ClientTeamPermissions.TEAM_MANAGE);
            var now = preparation.now();
            var paging = request.getPaging();
            return new ClientTeamPage<>(invitations.invitations(workspace.getTenantId(), status, now, paging.offset(),
                paging.size()), paging, invitations.countInvitations(workspace.getTenantId(), status, now), now,
                workspace.getMemberId());
        });
    }
}
