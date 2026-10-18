package com.paytech.client.console.bff.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.integration.CctWorkspaceClient;
import com.paytech.client.console.bff.integration.PlatformInvitationClient;
import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.client.console.bff.service.model.response.ClientInvitationDetailsResponseBody;
import com.paytech.client.console.bff.service.processor.request.ClientInvitationDetailsRequest;
import com.paytech.client.contract.api.workspace.response.ClientSummaryResponseBody;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * I02 接受邀请页详情：Platform 判定邀请与邮箱一致性，CCT 提供工作区名称
 * <li>clientId 只取自 Platform 详情（且仅在邮箱一致时返回），不接受浏览器提交。</li>
 * <li>名称只是展示补充：CCT 业务拒绝、超时、不可用或回执无效时名称为空，不阻断接受邀请。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientInvitationDetailsProcessor
    extends AbstractProcessor<ClientInvitationDetailsRequest, ClientInvitationDetailsResponseBody> {
    private final CctWorkspaceClient workspaceSummaries;
    private final PlatformInvitationClient invitations;

    @Override
    protected ClientInvitationDetailsResponseBody processRequest(ClientInvitationDetailsRequest request) {
        var details = invitations.details(request.getActor(), request.getProof());
        var result = new ClientInvitationDetailsResponseBody(details.getEmailMasked(), details.getRoleCode(), details.getStatus(),
            details.getExpiresAt(), details.isEmailMatches(), details.getClientId(), null, null, null);
        if (!details.isEmailMatches() || details.getClientId() == null) {
            return result;
        }
        var summary = summary(request.getActor(), details.getClientId());
        if (summary != null) {
            result.setWorkspaceName(summary.getDisplayName());
            result.setClientNo(summary.getClientNo());
            result.setClientType(summary.getClientType());
        }
        return result;
    }

    /** 读取单个 Client 的已批准摘要；任何失败都视为没有名称。 */
    ClientSummaryResponseBody summary(ClientActor actor, String clientId) {
        try {
            return workspaceSummaries.summaries(actor, List.of(clientId)).get(clientId);
        } catch (FailException | ErrorException unavailable) {
            return null;
        }
    }
}
