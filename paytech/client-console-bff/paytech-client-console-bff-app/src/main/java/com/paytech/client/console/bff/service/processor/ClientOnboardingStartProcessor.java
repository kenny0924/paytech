package com.paytech.client.console.bff.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.domain.enums.SectionAvailability;
import com.paytech.client.console.bff.domain.enums.WorkspaceRestrictionReason;
import com.paytech.client.console.bff.integration.CctOnboardingClient;
import com.paytech.client.console.bff.integration.CctWorkspaceClient;
import com.paytech.client.console.bff.integration.PlatformAuthenticationClient;
import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.client.console.bff.service.model.response.StartResponseBody;
import com.paytech.client.console.bff.service.model.response.WorkspaceItem;
import com.paytech.client.console.bff.service.model.response.WorkspaceList;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.ApplicationSummary;
import com.paytech.client.contract.api.workspace.response.ClientSummaryResponseBody;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.authentication.response.ClientWorkspaceResponseBody;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;

import lombok.AllArgsConstructor;

/**
 * 入驻开始页：CCT 申请导航加“我的工作区”（Platform 成员事实 + CCT 已批准名称）
 * <li>GET 只导航既有申请，不以读取页面触发创建；请求只有可信身份，直接以 {@link ClientActor} 作为内部请求。</li>
 * <li>读取前后各回读一次 Platform 会话，整页只基于仍有效的会话返回。</li>
 * <li>工作区区域的依赖失败只标记 UNAVAILABLE，申请导航照常返回；会话失效仍终止整页。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientOnboardingStartProcessor extends AbstractProcessor<ClientActor, StartResponseBody> {
    private final CctWorkspaceClient workspaceSummaries;
    private final CctOnboardingClient onboarding;
    private final PlatformAuthenticationClient authentication;

    @Override
    protected StartResponseBody processRequest(ClientActor actor) {
        authentication.context(actor);
        var navigation = onboarding.start(actor);
        String activeApplicationId = navigation.applications().stream().filter(ApplicationSummary::active)
            .map(ApplicationSummary::applicationId).findFirst().orElse(null);
        var workspaces = workspaceList(actor);
        authentication.context(actor);
        return new StartResponseBody(navigation.applications(), navigation.hasMore(), activeApplicationId, workspaces);
    }

    /** 组合工作区列表；依赖不可用时返回不可用的空列表，会话失效直接抛出。 */
    WorkspaceList workspaceList(ClientActor actor) {
        try {
            var workspaces = authentication.workspaces(actor);
            var summaries = workspaceSummaries.summaries(actor,
                workspaces.stream().map(ClientWorkspaceResponseBody::getClientId).toList());
            String currentMemberId = actor.isTenantContext() ? actor.getMemberId() : "";
            return new WorkspaceList(SectionAvailability.AVAILABLE.getCode(), workspaces.stream()
                .map(workspace -> item(workspace, summaries.get(workspace.getClientId()), currentMemberId)).toList());
        } catch (FailException failure) {
            if (failure.getErrorCode() == ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID) {
                throw failure;
            }
            return unavailableWorkspaces();
        } catch (ErrorException unavailable) {
            // 超时、不可用与无效响应只影响工作区区域，入驻申请照常展示。
            return unavailableWorkspaces();
        }
    }

    /** 不可用的工作区区域。 */
    static WorkspaceList unavailableWorkspaces() {
        return new WorkspaceList(SectionAvailability.UNAVAILABLE.getCode(), List.of());
    }

    /** 组合单个工作区；CCT 没有名称时 displayName 为空，页面改显客户编号。 */
    static WorkspaceItem item(ClientWorkspaceResponseBody workspace, ClientSummaryResponseBody summary,
        String currentMemberId) {
        boolean accessible = workspace.isAccessible();
        return new WorkspaceItem(workspace.getMemberId(), workspace.getClientId(),
            summary == null ? null : summary.getClientNo(), summary == null ? null : summary.getDisplayName(),
            summary == null ? null : summary.getClientType(), accessible ? "ACCESSIBLE" : "RESTRICTED",
            accessible ? null : WorkspaceRestrictionReason.fromCode(workspace.getReasonCode()).getMessage(),
            workspace.getMemberId().equals(currentMemberId));
    }
}
