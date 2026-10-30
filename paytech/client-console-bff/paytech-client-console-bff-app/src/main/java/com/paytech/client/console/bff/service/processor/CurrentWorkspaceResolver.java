package com.paytech.client.console.bff.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.domain.enums.ClientBffErrorCode;
import com.paytech.client.console.bff.domain.enums.ClientContextType;
import com.paytech.client.console.bff.integration.PlatformAuthenticationClient;
import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.api.authentication.response.ClientWorkspaceResponseBody;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;

import lombok.AllArgsConstructor;

/**
 * 当前租户上下文对应的 Client 工作区：clientId 只取自 Platform 成员事实，不接受浏览器提交
 * <li>账号上下文直接拒绝；Gateway 头中的成员与租户必须与 Platform 实时上下文一致，否则会话无效。</li>
 * <li>当前成员不再可访问该工作区时统一返回工作区不可用。工作区首页与收款账户、余额接口共用。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class CurrentWorkspaceResolver {
    private final PlatformAuthenticationClient authentication;

    /**
     * 解析当前成员可访问的工作区
     *
     * @param actor 入站可信身份
     * @return 当前成员、当前租户且可访问的工作区
     */
    public ClientWorkspaceResponseBody current(ClientActor actor) {
        if (!actor.isTenantContext()) {
            throw new FailException(ClientBffErrorCode.WORKSPACE_CONTEXT_REQUIRED);
        }
        requireCurrentTenantContext(actor);
        return authentication.workspaces(actor).stream()
            .filter(candidate -> actor.getMemberId().equals(candidate.getMemberId())
                && actor.getTenantId().equals(candidate.getTenantId()) && candidate.isAccessible())
            .findFirst()
            .orElseThrow(() -> new FailException(ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE));
    }

    /** Platform 实时上下文必须是同一成员、同一租户的租户上下文。 */
    void requireCurrentTenantContext(ClientActor actor) {
        var context = authentication.context(actor);
        if (context == null || !ClientContextType.TENANT.getCode().equals(context.getContextType())
            || !actor.getMemberId().equals(context.getMemberId()) || !actor.getTenantId().equals(context.getTenantId())) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        }
    }
}
