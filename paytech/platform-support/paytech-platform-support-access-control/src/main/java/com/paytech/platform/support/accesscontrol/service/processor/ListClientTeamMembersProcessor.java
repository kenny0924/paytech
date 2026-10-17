package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientTeamPage;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientTeamPermissions;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TeamMemberView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamMemberRepository;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientTeamRequest;

import lombok.AllArgsConstructor;

/**
 * 查询本工作区 ACTIVE 成员（需要 CLIENT_TEAM_READ）
 * <li>会话、权限、成员页与总数在同一只读快照内读取；所有者在前，其余按加入时间。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ListClientTeamMembersProcessor extends AbstractProcessor<ClientTeamRequest, ClientTeamPage<TeamMemberView>> {
    private final TeamMemberRepository members;
    private final ClientWorkspaceAccessSupport access;
    private final ClientInvitationPreparation preparation;

    @Override
    protected ClientTeamPage<TeamMemberView> processRequest(ClientTeamRequest request) {
        return access.readTransaction().execute(status -> {
            var workspace = access.require(request.getAccountId(), request.getSessionId(), ClientTeamPermissions.TEAM_READ);
            var now = preparation.now();
            var paging = request.getPaging();
            return new ClientTeamPage<>(members.members(workspace.getTenantId(), now, paging.offset(), paging.size()),
                paging, members.countMembers(workspace.getTenantId()), now, workspace.getMemberId());
        });
    }
}
