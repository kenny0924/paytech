package com.paytech.client.console.bff.service.processor;

import com.paytech.client.console.bff.domain.enums.ClientBffErrorCode;
import com.paytech.client.console.bff.service.model.ClientWorkspaceModels.*;
import com.paytech.client.contract.api.workspace.ClientWorkspaceApi;
import com.paytech.client.contract.api.workspace.response.ClientSummaryResponseBody;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.authentication.ClientAuthenticationApi;
import com.paytech.platform.support.api.authentication.response.ClientWorkspaceResponseBody;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 工作区页面聚合：Platform 判定成员与租户，CCT 提供名称与已批准资料
 * <li>clientId 只取自 Platform 成员事实，不接受浏览器提交。</li>
 * <li>没有写入、缓存或跨 Owner 事务。</li>
 */
@Component
@lombok.AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientWorkspaceProcessor {

    /** 首页固定提示：进入工作区不代表已开通收款。 */
    static final String NOTICE = "PAYMENTS_NOT_YET_ENABLED";

    /** Platform 公开原因码到客户可见文案；内部暂停理由不进入页面。 */
    static final Map<String, String> REASONS = Map.of(
        "TENANT_TERMINATED", "工作区已终止",
        "TENANT_SUSPENDED", "工作区已暂停",
        "TENANT_SECURITY_SUSPENDED", "工作区暂时不可访问",
        "MEMBER_INACTIVE", "您的成员资格已失效"
    );

    private static final String ID = "[1-9][0-9]{0,18}";

    private final ClientAuthenticationApi platform;
    private final ClientWorkspaceApi clients;
    private final ClientSessionProcessor sessions;
    private final ClientPermissionProcessor permissions;

    /** 开始页工作区；依赖不可用只标记不可用，会话失效仍终止整页。 */
    public WorkspaceList list(HttpServletRequest request, String[] identity) {
        try {
            var workspaces = workspaces(identity);
            var summaries = summaries(identity, workspaces);
            String current = "TENANT".equals(header(request, "X-Gateway-Context-Type"))
                ? header(request, "X-Gateway-Tenant-Member-Id") : "";
            return new WorkspaceList("AVAILABLE",
                workspaces.stream().map(w -> item(w, summaries.get(w.getClientId()), current)).toList());
        } catch (FailException failure) {
            if (failure.getErrorCode() == ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID) {
                throw failure;
            }
            return new WorkspaceList("UNAVAILABLE", List.of());
        } catch (ErrorException unavailable) {
            // 超时、不可用与无效响应只影响工作区区域，入驻申请照常展示。
            return new WorkspaceList("UNAVAILABLE", List.of());
        }
    }

    /**
     * 工作区首页
     * <li>只接受租户上下文；Gateway 头与 Platform 实时上下文必须一致。</li>
     * <li>当前成员不再可访问时统一返回工作区不可用。</li>
     */
    public WorkspaceHomeResponseBody home(HttpServletRequest request) {
        if (!"TENANT".equals(header(request, "X-Gateway-Context-Type"))) {
            throw new FailException(ClientBffErrorCode.WORKSPACE_CONTEXT_REQUIRED);
        }
        var identity = sessions.identity(request);
        String member = header(request, "X-Gateway-Tenant-Member-Id"), tenant = header(request, "X-Gateway-Tenant-Id");
        var context = sessions.context(request);
        if (context == null || !"TENANT".equals(context.getContextType()) || !member.equals(context.getMemberId())
            || !tenant.equals(context.getTenantId())) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        }
        var workspace = workspaces(identity).stream()
            .filter(w -> member.equals(w.getMemberId()) && tenant.equals(w.getTenantId()) && w.isAccessible())
            .findFirst()
            .orElseThrow(() -> new FailException(ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE));
        var summary = summaries(identity, List.of(workspace)).get(workspace.getClientId());
        var capabilities = permissions.capabilities(identity);
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

    /** Platform 成员列表；数量、ID 格式与可访问标志必须自洽，否则视为依赖不可用。 */
    List<ClientWorkspaceResponseBody> workspaces(String[] identity) {
        var value = platform.workspaces(identity[0], identity[1]);
        if (value == null || value.getItems() == null || value.getItems().size() > 50) {
            throw new ServiceUnavailableException();
        }
        var members = new HashSet<String>();
        for (var w : value.getItems()) {
            if (w == null || w.getMemberId() == null || !w.getMemberId().matches(ID) || w.getTenantId() == null
                || !w.getTenantId().matches(ID) || w.getClientId() == null || !w.getClientId().matches(ID)
                || !members.add(w.getMemberId()) || w.isAccessible() != (w.getReasonCode() == null)
                || w.getReasonCode() != null && !REASONS.containsKey(w.getReasonCode())) {
                throw new ServiceUnavailableException();
            }
        }
        return value.getItems();
    }

    /** CCT 只返回本账号已批准的 Client；未返回的 Client 没有名称，不视为错误。 */
    Map<String, ClientSummaryResponseBody> summaries(String[] identity, List<ClientWorkspaceResponseBody> workspaces) {
        if (workspaces.isEmpty()) {
            return Map.of();
        }
        var ids = workspaces.stream().map(ClientWorkspaceResponseBody::getClientId).distinct().toList();
        var value = clients.summaries("client-console-bff", identity[0], identity[1], ids);
        if (value == null || value.getItems() == null) {
            throw new ServiceUnavailableException();
        }
        Set<String> requested = Set.copyOf(ids);
        var result = new HashMap<String, ClientSummaryResponseBody>();
        for (var s : value.getItems()) {
            if (s == null || !requested.contains(s.getClientId()) || result.put(s.getClientId(), s) != null) {
                throw new ServiceUnavailableException();
            }
        }
        return result;
    }

    /** 组合单项；CCT 没有名称时 displayName 为空，页面改显客户编号。 */
    WorkspaceItem item(ClientWorkspaceResponseBody w, ClientSummaryResponseBody summary, String current) {
        return new WorkspaceItem(w.getMemberId(), w.getClientId(),
            summary == null ? null : summary.getClientNo(),
            summary == null ? null : summary.getDisplayName(),
            summary == null ? null : summary.getClientType(),
            w.isAccessible() ? "ACCESSIBLE" : "RESTRICTED",
            w.isAccessible() ? null : REASONS.get(w.getReasonCode()),
            w.getMemberId().equals(current));
    }

    /** 只接受恰好一个值的可信头。 */
    String header(HttpServletRequest request, String name) {
        var values = Collections.list(request.getHeaders(name));
        return values.size() == 1 ? values.getFirst() : "";
    }
}
