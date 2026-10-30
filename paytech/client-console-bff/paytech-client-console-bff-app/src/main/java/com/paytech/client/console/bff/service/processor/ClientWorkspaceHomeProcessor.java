package com.paytech.client.console.bff.service.processor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.integration.CctWorkspaceClient;
import com.paytech.client.console.bff.integration.PlatformAuthorizationClient;
import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.client.console.bff.service.model.response.HomeProfile;
import com.paytech.client.console.bff.service.model.response.HomeWorkspace;
import com.paytech.client.console.bff.service.model.response.WorkspaceHomeResponseBody;
import com.paytech.client.contract.api.workspace.response.ClientSummaryResponseBody;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.authentication.response.ClientWorkspaceResponseBody;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;

import lombok.AllArgsConstructor;

/**
 * W02 工作区首页：Platform 判定成员与租户，CCT 提供名称与已批准资料，Platform 授权给出页面能力
 * <li>只接受租户上下文；Gateway 头中的成员与租户必须与 Platform 实时上下文一致，否则会话无效。</li>
 * <li>clientId 只取自 Platform 成员事实，不接受浏览器提交；当前成员不再可访问时统一返回工作区不可用。</li>
 * <li>页面能力只控制菜单显示：会话失效照常抛出，其他授权失败返回空能力。请求只有可信身份，直接以 {@link ClientActor} 作为内部请求。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientWorkspaceHomeProcessor extends AbstractProcessor<ClientActor, WorkspaceHomeResponseBody> {
    /** 首页固定提示：进入工作区不代表已开通收款。 */
    static final String NOTICE = "PAYMENTS_NOT_YET_ENABLED";

    /** 页面能力名到 Platform 权限码；顺序即返回顺序。 */
    static final Map<String, String> CAPABILITIES = capabilityPermissions();

    private final CurrentWorkspaceResolver workspaces;
    private final CctWorkspaceClient workspaceSummaries;
    private final PlatformAuthorizationClient authorization;

    @Override
    protected WorkspaceHomeResponseBody processRequest(ClientActor actor) {
        var workspace = workspaces.current(actor);
        var summary = workspaceSummaries.summaries(actor, List.of(workspace.getClientId())).get(workspace.getClientId());
        return response(workspace, summary, capabilities(actor));
    }

    /** 当前成员被授予的页面能力；会话失效照常抛出，其他失败返回空能力。 */
    List<String> capabilities(ClientActor actor) {
        try {
            var granted = authorization.grantedPermissions(actor, List.copyOf(CAPABILITIES.values()));
            return CAPABILITIES.entrySet().stream().filter(entry -> granted.contains(entry.getValue())).map(Map.Entry::getKey).toList();
        } catch (FailException failure) {
            if (failure.getErrorCode() == ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID) {
                throw failure;
            }
            return List.of();
        } catch (ErrorException unavailable) {
            return List.of();
        }
    }

    /** 组装首页；CCT 未返回已批准资料时只有 clientId。 */
    static WorkspaceHomeResponseBody response(ClientWorkspaceResponseBody workspace, ClientSummaryResponseBody summary,
        List<String> capabilities) {
        if (summary == null) {
            return new WorkspaceHomeResponseBody(new HomeWorkspace(workspace.getClientId(), null, null, null, null), null, NOTICE,
                capabilities);
        }
        return new WorkspaceHomeResponseBody(
            new HomeWorkspace(summary.getClientId(), summary.getClientNo(), summary.getDisplayName(), summary.getClientType(),
                summary.getClientStatus()),
            new HomeProfile(summary.getCountry(), summary.getBusinessModelCode(), summary.getApprovedAt()),
            NOTICE, capabilities);
    }

    /** 固定的开发者与团队能力。 */
    static Map<String, String> capabilityPermissions() {
        var map = new LinkedHashMap<String, String>();
        map.put("API_CREDENTIAL_READ", "CLIENT_API_CREDENTIAL_READ");
        map.put("API_CREDENTIAL_MANAGE", "CLIENT_API_CREDENTIAL_MANAGE");
        map.put("WEBHOOK_READ", "CLIENT_WEBHOOK_READ");
        map.put("WEBHOOK_MANAGE", "CLIENT_WEBHOOK_MANAGE");
        map.put("TEAM_READ", "CLIENT_TEAM_READ");
        map.put("TEAM_MANAGE", "CLIENT_TEAM_MANAGE");
        return Collections.unmodifiableMap(map);
    }
}
