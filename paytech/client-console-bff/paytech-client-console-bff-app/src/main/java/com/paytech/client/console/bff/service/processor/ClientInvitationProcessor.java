package com.paytech.client.console.bff.service.processor;

import com.paytech.client.console.bff.service.model.ClientInvitationDetailsResponseBody;
import com.paytech.client.contract.api.workspace.ClientWorkspaceApi;
import com.paytech.client.contract.api.workspace.response.ClientSummaryResponseBody;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.clientinvitation.ClientInvitationApi;
import com.paytech.platform.support.api.clientinvitation.request.ClientInvitationProofRequestBody;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 接受邀请页详情聚合：Platform 判定邀请与邮箱一致性，CCT 提供工作区名称
 * <li>clientId 只取自 Platform 详情（且仅在邮箱一致时返回），不接受浏览器提交。</li>
 * <li>CCT 读取失败或未返回时名称为空，不影响详情本身；没有写入、缓存或跨 Owner 事务。</li>
 */
@Component
@lombok.AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientInvitationProcessor {

    private static final String CALLER = "client-console-bff";

    private final ClientWorkspaceApi clients;
    private final ClientInvitationApi invitations;

    /**
     * 已登录账号查看邀请详情
     *
     * @param identity 已校验的账号 ID 与会话 ID（账号上下文）
     * @param proof 链接证明，原样交给 Platform 校验
     * @return Platform 详情字段，邮箱一致且 CCT 可读时附工作区名称、编号与类型
     */
    public ClientInvitationDetailsResponseBody details(String[] identity, ClientInvitationProofRequestBody proof) {
        var details = invitations.details(identity[0], identity[1], proof);
        if (details == null) {
            throw new ServiceUnavailableException();
        }
        var result = new ClientInvitationDetailsResponseBody(details.getEmailMasked(), details.getRoleCode(),
            details.getStatus(), details.getExpiresAt(), details.isEmailMatches(), details.getClientId(), null, null, null);
        if (!details.isEmailMatches() || details.getClientId() == null) {
            return result;
        }
        var summary = summary(identity, details.getClientId());
        if (summary != null) {
            result.setWorkspaceName(summary.getDisplayName());
            result.setClientNo(summary.getClientNo());
            result.setClientType(summary.getClientType());
        }
        return result;
    }

    /**
     * 读取单个 Client 的已批准摘要
     * <li>CCT 业务拒绝、超时、不可用或返回了其他 Client 时视为没有名称，返回 null。</li>
     */
    ClientSummaryResponseBody summary(String[] identity, String clientId) {
        try {
            var value = clients.summaries(CALLER, identity[0], identity[1], List.of(clientId));
            if (value == null || value.getItems() == null || value.getItems().size() != 1
                || !clientId.equals(value.getItems().getFirst().getClientId())) {
                return null;
            }
            return value.getItems().getFirst();
        } catch (FailException | ErrorException unavailable) {
            // 名称只是展示补充，CCT 失败不阻断接受邀请。
            return null;
        }
    }
}
